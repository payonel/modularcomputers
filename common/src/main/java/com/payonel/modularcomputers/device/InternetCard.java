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
 * it is the stand-in). A request ({@link #request}) gets a handle at once; what comes back are
 * events, its interrupt saying when there are some to {@link #pull}: the response (status and
 * headers), the body in pieces, its end, or why it failed. Each request runs on a thread of its
 * own ({@link HttpExchange}, which keeps it to the public internet: {@link InternetPolicy}); its
 * events wait in the card, at most {@link #EVENTS}, and a request whose next piece finds the card
 * full waits for room (the handshake: nothing dropped), so a big download goes at the program's
 * pace. At most {@link #MAX_OPEN} requests at a time; closing one ({@link #close}) stops it.
 */
public final class InternetCard extends Card {
	/** Requests open at once, a card. */
	public static final int MAX_OPEN = 4;
	/** And on the whole server: however many computers a player builds, its threads are bounded. */
	public static final int MAX_RUNNING = 64;
	private static final java.util.concurrent.atomic.AtomicInteger RUNNING = new java.util.concurrent.atomic.AtomicInteger();
	/** Events the card holds for the program. */
	public static final int EVENTS = 32;
	/** Its draw a tick, at rest. */
	public static final int DRAW = 4;
	/** What a request costs. */
	public static final int REQUEST_COST = PowerUnits.CPU;
	/** What a piece of a body costs. */
	public static final int DATA_COST = PowerUnits.CPU / 10;
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
	private final LinkedBlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>(EVENTS);
	private final Map<Long, HttpExchange> open = new ConcurrentHashMap<>();
	private final HttpExchange.Resolver resolver;
	private long nextHandle = 1;
	/** Its interrupt was raised for what's waiting; raised again once it's been emptied and more came. */
	private volatile boolean announced;

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

	@Method(doc = "request(url[, method[, headers[, body]]]): starts an HTTP or HTTPS request (method GET, or POST with a body; headers a table); returns its handle; what comes back are events to pull")
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
		HttpExchange exchange;
		try {
			exchange = HttpExchange.of(url, method, headers, body, policy.get(), resolver, listener(handle));
		} catch (IllegalArgumentException e) {
			return Result.fail(e.getMessage());
		}
		power.charge(REQUEST_COST);
		open.put(handle, exchange);
		RUNNING.incrementAndGet();
		THREADS.execute(() -> {
			try {
				exchange.run();
			} finally {
				RUNNING.decrementAndGet();
			}
		});
		return Result.ok(handle);
	}

	/** What a request's thread tells the card: its events, each waiting for room. */
	private HttpExchange.Listener listener(long handle) {
		return new HttpExchange.Listener() {
			@Override
			public void response(int status, String message, Map<String, String> headers) {
				Map<String, Object> event = event(handle, "response");
				event.put("status", (long) status);
				event.put("message", message);
				event.put("headers", new LinkedHashMap<String, Object>(headers));
				offer(handle, event);
			}

			@Override
			public boolean data(byte[] bytes) throws InterruptedException {
				Map<String, Object> event = event(handle, "data");
				event.put("data", new String(bytes, StandardCharsets.ISO_8859_1));
				return offer(handle, event);
			}

			@Override
			public void end() {
				offer(handle, event(handle, "end"));
				open.remove(handle);
			}

			@Override
			public void failed(String why) {
				Map<String, Object> event = event(handle, "error");
				event.put("why", why);
				offer(handle, event);
				open.remove(handle);
			}
		};
	}

	private static Map<String, Object> event(long handle, String kind) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("handle", handle);
		event.put("event", kind);
		return event;
	}

	/** An event, waiting for room; false once its request is closed (it stops). */
	private boolean offer(long handle, Map<String, Object> event) {
		try {
			while (open.containsKey(handle)) {
				if (events.offer(event, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
					return true;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return false;
	}

	@Method(doc = "pull(): the oldest event, as {handle, event, ...}: response (status, message, headers), data (data), end, or error (why); or nil")
	public Result pull(Arguments args) {
		Map<String, Object> event = events.poll();
		if (event != null && "data".equals(event.get("event"))) {
			power.charge(DATA_COST);
		}
		if (events.isEmpty()) {
			announced = false;
		}
		return Result.ok(event);
	}

	@Method(doc = "close(handle): stops a request: its connection closes, and its events still waiting go")
	public Result close(Arguments args) {
		long handle = args.checkInteger(0);
		HttpExchange exchange = open.remove(handle);
		if (exchange != null) {
			exchange.close();
		}
		events.removeIf(event -> Long.valueOf(handle).equals(event.get("handle")));
		return Result.ok();
	}

	/** Each server tick (its block's): its interrupt, once, for events that came on its requests' threads. */
	public void tick() {
		if (!announced && !events.isEmpty()) {
			announced = true;
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
		return !events.isEmpty();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	/** Everything stops: its requests close, their events go. */
	private void closeAll() {
		open.values().forEach(HttpExchange::close);
		open.clear();
		events.clear();
		announced = false;
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
