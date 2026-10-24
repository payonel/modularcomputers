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
 * it said go to the server's log. In mcos it's a file: a line written runs a command, and a read
 * gives what the last one said.
 */
public final class OperatorCard extends Card {
	/** What a command is answered with: how it went ({@code result}: its value, 0 when it failed) and what it said ({@code output}: its lines). */
	public record Outcome(boolean success, long result, String output) {
	}

	/** How its block runs a command (in its world, at its place); none: it isn't in one. */
	private Function<String, Outcome> runner = command -> new Outcome(false, 0, "not in a world");
	/** What the last command said, for its file. */
	private String last = "";

	public OperatorCard() {
		super(CardKind.OPERATOR);
	}

	/** How its block runs a command. */
	public void runWith(Function<String, Outcome> runner) {
		this.runner = runner;
	}

	@Method(doc = "command(text): runs a server command (no leading /) at the card's block, as a command block does; returns {success, result, output}")
	public Result command(Arguments args) {
		String text = args.checkString(0).strip();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		if (text.isEmpty()) {
			return Result.fail("no command");
		}
		Outcome outcome = runner.apply(text);
		last = outcome.output();
		Map<String, Object> answer = new LinkedHashMap<>();
		answer.put("success", outcome.success());
		answer.put("result", outcome.result());
		answer.put("output", outcome.output());
		return Result.ok(answer);
	}

	@Method(doc = "get(): what the last command said")
	public Result get(Arguments args) {
		return Result.ok(last);
	}

	@Method(doc = "set({command = text}): runs a command, as command does (a line written to its file)")
	public Result set(Arguments args) {
		Object command = args.checkTable(0).get("command");
		if (!(command instanceof String text)) {
			return Result.fail("no command");
		}
		Result result = command(new Arguments(java.util.List.of(text)));
		if (!result.failed() && result.first() instanceof Map<?, ?> answer && Boolean.FALSE.equals(answer.get("success"))) {
			return Result.fail(last.isEmpty() ? "it failed" : last.strip()); // (a write that failed says why)
		}
		return result;
	}
}
