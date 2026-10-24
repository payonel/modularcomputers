package com.payonel.modularcomputers.internet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What internet cards may reach: the public internet only, by default; every address a host resolves to judged. */
class InternetPolicyTest {
	private static InetAddress at(String address) throws Exception {
		return InetAddress.getByName(address); // (literals only: no lookup)
	}

	@Test
	void theServersOwnMachineAndNetworkAndSpecialRangesAreRefused() throws Exception {
		for (String address : List.of("127.0.0.1", "127.255.0.9", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
			"169.254.169.254", "100.64.0.1", "224.0.0.1", "255.255.255.255", "192.0.0.8", "198.18.0.1",
			"::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254",
			"64:ff9b::7f00:1", "2002:7f00:1::", "2002:c0a8:101::", "2001:db8::1", "2001:0:4136:e378::1")) {
			assertThat(InternetPolicy.forbidden(at(address))).as(address).isNotNull();
		}
	}

	@Test
	void thePublicInternetIsReached() throws Exception {
		for (String address : List.of("93.184.216.34", "1.1.1.1", "8.8.8.8", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111",
			"::ffff:93.184.216.34")) {
			assertThat(InternetPolicy.forbidden(at(address))).as(address).isNull();
		}
	}

	@Test
	void oneForbiddenAddressAmongAHostsRefusesIt() throws Exception {
		assertThat(InternetPolicy.DEFAULT.refused("rebind.example", List.of(at("93.184.216.34"), at("127.0.0.1")))).isNotNull();
		assertThat(InternetPolicy.DEFAULT.refused("example.com", List.of(at("93.184.216.34")))).isNull();
		assertThat(InternetPolicy.DEFAULT.refused("nowhere", List.of())).isEqualTo("unknown host");
	}

	@Test
	void anAdminAllowsSomeAndDeniesMoreADenyWinning() throws Exception {
		InternetPolicy policy = new InternetPolicy(true, List.of("192.168.5.0/24", "nas.lan"), List.of(".evil.example", "1.1.1.1", "192.168.5.9"));
		assertThat(policy.refused("printer", List.of(at("192.168.5.20")))).as("an allowed range").isNull();
		assertThat(policy.refused("nas.lan", List.of(at("10.0.0.5")))).as("an allowed host").isNull();
		assertThat(policy.refused("other", List.of(at("192.168.6.1")))).as("outside it").isNotNull();
		assertThat(policy.refused("a.evil.example", List.of(at("8.8.8.8")))).as("a denied domain").isNotNull();
		assertThat(policy.refused("evil.example", List.of(at("8.8.8.8")))).as("the domain itself").isNotNull();
		assertThat(policy.refused("dns", List.of(at("1.1.1.1")))).as("a denied address").isNotNull();
		assertThat(policy.refused("x", List.of(at("192.168.5.9")))).as("a deny wins over an allow").isNotNull();
		assertThat(new InternetPolicy(false, List.of(), List.of()).refused("example.com", List.of(at("93.184.216.34")))).as("off").isNotNull();
	}
}
