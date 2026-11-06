package com.payonel.modularcomputers.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
		assertThat(Failure.missing("legs").table()).isEqualTo(Map.of("why", "fault", "condition", "missing", "part", "legs", "message", "legs required"));
		assertThat(Failure.ioError("storage", new java.io.IOException("x")).table())
			.isEqualTo(Map.of("why", "fault", "condition", "io_error", "part", "storage", "message", "i/o error"));
		assertThat(Failure.internalError("board", new IllegalStateException("x")).table())
			.isEqualTo(Map.of("why", "fault", "condition", "internal_error", "part", "board", "message", "internal error"));
		assertThat(Failure.badArgument(2, "CONNECT", "method").table())
			.isEqualTo(Map.of("why", "bad_argument", "argument", 2L, "value", "CONNECT", "expected", "method"));
		assertThat(Failure.badArgument(1, null, "url").get("value")).as("nothing given: \"\", never missing").isEqualTo("");
		assertThat(Failure.denied("loopback", "127.0.0.1").table()).isEqualTo(Map.of("why", "denied", "reason", "loopback", "value", "127.0.0.1"));
		assertThat(Failure.limit("open_requests", 4).table()).isEqualTo(Map.of("why", "limit", "of", "open_requests", "max", 4L));
		assertThat(Failure.network("bad_chunk", null).table()).isEqualTo(Map.of("why", "network", "reason", "bad_chunk", "message", "bad chunked encoding"));
		assertThat(Failure.symbolicLink("/to", "/real").table()).isEqualTo(Map.of("why", "symbolic_link", "at", "/to", "target", "/real"));
		assertThat(Failure.badState("path", "/a", "absent").table()).isEqualTo(Map.of("why", "bad_state", "of", "path", "value", "/a", "expected", "absent"));
		assertThat(Failure.missingInputs("").table()).isEqualTo(Map.of("why", "missing_inputs", "name", ""));
		assertThat(Failure.noSpace("minecraft:stick").table()).isEqualTo(Map.of("why", "no_space", "name", "minecraft:stick"));
	}

	@Test
	void aFieldCantBeMissing() {
		assertThatThrownBy(() -> Failure.busy(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("busy's id");
		assertThatThrownBy(() -> Failure.denied("loopback", null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Failure.network("other", null)).as("a reason with no words: none").isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Failure.internalError(null, new IllegalStateException())).isInstanceOf(NullPointerException.class);
	}

	/** Every part a fault finds missing has its message; a part with none is refused, never said without one. */
	@Test
	void aMissingPartIsOneWithItsMessage() {
		for (String part : List.of("legs", "thrusters", "power", "bench", "robotics", "body", "position", "projector", "destination", "source")) {
			assertThat((String) Failure.missing(part).get("message")).as(part).isNotEmpty();
		}
		assertThatThrownBy(() -> Failure.missing("storage")).isInstanceOf(IllegalArgumentException.class);
	}

	/** The host's errors never reach a program: their text could tell of the host (its paths, its classes). */
	@Test
	void aFaultNeverSaysTheHostsError() {
		IllegalStateException host = new IllegalStateException("/home/someone/world/modularcomputers/disks/x");
		assertThat(Failure.internalError("storage", host).toString()).doesNotContain("/home", "IllegalState");
		assertThat(Failure.ioError("storage", new java.io.IOException(host.getMessage())).toString()).doesNotContain("/home");
		assertThat(Failure.network("tls", new javax.net.ssl.SSLException("sun.security.provider.certpath.SunCertPathBuilderException")).toString())
			.as("a network failure's words are its reason's").doesNotContain("sun.security");
	}
}
