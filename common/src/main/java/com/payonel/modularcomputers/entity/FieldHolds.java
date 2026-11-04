package com.payonel.modularcomputers.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Every energy field's hold in a world, saved with it (the user's, 2026-11-04): the hold's one home,
 * read by its card and its field alike, so neither keeps a copy. A hold is what its field holds (by
 * its id) and where; a card's every way of ending (a release, taken out, out of reach or power)
 * deletes it, whether or not its field is loaded, and a field loading with no hold here lets go. A
 * card unloaded leaves its hold as it is: frozen in time till the card loads (when its field last
 * heard from it, {@link Hold#seen}).
 */
public final class FieldHolds extends SavedData {
	/** A hold: what's held (its id, its name), where its field is (what it holds' middle), and the game time its card last ticked. */
	public record Hold(UUID held, String name, Vec3 at, long seen) {
		static final Codec<Hold> CODEC = RecordCodecBuilder.create(hold -> hold.group(
			net.minecraft.core.UUIDUtil.STRING_CODEC.fieldOf("held").forGetter(Hold::held),
			Codec.STRING.fieldOf("name").forGetter(Hold::name),
			Vec3.CODEC.fieldOf("at").forGetter(Hold::at),
			Codec.LONG.fieldOf("seen").forGetter(Hold::seen)).apply(hold, Hold::new));
	}

	private static final Codec<FieldHolds> CODEC = Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, Hold.CODEC)
		.xmap(FieldHolds::new, holds -> holds.holds);

	private static final SavedDataType<FieldHolds> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath("modularcomputers", "field_holds"), FieldHolds::new, CODEC, null);

	private final Map<UUID, Hold> holds;

	private FieldHolds() {
		this(Map.of());
	}

	private FieldHolds(Map<UUID, Hold> holds) {
		this.holds = new HashMap<>(holds);
	}

	/** A world's holds. */
	public static FieldHolds of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** A hold, or null: none (released, or its card ended). */
	public Hold get(UUID hold) {
		return hold == null ? null : holds.get(hold);
	}

	/** A hold begins. */
	public void put(UUID hold, UUID held, String name, Vec3 at, long now) {
		holds.put(hold, new Hold(held, name, at, now));
		setDirty();
	}

	/** Where its field is now. */
	public void moved(UUID hold, Vec3 at) {
		Hold was = holds.get(hold);
		if (was != null && !was.at().equals(at)) {
			holds.put(hold, new Hold(was.held(), was.name(), at, was.seen()));
			setDirty();
		}
	}

	/** Its card ticked now (it's loaded: its field may act on its word). */
	public void seen(UUID hold, long now) {
		Hold was = holds.get(hold);
		if (was != null && was.seen() != now) {
			holds.put(hold, new Hold(was.held(), was.name(), was.at(), now)); // (not saved for this alone: a load starts it afresh)
		}
	}

	/** A hold ends. */
	public void remove(UUID hold) {
		if (hold != null && holds.remove(hold) != null) {
			setDirty();
		}
	}

	/** The holds there are (for tests). */
	List<UUID> all() {
		return List.copyOf(holds.keySet());
	}
}
