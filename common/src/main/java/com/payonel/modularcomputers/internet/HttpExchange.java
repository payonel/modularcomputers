package com.payonel.modularcomputers.internet;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One HTTP/1.1 request, as an internet card makes it, on a thread of its own (never the server's).
 * Its own small client, not Java's, for one reason: it connects to exactly the address it checked.
 * It resolves the host, has its {@link InternetPolicy} judge every address, and opens the socket to
 * the one it judged, so a name can't resolve to a public address for the check and a private one
 * for the connection (DNS rebinding). HTTPS is TLS on that socket, the server's certificate checked
 * against the host's name (SNI and hostname verification), as a browser does.
 * <p>
 * It sends only what the program asked for and what HTTP needs: no proxy, no cookies, no
 * credentials, its own User-Agent; the program's headers are checked (no line breaks, nothing that
 * reframes the request: Host, Content-Length, Transfer-Encoding, Connection are its own). It never
 * follows a redirect (the program does, so each hop is judged again). The response comes to the
 * {@link Listener} as it's read: its status and headers, then its body in pieces, each handed over
 * only when the card has room (a full card stops the reading: TCP's own flow control), then its end
 * or why it failed. Limits: {@link #CONNECT_MILLIS} to connect, {@link #READ_MILLIS} with nothing
 * read, {@link #TOTAL_MILLIS} in all, {@link #MAX_HEADERS} bytes of headers.
 */
public final class HttpExchange implements Runnable {
	/** How long connecting may take. */
	public static final int CONNECT_MILLIS = 10_000;
	/** How long a read may wait with nothing coming. */
	public static final int READ_MILLIS = 30_000;
	/** How long a request may take in all (a server dripping a byte a while can't keep its thread forever). */
	public static final long TOTAL_MILLIS = 10 * 60_000;
	/** The most a response's status line and headers may take, in bytes. */
	public static final int MAX_HEADERS = 64 * 1024;
	/** The most a request's body may be, in bytes. */
	public static final int MAX_BODY = 1024 * 1024;
	/** A piece of a response's body handed over at a time, at most. */
	public static final int CHUNK = 4096;
	/** What it says it is. */
	public static final String USER_AGENT = "ModularComputers";
	/** Headers it writes itself, which a program may not. */
	private static final Set<String> OWN = Set.of("host", "content-length", "transfer-encoding", "connection", "upgrade", "te", "trailer",
		"keep-alive", "proxy-connection", "expect");
	/** The methods it sends: HTTP's (no CONNECT: a tunnel isn't a request). */
	public static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
	private static final int HTTP_PORT = 80;
	private static final int HTTPS_PORT = 443;

	/** What the exchange tells its card. Called on the exchange's thread. */
	public interface Listener {
		void response(int status, String message, Map<String, String> headers);

		/** A piece of the body: returns once the card has taken it (it waits for room); false: stop (the card closed it). */
		boolean data(byte[] bytes) throws InterruptedException;

		void end();

		void failed(String why);
	}

	/** How a host's name becomes addresses (the system's resolver; a test's). */
	public interface Resolver {
		List<InetAddress> resolve(String host) throws IOException;
	}

	public static final Resolver SYSTEM = host -> Arrays.asList(InetAddress.getAllByName(host));

	private final URI uri;
	private final String method;
	private final Map<String, String> headers;
	private final byte[] body;
	private final InternetPolicy policy;
	private final Resolver resolver;
	private final Listener listener;
	private volatile Socket socket;
	private volatile boolean closed;
	/** When it must be done by (System.nanoTime), from its start. */
	private long deadline;

	private HttpExchange(URI uri, String method, Map<String, String> headers, byte[] body, InternetPolicy policy, Resolver resolver, Listener listener) {
		this.uri = uri;
		this.method = method;
		this.headers = headers;
		this.body = body;
		this.policy = policy;
		this.resolver = resolver;
		this.listener = listener;
	}

	/** A request, checked before any thread starts: why it can't be made, as an exception. */
	public static HttpExchange of(String url, String method, Map<String, String> headers, byte[] body, InternetPolicy policy, Resolver resolver,
		Listener listener) {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("not a URL: " + url);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new IllegalArgumentException("not http: " + url);
		} else if (uri.getHost() == null || uri.getRawUserInfo() != null) {
			throw new IllegalArgumentException(uri.getRawUserInfo() != null ? "credentials: " + url : "no host: " + url); // (credentials in a URL aren't sent)
		}
		String verb = method == null ? (body == null ? "GET" : "POST") : method.toUpperCase(Locale.ROOT);
		if (!METHODS.contains(verb)) {
			throw new IllegalArgumentException("not a method: " + method);
		} else if (body != null && body.length > MAX_BODY) {
			throw new IllegalArgumentException("too long: " + MAX_BODY);
		}
		Map<String, String> checked = new LinkedHashMap<>();
		for (var header : headers.entrySet()) {
			String name = header.getKey();
			String value = header.getValue();
			if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)) {
				throw new IllegalArgumentException("not a header: " + name);
			} else if (OWN.contains(name.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("reserved header: " + name); // (one the card writes itself)
			}
			checked.put(name, value);
		}
		return new HttpExchange(uri, verb, checked, body, policy, resolver, listener);
	}

	/** Stops it: its connection closes, and its thread ends. */
	public void close() {
		closed = true;
		Socket open = socket;
		if (open != null) {
			try {
				open.close();
			} catch (IOException e) {
				// (closing)
			}
		}
	}

	@Override
	public void run() {
		try {
			exchange();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException e) {
			if (!closed) {
				listener.failed(why(e));
			}
		} finally {
			close();
		}
	}

	private static String why(Exception e) {
		return switch (e) {
			case java.net.UnknownHostException unknown -> "unknown host";
			case java.net.SocketTimeoutException timeout -> "timed out";
			case java.net.ConnectException refused -> "connection refused";
			case javax.net.ssl.SSLException tls -> "tls: " + tls.getMessage();
			default -> e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		};
	}

	/** Past its deadline: it ends. */
	private void inTime() throws IOException {
		if (System.nanoTime() > deadline) {
			throw new java.net.SocketTimeoutException("timed out");
		}
	}

	private void exchange() throws IOException, InterruptedException {
		deadline = System.nanoTime() + TOTAL_MILLIS * 1_000_000;
		String host = uri.getHost().startsWith("[") ? uri.getHost().substring(1, uri.getHost().length() - 1) : uri.getHost();
		List<InetAddress> addresses = resolver.resolve(host);
		String refused = policy.refused(host, addresses);
		if (refused != null) {
			listener.failed("refused: " + refused);
			return;
		}
		boolean tls = uri.getScheme().equalsIgnoreCase("https");
		int port = uri.getPort() >= 0 ? uri.getPort() : tls ? HTTPS_PORT : HTTP_PORT;
		Socket plain = new Socket();
		socket = plain;
		if (closed) {
			return;
		}
		plain.connect(new InetSocketAddress(addresses.getFirst(), port), CONNECT_MILLIS); // (the address it judged: no second lookup)
		plain.setSoTimeout(READ_MILLIS);
		Socket open = plain;
		if (tls) {
			SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, port, true);
			SSLParameters parameters = secure.getSSLParameters();
			parameters.setEndpointIdentificationAlgorithm("HTTPS"); // the certificate must name the host
			if (!host.matches("[0-9.]+|.*:.*")) {
				parameters.setServerNames(List.of(new SNIHostName(host)));
			}
			secure.setSSLParameters(parameters);
			secure.startHandshake();
			open = secure;
			socket = secure;
		}
		request(open.getOutputStream(), host, port, tls);
		response(new BufferedInputStream(open.getInputStream()));
	}

	private void request(OutputStream out, String host, int port, boolean tls) throws IOException {
		String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
		if (uri.getRawQuery() != null) {
			path += "?" + uri.getRawQuery();
		}
		boolean usual = port == (tls ? HTTPS_PORT : HTTP_PORT);
		String hostHeader = (host.contains(":") ? "[" + host + "]" : host) + (usual ? "" : ":" + port);
		StringBuilder head = new StringBuilder();
		head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
		head.append("Host: ").append(hostHeader).append("\r\n");
		head.append("Connection: close\r\n");
		if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("user-agent"))) {
			head.append("User-Agent: ").append(USER_AGENT).append("\r\n");
		}
		if (headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("accept-encoding"))) {
			head.append("Accept-Encoding: identity\r\n"); // (the body comes as it is: the program decodes nothing)
		}
		for (var header : headers.entrySet()) {
			head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
		}
		if (body != null) {
			head.append("Content-Length: ").append(body.length).append("\r\n");
		}
		head.append("\r\n");
		out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
		if (body != null) {
			out.write(body);
		}
		out.flush();
	}

	private void response(InputStream in) throws IOException, InterruptedException {
		int[] budget = {MAX_HEADERS};
		String statusLine;
		int status;
		Map<String, String> fields;
		do { // (1xx answers come first: skipped)
			statusLine = line(in, budget);
			String[] parts = statusLine.split(" ", 3);
			if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
				throw new IOException("not http");
			}
			try {
				status = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				throw new IOException("not http");
			}
			fields = headers(in, budget);
		} while (status >= 100 && status < 200);
		String message = statusLine.split(" ", 3).length > 2 ? statusLine.split(" ", 3)[2] : "";
		listener.response(status, message, fields);
		boolean none = method.equals("HEAD") || status == 204 || status == 304;
		if (!none) {
			String encoding = fields.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
			String length = fields.get("content-length");
			if (encoding.contains("chunked")) {
				chunked(in, budget);
			} else if (length != null) {
				long left;
				try {
					left = Long.parseLong(length.strip());
				} catch (NumberFormatException e) {
					throw new IOException("bad content-length");
				}
				copy(in, left);
			} else {
				copy(in, -1); // (until the connection closes)
			}
		}
		if (!closed) {
			listener.end();
		}
	}

	/** {@code left} bytes of body (all of it, to the end, when -1), handed over a piece at a time. */
	private void copy(InputStream in, long left) throws IOException, InterruptedException {
		byte[] buffer = new byte[CHUNK];
		while (left != 0 && !closed) {
			inTime();
			int got = in.read(buffer, 0, left < 0 ? CHUNK : (int) Math.min(CHUNK, left));
			if (got < 0) {
				if (left > 0) {
					throw new IOException("closed early");
				}
				return;
			}
			if (!listener.data(Arrays.copyOf(buffer, got))) {
				close();
				return;
			}
			if (left > 0) {
				left -= got;
			}
		}
	}

	private void chunked(InputStream in, int[] budget) throws IOException, InterruptedException {
		while (!closed) {
			inTime();
			String size = line(in, budget).split(";", 2)[0].strip();
			long length;
			try {
				length = Long.parseLong(size, 16);
			} catch (NumberFormatException e) {
				throw new IOException("bad chunk");
			}
			if (length == 0) {
				headers(in, budget); // (its trailer)
				return;
			}
			copy(in, length);
			line(in, budget); // (the chunk's CRLF)
		}
	}

	/** A line of the head (CRLF, or LF, ended), within what's left of its budget. */
	private static String line(InputStream in, int[] budget) throws IOException {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		while (true) {
			int c = in.read();
			if (c < 0) {
				throw new IOException("closed early");
			} else if (--budget[0] < 0) {
				throw new IOException("headers too long");
			} else if (c == '\n') {
				String text = line.toString(StandardCharsets.ISO_8859_1);
				return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
			}
			line.write(c);
		}
	}

	/** Header fields, to the empty line: names in lower case (as HTTP/2 has them), repeats joined with ", ". */
	private static Map<String, String> headers(InputStream in, int[] budget) throws IOException {
		Map<String, String> fields = new LinkedHashMap<>();
		for (String line = line(in, budget); !line.isEmpty(); line = line(in, budget)) {
			int colon = line.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String name = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).strip();
			fields.merge(name, value, (a, b) -> a + ", " + b);
		}
		return fields;
	}
}
