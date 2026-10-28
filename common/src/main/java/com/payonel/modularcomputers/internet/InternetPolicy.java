package com.payonel.modularcomputers.internet;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

/**
 * What internet cards may reach (a server's settings: {@code internet_*}). A computer in the world
 * is someone else's program running on the server's network, so by default it reaches only the
 * public internet: never the server itself (loopback), its LAN (private ranges), link-local
 * addresses (which hold cloud machines' metadata services, 169.254.169.254, and their keys),
 * carrier-grade NAT, multicast, or reserved ranges, in IPv4 or IPv6 (an IPv4 address inside an
 * IPv6 one is judged as itself). A host is judged by every address it resolves to: one
 * forbidden address forbids it (a name can't smuggle a private address in among public ones).
 * Admins can deny more hosts or ranges, and allow some that would be refused (a LAN service they
 * mean computers to use); a deny wins.
 */
public record InternetPolicy(boolean enabled, List<String> allow, List<String> deny) {
	/** The default: on, the public internet only. */
	public static final InternetPolicy DEFAULT = new InternetPolicy(true, List.of(), List.of());

	/** Why a host can't be reached at these addresses, or null: it can. */
	public String refused(String host, List<InetAddress> addresses) {
		if (!enabled) {
			return "disabled";
		}
		if (addresses.isEmpty()) {
			return "unknown host";
		}
		for (InetAddress address : addresses) {
			if (listed(deny, host, address)) {
				return "denied";
			}
		}
		for (InetAddress address : addresses) {
			String why = forbidden(address);
			if (why != null && !listed(allow, host, address)) {
				return why;
			}
		}
		return null;
	}

	/** Why an address is off limits by default (loopback, private...), or null. */
	public static String forbidden(InetAddress address) {
		if (address instanceof Inet6Address six) {
			byte[] bytes = six.getAddress();
			InetAddress inner = embeddedIpv4(bytes);
			if (inner != null) {
				return forbidden(inner);
			}
			int first = bytes[0] & 0xFF;
			if (six.isLoopbackAddress() || six.isAnyLocalAddress()) {
				return "loopback";
			} else if (six.isLinkLocalAddress() || six.isSiteLocalAddress()) {
				return "link-local";
			} else if ((first & 0xFE) == 0xFC) {
				return "private";
			} else if (six.isMulticastAddress()) {
				return "multicast";
			} else if (first == 0x20 && (bytes[1] & 0xFF) == 0x01 && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) {
				return "documentation"; // 2001:db8::/32
			} else if (first == 0x20 && bytes[1] == 0x01 && bytes[2] == 0 && bytes[3] == 0) {
				return "teredo"; // 2001::/32: an IPv4 address hidden in it
			}
			return null;
		}
		byte[] b = address.getAddress();
		int a0 = b[0] & 0xFF;
		int a1 = b[1] & 0xFF;
		if (a0 == 0 || address.isAnyLocalAddress()) {
			return "unspecified"; // 0.0.0.0/8: "this network" (0.0.0.0 is this host on Linux)
		} else if (a0 == 127) {
			return "loopback"; // (the server itself)
		} else if (a0 == 10 || a0 == 172 && a1 >= 16 && a1 <= 31 || a0 == 192 && a1 == 168) {
			return "private"; // (the server's network)
		} else if (a0 == 169 && a1 == 254) {
			return "link-local"; // (a cloud's metadata lives here)
		} else if (a0 == 100 && a1 >= 64 && a1 <= 127) {
			return "shared"; // (carrier-grade NAT)
		} else if (a0 == 192 && a1 == 0 && (b[2] & 0xFF) == 0) {
			return "reserved"; // (IETF protocol assignments)
		} else if (a0 == 198 && (a1 == 18 || a1 == 19)) {
			return "reserved"; // (benchmarking)
		} else if (a0 >= 224) {
			return "reserved"; // (multicast, or reserved)
		}
		return null;
	}

	/** The IPv4 address an IPv6 one carries (mapped ::ffff:a.b.c.d, compatible ::a.b.c.d, NAT64 64:ff9b::/96, 6to4 2002::/16), or null. */
	private static InetAddress embeddedIpv4(byte[] six) {
		boolean zeros = true;
		for (int i = 0; i < 10; i++) {
			zeros &= six[i] == 0;
		}
		int offset = -1;
		if (zeros && (six[10] == (byte) 0xFF && six[11] == (byte) 0xFF || six[10] == 0 && six[11] == 0)) {
			offset = 12; // mapped, or compatible (:: and ::1 are judged as IPv6: loopback, unspecified)
			if (six[10] == 0 && (six[12] | six[13] | six[14]) == 0 && (six[15] & 0xFE) == 0) {
				return null;
			}
		} else if (six[0] == 0x00 && six[1] == 0x64 && six[2] == (byte) 0xFF && six[3] == (byte) 0x9B) {
			offset = 12; // NAT64
		} else if (six[0] == 0x20 && six[1] == 0x02) {
			offset = 2; // 6to4
		}
		if (offset < 0) {
			return null;
		}
		try {
			return Inet4Address.getByAddress(new byte[] {six[offset], six[offset + 1], six[offset + 2], six[offset + 3]});
		} catch (java.net.UnknownHostException e) {
			return null;
		}
	}

	/** Whether a host or one of its addresses is on a list: a host name (a.example.com), a domain (.example.com: it and its subdomains), an address, or a range (10.0.0.0/8). */
	static boolean listed(List<String> list, String host, InetAddress address) {
		String name = host.toLowerCase(Locale.ROOT);
		for (String entry : list) {
			String rule = entry.strip().toLowerCase(Locale.ROOT);
			if (rule.isEmpty()) {
				continue;
			}
			if (rule.startsWith(".") ? name.endsWith(rule) || name.equals(rule.substring(1)) : name.equals(rule)) {
				return true;
			}
			if (inRange(rule, address)) {
				return true;
			}
		}
		return false;
	}

	/** An address in an address or range (CIDR). */
	private static boolean inRange(String rule, InetAddress address) {
		String[] parts = rule.split("/", 2);
		InetAddress base;
		try {
			if (!parts[0].matches("[0-9a-f.:]+")) {
				return false; // (a name, not an address: InetAddress would look it up)
			}
			base = InetAddress.getByName(parts[0]);
		} catch (java.net.UnknownHostException e) {
			return false;
		}
		byte[] a = address.getAddress();
		byte[] r = base.getAddress();
		if (a.length != r.length) {
			return false;
		}
		int bits = r.length * 8;
		if (parts.length > 1) {
			try {
				bits = Math.clamp(Integer.parseInt(parts[1]), 0, r.length * 8);
			} catch (NumberFormatException e) {
				return false;
			}
		}
		for (int i = 0; i < bits; i++) {
			int mask = 0x80 >> (i % 8);
			if ((a[i / 8] & mask) != (r[i / 8] & mask)) {
				return false;
			}
		}
		return true;
	}
}
