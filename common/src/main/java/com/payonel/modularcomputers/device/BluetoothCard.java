package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Bluetooth card in a computer, on its bus (user, 2026-10-24): the link it makes, as the player
 * makes it by hand (right-clicking a block with it), read and set by programs. A link names a
 * <em>board</em> (a computer's, a hub's, a screen's...), not a card: its id, a UUID. Its board is
 * the one its computer's cards are linked to by others. The link lives on the card itself (its
 * item), read and written there.
 */
public final class BluetoothCard extends Card {
	private Supplier<UUID> link = () -> null;
	private Consumer<UUID> relink = id -> {
	};
	private Supplier<UUID> board = () -> null;

	public BluetoothCard() {
		super(CardKind.BLUETOOTH);
	}

	/** Where its link lives (its item): read, and written (null: no link). */
	public void linkIn(Supplier<UUID> read, Consumer<UUID> write) {
		this.link = read;
		this.relink = write;
	}

	/** The board it's in (its computer's): what other cards link to, to reach it. */
	public void boardFrom(Supplier<UUID> board) {
		this.board = board;
	}

	@Method(doc = "getLink(): the id of the board it links to, or nil")
	public Result getLink(Arguments args) {
		UUID id = link.get();
		return Result.ok(id == null ? null : id.toString());
	}

	@Method(doc = "setLink(id): links it to the board with that id (nil: to none), as linking it by hand does; the board needn't be there now")
	public Result setLink(Arguments args) {
		Object id = args.count() == 0 ? null : args.get(0);
		if (id == null) {
			relink.accept(null);
			return Result.ok();
		}
		if (!(id instanceof String text)) {
			return Result.fail("not a board id: " + id);
		}
		try {
			relink.accept(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return Result.fail("not a board id: " + text);
		}
		return Result.ok();
	}

	@Method(doc = "get(): {link = the board it links to (nil: none), board = the board it's in}")
	public Result get(Arguments args) {
		java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
		UUID linked = link.get();
		if (linked != null) {
			state.put("link", linked.toString());
		}
		UUID in = board.get();
		if (in != null) {
			state.put("board", in.toString());
		}
		return Result.ok(state);
	}

	@Method(doc = "set({link = id}): links it to that board ({link = false}: to none), as setLink")
	public Result set(Arguments args) {
		Object linked = args.checkTable(0).get("link");
		return setLink(new Arguments(java.util.Collections.singletonList(Boolean.FALSE.equals(linked) ? null : linked)));
	}

	@Method(doc = "getBoard(): the id of the board it's in (its computer's): what another card links to, to reach it")
	public Result getBoard(Arguments args) {
		UUID id = board.get();
		return Result.ok(id == null ? null : id.toString());
	}
}
