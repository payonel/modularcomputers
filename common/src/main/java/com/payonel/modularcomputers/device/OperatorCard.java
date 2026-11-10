package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * An operator card (user design, 2026-10-24; OpenComputers' debug card, named for Minecraft's
 * operators: Lua has its own debug): creative only, it runs server commands as a command block
 * does, at its block, with a command block's permission ({@code time set day}, {@code weather
 * clear}, {@code give}...; not an operator's own, {@code op} or {@code stop}). Each command and what
 * it said go to the server's log. A command that ran answers with what Minecraft gave of it, as an
 * HTTP response is a server's (the user's, 2026-11-10: the card dispatched it; the rest is the
 * command's): how many of its runs succeeded (a command block's SuccessCount), its result, what it
 * said, and its last message's translation key. In mcos it's a file: a line written runs a command,
 * and a read gives what the last one written through that file said.
 */
public final class OperatorCard extends Card {
	/**
	 * What Minecraft gave of a command: how many of its runs succeeded ({@code successes}: a command
	 * block's SuccessCount), its last run's value ({@code result}: what /execute store result would
	 * keep), what it said ({@code message}: its lines), and its last message's translation key
	 * ({@code key}: the message a command block keeps as its LastOutput; "" for none, or a plain one).
	 */
	public record Outcome(long successes, long result, String message, String key) {
	}

	/** How its block runs a command (in its world, at its place); null: it isn't in one (a client's copy, a test's). */
	private Function<String, Outcome> runner = command -> null;

	public OperatorCard() {
		super(CardKind.OPERATOR);
	}

	/** How its block runs a command. */
	public void runWith(Function<String, Outcome> runner) {
		this.runner = runner;
	}

	@Method(doc = "command(text): runs a server command (a leading / allowed) at the card's block, as a command block does; returns what Minecraft gave of it, {successes (how many of its runs succeeded: 0, it didn't), result (its last run's value), message (what it said), key (its last message's translation key, \"\" for none)}, or false and why: bad_argument (an empty command)")
	public Result command(Arguments args) {
		String text = args.checkString(0).strip();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		if (text.isEmpty()) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.badArgument(1, text, "command")); // (an empty one: nothing to run)
		}
		Outcome outcome = runner.apply(text);
		if (outcome == null) { // (no world to run it in: the host's bug, never a program's)
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.internalError(type().name().toLowerCase(java.util.Locale.ROOT),
				new IllegalStateException("an operator card in no world")));
		}
		Map<String, Object> answer = new LinkedHashMap<>();
		answer.put("successes", outcome.successes());
		answer.put("result", outcome.result());
		answer.put("message", outcome.message());
		answer.put("key", outcome.key());
		return Result.ok(answer);
	}

}
