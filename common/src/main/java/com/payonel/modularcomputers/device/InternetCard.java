package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.BusPower;
import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.internet.HttpExchange;
import com.payonel.modularcomputers.internet.InternetPolicy;
import com.payonel.modularcomputers.power.PowerUnits;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * An internet card (user design, 2026-10-24, after OpenComputers'): HTTP and HTTPS requests to the
 * real internet, the card doing HTTP itself (a program's Lua can't do TLS; a real card offloading
 * it is the stand-in). A program uses it as any device, through its methods (in mcos, ioctls on
 * its /dev file), as OpenComputers' handles were used: {@link #request} gives a handle at once;
 * {@link #response} gives its status and headers once they've come, {@link #read} its body as it
 * comes (false: not yet; "": its end), and either fails with why the request did; {@link #close}
 * stops it. Its interrupt says when a request has news. Each request runs on a thread of its own
 * ({@link HttpExchange}, which keeps it to the public internet: {@link InternetPolicy}); its body
 * waits in the card, at most its setting's worth a request ({@link #RESPONSE_BUFFER_KIB} KiB by default), and a request whose next piece
 * finds no room waits (the handshake: nothing dropped), so a download goes at the program's pace.
 * At most {@link #MAX_OPEN} requests at a time.
 * <p>
 * Its stream ({@link #pull}) is its access log, as a web server's: each request as it ends, as a
 * command's end (the user's, 2026-11-05: a request is a command over time), its method and URL
 * always, then how it went: {@code {event = "request", ok = true, method, url, result = {status,
 * millis, bytes}}}, or {@code {event = "request", ok = false, method, url, why, ...its fields, call
 * = {args, progress = {status, millis, bytes, sent}}}} (its why flat, as an end's; stopped when its
 * program closed it first; its call the arguments as given, a body cut to {@link #BODY_KEPT} bytes).
 */
public final class InternetCard extends Card {
	/** Requests open at once, a card. */
	public static final int MAX_OPEN = 4;
	/** And on the whole server: however many computers a player builds, its threads are bounded. */
	public static final int MAX_RUNNING = 64;
	private static final java.util.concurrent.atomic.AtomicInteger RUNNING = new java.util.concurrent.atomic.AtomicInteger();
	/** How much of a response's body a request holds unread before its download waits, by default, in KiB (a data buffer, its own setting). */
	public static final int RESPONSE_BUFFER_KIB = 64;
	/** Bytes of a body a request holds unread, as last loaded ({@code internet_response_buffer_kib}). */
	private static volatile int bodyBytes = bytes(RESPONSE_BUFFER_KIB);

	/** The server's setting (ModConfig's), in KiB. */
	public static void responseBuffer(int kib) {
		bodyBytes = bytes(kib);
	}

	private static int bytes(int kib) {
		return (int) Math.min(Integer.MAX_VALUE, (long) kib * 1024);
	}
	/** Its access log's event: a request's end. */
	public static final String REQUEST = "request";
	/** A body's bytes a failed request's call keeps (the user's: its start, enough to tell which; its size is the progress's sent). */
	public static final int BODY_KEPT = 16;
	/** Its draw a tick, at rest. */
	public static final int DRAW = 4;
	/** What a request costs. */
	public static final int REQUEST_COST = PowerUnits.CPU;
	/** What each {@link #DATA_UNIT} bytes of a body read cost (a part of one costs a whole one). */
	public static final int DATA_COST = PowerUnits.CPU / 10;
	/** How many bytes of a body cost {@link #DATA_COST}: 4 KiB. */
	public static final int DATA_UNIT = 4096;
	/** How long a downloaded piece waits for room before looking again whether its request was closed, in milliseconds. */
	private static final long ROOM_MILLIS = 100;
	/** The servers' settings, as loaded (a test's own). */
	private static volatile Supplier<InternetPolicy> policy = () -> InternetPolicy.DEFAULT;
	/** Its requests' threads: daemons, so a stopping server never waits on the internet. */
	private static final ExecutorService THREADS = Executors.newCachedThreadPool(run -> {
		Thread thread = new Thread(run, "modularcomputers-internet");
		thread.setDaemon(true);
		return thread;
	});
	private final InterruptLine line = new InterruptLine();
	private final BusPower power = new BusPower(DRAW);
	private final Map<Long, Request> open = new ConcurrentHashMap<>();
	/**
	 * Its access log, kept as a device's events are (the user's): a window, its oldest going past
	 * {@link EventQueue#window}, the next entry saying how many as lost; pending till taken. Server
	 * thread only: entries made on a request's thread wait in {@link #ending} for the next tick.
	 */
	private final EventQueue log = new EventQueue(com.payonel.modularcomputers.bus.DeviceType.INTERNET, line, () -> { }, this::isAccepting);
	/** Requests' ends made on their threads, for the next tick to put in the log, in order. */
	private final java.util.concurrent.ConcurrentLinkedQueue<Map<String, Object>> ending = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private final HttpExchange.Resolver resolver;
	private long nextHandle = 1;
	/** Something happened on a request's thread since the last tick: its interrupt, at the next. */
	private final java.util.concurrent.atomic.AtomicBoolean news = new java.util.concurrent.atomic.AtomicBoolean();

	/** A request: what's come of it, as its thread fills it in. */
	private final class Request {
		final String method;
		final String url;
		/** Its arguments as given, its body cut to its start; and its body's whole size. */
		final java.util.List<Object> given;
		final long sent;
		final long started = System.nanoTime();
		/** Its end is in the log (once: a close and its thread's end can meet). */
		final java.util.concurrent.atomic.AtomicBoolean logged = new java.util.concurrent.atomic.AtomicBoolean();
		final BodyBuffer body = new BodyBuffer(bodyBytes); // (the setting as the request starts)
		volatile Map<String, Object> response;
		volatile boolean ended;
		volatile com.payonel.modularcomputers.runtime.Failure error;
		volatile long bytes;
		/** News the program hasn't looked at (its interrupt stays pending while any request has some). */
		volatile boolean fresh;
		HttpExchange exchange;

		Request(String method, String url, java.util.List<Object> given, long sent) {
			this.method = method;
			this.url = url;
			this.given = given;
			this.sent = sent;
		}

		void told() {
			fresh = true;
			news.set(true);
		}
	}

	public InternetCard() {
		this(HttpExchange.SYSTEM);
	}

	/** One with its own way of resolving names (a test's). */
	public InternetCard(HttpExchange.Resolver resolver) {
		super(CardKind.INTERNET);
		this.resolver = resolver;
	}

	/** Where the server's settings come from. */
	public static void policyFrom(Supplier<InternetPolicy> settings) {
		policy = settings;
	}

	@Method(doc = "request(url[, method[, headers[, body]]]): starts an HTTP or HTTPS request (GET, or POST with a body; headers a table); returns its handle, or false and why, {why, ...}: bad_argument, denied or limit")
	public Result request(Arguments args) {
		if (!policy.get().enabled()) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.denied("disabled", ""));
		} else if (open.size() >= MAX_OPEN) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.limit("open_requests", MAX_OPEN));
		} else if (RUNNING.get() >= MAX_RUNNING) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.limit("server_requests", MAX_RUNNING)); // (the server's, every card's: no device's news ends it, so not busy)
		}
		String url = args.checkString(0);
		String method = args.get(1) instanceof String verb ? verb : null;
		Map<String, String> headers = new LinkedHashMap<>();
		if (args.get(2) instanceof Map<?, ?> given) {
			given.forEach((name, value) -> headers.put(String.valueOf(name), String.valueOf(value)));
		}
		byte[] body = args.get(3) instanceof String text ? text.getBytes(StandardCharsets.ISO_8859_1) : null;
		long handle = nextHandle++;
		java.util.List<Object> given = new java.util.ArrayList<>(EventQueue.given(args.values()));
		if (given.size() > 3 && given.get(3) instanceof String text && text.length() > BODY_KEPT) {
			given.set(3, text.substring(0, BODY_KEPT)); // (the log keeps its start, not the whole body)
		}
		Request request = new Request(method != null ? method.toUpperCase(java.util.Locale.ROOT) : body == null ? "GET" : "POST", url,
			java.util.Collections.unmodifiableList(given), body == null ? 0 : body.length);
		try {
			request.exchange = HttpExchange.of(url, method, headers, body, policy.get(), resolver, listener(handle, request));
		} catch (com.payonel.modularcomputers.internet.HttpExchange.Refused e) {
			return Result.fail(e.failure());
		}
		power.charge(REQUEST_COST);
		open.put(handle, request);
		RUNNING.incrementAndGet();
		THREADS.execute(() -> {
			try {
				request.exchange.run();
			} finally {
				RUNNING.decrementAndGet();
			}
		});
		return Result.ok(handle);
	}

	/** What a request's thread tells it. */
	private HttpExchange.Listener listener(long handle, Request request) {
		return new HttpExchange.Listener() {
			@Override
			public void response(int status, String message, Map<String, String> headers) {
				Map<String, Object> response = new LinkedHashMap<>();
				response.put("status", (long) status);
				response.put("message", message);
				response.put("headers", new LinkedHashMap<String, Object>(headers));
				request.response = response;
				request.told();
			}

			@Override
			public boolean data(byte[] bytes) throws InterruptedException {
				// As much as there's room for at once, the rest as the program reads (closed: it stops).
				return request.body.put(new String(bytes, StandardCharsets.ISO_8859_1), () -> open.get(handle) == request, ROOM_MILLIS, put -> {
					request.bytes += put;
					request.told();
				});
			}

			@Override
			public void end() {
				request.ended = true;
				logged(request, false);
				request.told();
			}

			@Override
			public void failed(com.payonel.modularcomputers.runtime.Failure why) {
				request.error = why;
				logged(request, false);
				request.told();
			}
		};
	}

	/** A request ended (done, failed, or {@code stopped} by its program): its entry in the access log, as a command's end. */
	private void logged(Request request, boolean stopped) {
		if (!request.logged.compareAndSet(false, true)) {
			return;
		}
		Object status = request.response != null ? request.response.get("status") : null;
		Map<String, Object> measures = new LinkedHashMap<>();
		measures.put("status", status != null ? status : 0L); // (0: no response came)
		measures.put("millis", (System.nanoTime() - request.started) / 1_000_000);
		measures.put("bytes", request.bytes);
		Map<String, Object> entry;
		if (request.error == null && !stopped) {
			entry = EventQueue.done(REQUEST, measures);
		} else {
			measures.put("sent", request.sent);
			Map<String, Object> call = EventQueue.call(request.given, measures);
			entry = request.error != null ? EventQueue.failed(REQUEST, request.error, call) : EventQueue.stoppedEnd(REQUEST, call);
		}
		Map<String, Object> said = new LinkedHashMap<>();
		said.put("event", entry.remove("event"));
		said.put("ok", entry.remove("ok"));
		said.put("method", request.method); // (what it is, always: never only in its call, the user's)
		said.put("url", request.url);
		said.putAll(entry);
		ending.add(said); // (the log's, at the next tick: it's the server thread's)
		news.set(true);
	}

	/** The request a handle names, its news now looked at; null: none (its caller's refusal: {@link #noSuchRequest}). */
	private Request handle(Arguments args) {
		Request request = open.get(args.checkInteger(0));
		if (request == null) {
			return null;
		}
		request.fresh = false; // (asked: this request's news is looked at, and only its: a log entry is news till it's taken)
		return request;
	}

	@Method(doc = "response(handle): its {status, message, headers} once they've come (headers by name, in lower case); false: not yet; fails with why the request did, {why, ...}: network, denied")
	public Result response(Arguments args) {
		Request request = handle(args);
		if (request == null) {
			return noSuchRequest(args);
		}
		if (request.response != null) {
			return Result.ok(request.response);
		}
		return request.error != null ? Result.fail(request.error) : Result.ok(false);
	}

	@Method(doc = "read(handle[, n]): what's come of its body, all of it or at most n bytes; false: none yet; \"\": its end; fails with why the request did, {why, ...}: network, denied")
	public Result read(Arguments args) {
		Request request = handle(args);
		if (request == null) {
			return noSuchRequest(args);
		}
		long most = args.count() > 1 ? Math.max(1, args.checkInteger(1)) : Long.MAX_VALUE;
		String data = request.body.take(most); // (everything held, up to most: read(2)'s way)
		if (data != null) {
			power.charge(DATA_COST * (int) Math.ceilDiv(data.length(), DATA_UNIT));
			return Result.ok(data);
		}
		if (request.error != null) {
			return Result.fail(request.error);
		}
		return request.ended ? Result.ok("") : Result.ok(false);
	}

	/** A handle no request has (closed, or never made): the call can't use it. */
	private static Result noSuchRequest(Arguments args) {
		return Result.fail(com.payonel.modularcomputers.runtime.Failure.badArgument(1, args.get(0), "handle"));
	}

	@Method(doc = "close(handle): stops a request: its connection closes, and what it had waiting goes")
	public Result close(Arguments args) {
		Request request = open.remove(args.checkInteger(0));
		if (request != null) {
			request.exchange.close();
			if (!request.ended && request.error == null) {
				logged(request, true); // (closed before its end: stopped, as a command its program stopped)
			}
		}
		return Result.ok();
	}

	@Method(doc = "pull(): its access log's oldest entry, a request's end: {event = \"request\", ok = true, method, url, result = {status, millis, bytes}}, or {event = \"request\", ok = false, method, url, why (network, denied, stopped: closed first, or a fault), its why's fields, call = {args (a body cut to its first 16 bytes), progress = {status (0: no response), millis, bytes, sent}}}; lost (how many went before it, the log full) when some did; or nil")
	public Result pull(Arguments args) {
		return Result.ok(log.pull());
	}

	/** Each server tick (its block's): its interrupt, for news that came on its requests' threads. */
	public void tick() {
		for (Map<String, Object> ended; (ended = ending.poll()) != null; ) {
			log.record(ended); // (raises its line: pending till it's taken)
		}
		if (news.getAndSet(false)) {
			line.raise();
		}
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	@Override
	public BusPower power() {
		return power;
	}

	@Override
	public boolean interruptPending() {
		// (a log entry is pending until it's taken, its pull: a program's call about its own request
		// answers for that request only, so a log reader hears every entry whenever it came; in mcos
		// every file open on the card takes the log's entries, so they don't stay)
		if (log.pending() || !ending.isEmpty()) { // (an end still on its way to the log is news too)
			return true;
		}
		for (Request request : open.values()) {
			if (request.fresh) {
				return true;
			}
		}
		return false;
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	/** Everything stops: its requests close, what they had goes. */
	private void closeAll() {
		open.values().forEach(request -> request.exchange.close());
		open.clear();
		ending.clear();
		log.clear();
	}

	@Override
	protected void dropData() {
		closeAll();
	}

	@Override
	public void powerOff() {
		closeAll();
	}
}
