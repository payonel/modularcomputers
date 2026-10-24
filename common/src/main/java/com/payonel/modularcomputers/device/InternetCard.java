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
import java.util.concurrent.LinkedBlockingQueue;
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
 * waits in the card, at most {@link #BUFFERED} pieces a request, and a request whose next piece
 * finds no room waits (the handshake: nothing dropped), so a download goes at the program's pace.
 * At most {@link #MAX_OPEN} requests at a time.
 * <p>
 * Its stream ({@link #pull}) is its access log, as a web server's: each request as it ends, its
 * method, URL, status (or why it failed), how long it took and the bytes it brought.
 */
public final class InternetCard extends Card {
	/** Requests open at once, a card. */
	public static final int MAX_OPEN = 4;
	/** And on the whole server: however many computers a player builds, its threads are bounded. */
	public static final int MAX_RUNNING = 64;
	private static final java.util.concurrent.atomic.AtomicInteger RUNNING = new java.util.concurrent.atomic.AtomicInteger();
	/** Body pieces a request holds unread before its download waits. */
	public static final int BUFFERED = 16;
	/** Its access log's entries kept, at most: a small window (past it the oldest go; the user's: a reader sees what's current, at little memory). */
	public static final int LOG = 8;
	/** Its draw a tick, at rest. */
	public static final int DRAW = 4;
	/** What a request costs. */
	public static final int REQUEST_COST = PowerUnits.CPU;
	/** What a piece of a body costs. */
	public static final int DATA_COST = PowerUnits.CPU / 10;
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
	private final java.util.concurrent.ConcurrentLinkedDeque<Map<String, Object>> log = new java.util.concurrent.ConcurrentLinkedDeque<>();
	private final HttpExchange.Resolver resolver;
	private long nextHandle = 1;
	/** Something happened on a request's thread since the last tick: its interrupt, at the next. */
	private final java.util.concurrent.atomic.AtomicBoolean news = new java.util.concurrent.atomic.AtomicBoolean();

	/** A request: what's come of it, as its thread fills it in. */
	private final class Request {
		final String method;
		final String url;
		final long started = System.nanoTime();
		final LinkedBlockingQueue<String> body = new LinkedBlockingQueue<>(BUFFERED);
		volatile Map<String, Object> response;
		volatile boolean ended;
		volatile String error;
		volatile long bytes;
		/** What's left of a piece a read took only part of (the reader's, on the server thread: it reads first). */
		String rest;
		/** News the program hasn't looked at (its interrupt stays pending while any request has some). */
		volatile boolean fresh;
		HttpExchange exchange;

		Request(String method, String url) {
			this.method = method;
			this.url = url;
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

	@Method(doc = "request(url[, method[, headers[, body]]]): starts an HTTP or HTTPS request (GET, or POST with a body; headers a table); returns its handle")
	public Result request(Arguments args) {
		if (!policy.get().enabled()) {
			return Result.fail("the internet is turned off on this server");
		} else if (open.size() >= MAX_OPEN) {
			return Result.fail("too many requests open (" + MAX_OPEN + ")");
		} else if (RUNNING.get() >= MAX_RUNNING) {
			return Result.fail("the server's internet is busy: try again soon");
		}
		String url = args.checkString(0);
		String method = args.get(1) instanceof String verb ? verb : null;
		Map<String, String> headers = new LinkedHashMap<>();
		if (args.get(2) instanceof Map<?, ?> given) {
			given.forEach((name, value) -> headers.put(String.valueOf(name), String.valueOf(value)));
		}
		byte[] body = args.get(3) instanceof String text ? text.getBytes(StandardCharsets.ISO_8859_1) : null;
		long handle = nextHandle++;
		Request request = new Request(method != null ? method.toUpperCase(java.util.Locale.ROOT) : body == null ? "GET" : "POST", url);
		try {
			request.exchange = HttpExchange.of(url, method, headers, body, policy.get(), resolver, listener(handle, request));
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
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
				String piece = new String(bytes, StandardCharsets.ISO_8859_1);
				while (open.get(handle) == request) {
					if (request.body.offer(piece, ROOM_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
						request.bytes += bytes.length;
						request.told();
						return true;
					}
				}
				return false; // (closed: it stops)
			}

			@Override
			public void end() {
				request.ended = true;
				logged(request);
				request.told();
			}

			@Override
			public void failed(String why) {
				request.error = why;
				logged(request);
				request.told();
			}
		};
	}

	/** A request ended: its line in the access log. */
	private void logged(Request request) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("method", request.method);
		entry.put("url", request.url);
		Object status = request.response != null ? request.response.get("status") : null;
		entry.put("status", status != null ? status : 0L);
		entry.put("millis", (System.nanoTime() - request.started) / 1_000_000);
		entry.put("bytes", request.bytes);
		if (request.error != null) {
			entry.put("why", request.error);
		}
		log.addLast(entry);
		while (log.size() > LOG) {
			log.pollFirst();
		}
		news.set(true); // (its interrupt, once: a reader of its log hears)
	}

	/** The request a handle names, its news now looked at. */
	private Request handle(Arguments args) {
		Request request = open.get(args.checkInteger(0));
		if (request == null) {
			throw new Arguments.ArgumentError("no such request: " + args.get(0));
		}
		request.fresh = false;
		return request;
	}

	@Method(doc = "response(handle): its {status, message, headers} once they've come (headers by name, in lower case); false: not yet; fails with why the request did")
	public Result response(Arguments args) {
		Request request = handle(args);
		if (request.response != null) {
			return Result.ok(request.response);
		}
		return request.error != null ? Result.fail(request.error) : Result.ok(false);
	}

	@Method(doc = "read(handle[, n]): the next piece of its body (at most n bytes); false: none yet; \"\": its end; fails with why the request did")
	public Result read(Arguments args) {
		Request request = handle(args);
		long most = args.count() > 1 ? Math.max(1, args.checkInteger(1)) : Long.MAX_VALUE;
		String piece = request.rest != null ? request.rest : request.body.poll();
		request.rest = null;
		if (piece != null) {
			power.charge(DATA_COST);
			if (piece.length() <= most) {
				return Result.ok(piece);
			}
			request.rest = piece.substring((int) most); // (the next read's, before what's queued)
			return Result.ok(piece.substring(0, (int) most));
		}
		if (request.error != null) {
			return Result.fail(request.error);
		}
		return request.ended ? Result.ok("") : Result.ok(false);
	}

	@Method(doc = "close(handle): stops a request: its connection closes, and what it had waiting goes")
	public Result close(Arguments args) {
		Request request = open.remove(args.checkInteger(0));
		if (request != null) {
			request.exchange.close();
		}
		return Result.ok();
	}

	@Method(doc = "pull(): its access log's oldest entry, a request as it ended: {method, url, status, millis, bytes, why (when it failed)}; or nil")
	public Result pull(Arguments args) {
		return Result.ok(log.pollFirst());
	}

	/** Each server tick (its block's): its interrupt, for news that came on its requests' threads. */
	public void tick() {
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
		// (its log isn't held up: an entry is told once, so a program waiting on a request isn't woken over and over by a log nobody reads)
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
