package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A why's builder is its one shape (the user's): every field of it there, always, none missing. */
class FailureTest {
	@Test
	void eachWhyHasExactlyItsFields() {
		assertThat(Failure.busy("x").table()).isEqualTo(Map.of("why", "busy", "id", "x"));
		assertThat(Failure.held("x").table()).isEqualTo(Map.of("why", "held", "id", "x"));
		assertThat(Failure.drifted(Map.of("forward", 1L, "right", 0L, "up", 0L)).table())
			.isEqualTo(Map.of("why", "drifted", "drift", Map.of("forward", 1L, "right", 0L, "up", 0L)));
		assertThat(Failure.systemFailure("legs").table()).isEqualTo(Map.of("why", "system_failure", "missing", "legs"));
		assertThat(Failure.badArgument(2, "CONNECT", "method").table())
			.isEqualTo(Map.of("why", "bad_argument", "argument", 2L, "value", "CONNECT", "expected", "method"));
		assertThat(Failure.badArgument(1, null, "url").get("value")).as("nothing given: \"\", never missing").isEqualTo("");
		assertThat(Failure.denied("loopback", "127.0.0.1").table()).isEqualTo(Map.of("why", "denied", "reason", "loopback", "value", "127.0.0.1"));
		assertThat(Failure.limit("open_requests", 4).table()).isEqualTo(Map.of("why", "limit", "of", "open_requests", "max", 4L));
		assertThat(Failure.network("bad_chunk", "").table()).isEqualTo(Map.of("why", "network", "reason", "bad_chunk", "message", ""));
		assertThat(Failure.missingInputs("").table()).isEqualTo(Map.of("why", "missing_inputs", "name", ""));
		assertThat(Failure.noSpace("minecraft:stick").table()).isEqualTo(Map.of("why", "no_space", "name", "minecraft:stick"));
	}

	@Test
	void aFieldCantBeMissing() {
		assertThatThrownBy(() -> Failure.busy(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("busy's id");
		assertThatThrownBy(() -> Failure.denied("loopback", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Failure.network(null, "")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Failure.systemFailure(null)).isInstanceOf(NullPointerException.class);
	}

	/** A refusal not yet named keeps its old text as its why, no fields; a program still gets a table. */
	@Test
	void aRefusalNotYetNamedIsATableToo() {
		assertThat(Result.fail("no entity: x").failure().table()).isEqualTo(Map.of("why", "no entity: x"));
	}
}
