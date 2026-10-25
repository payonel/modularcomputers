package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.bus.BusOrder;
import com.payonel.modularcomputers.power.BatteryCharge;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Item data components. */
public final class ModComponents {
	/** A battery's charge: ticks of running a CPU, and when it was last worked out. */
	public static final DataComponentType<BatteryCharge> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("battery_charge"),
		DataComponentType.<BatteryCharge>builder()
			.persistent(RecordCodecBuilder.create(charge -> charge.group(
				Codec.intRange(0, Integer.MAX_VALUE).fieldOf("units").forGetter(BatteryCharge::units),
				Codec.LONG.optionalFieldOf("settled_at", BatteryCharge.UNSETTLED).forGetter(BatteryCharge::settledAt)
			).apply(charge, BatteryCharge::new)))
			.networkSynchronized(StreamCodec.composite(ByteBufCodecs.VAR_INT, BatteryCharge::units, ByteBufCodecs.VAR_LONG, BatteryCharge::settledAt, BatteryCharge::new))
			.build());

	/**
	 * A card's serial number, stamped the first time it goes into a computer or an expansion hub.
	 * Programs can address it as {@code id:<serial>} wherever it is (like Linux's
	 * {@code /dev/disk/by-id}), and a hub's computers keep its place by it, whichever slot it's in.
	 */
	public static final DataComponentType<UUID> SERIAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("serial"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/**
	 * A disk's files to come (user design, 2026-10-13): the name of a set of the mod's Lua files
	 * ({@code data/modularcomputers/lua/<name>/}), copied into the disk's folder the first time it's
	 * made, then gone. A crafted disk comes with mcos, every tier.
	 */
	public static final DataComponentType<String> DISK_IMAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("disk_image"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	/**
	 * A disk's format, "raw" (no filesystem); none: hfs. Its one home (the item, so its tooltip can
	 * say it): the disk device reads and writes it here ({@code Disk.formatIn}).
	 */
	public static final DataComponentType<String> DISK_FORMAT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("disk_format"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	/** A disk's format: its {@link #DISK_FORMAT}, or hfs. */
	public static String diskFormat(ItemStack stack) {
		return stack.getOrDefault(DISK_FORMAT, com.payonel.modularcomputers.device.Disk.HFS);
	}

	/** Sets a disk's format: none for hfs (the default), as a new disk's. */
	public static void diskFormat(ItemStack stack, String format) {
		if (format.equals(com.payonel.modularcomputers.device.Disk.HFS)) {
			stack.remove(DISK_FORMAT);
		} else {
			stack.set(DISK_FORMAT, format);
		}
	}

	/** A disk just wiped by crafting it alone: the id whose files go when the player takes it (then gone). */
	public static final DataComponentType<UUID> WIPED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("wiped"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/** The board (a computer's, a hub's, ...) this Bluetooth card links to. */
	public static final DataComponentType<UUID> BLUETOOTH_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bluetooth_link"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/**
	 * What an EEPROM holds (its image): plain text (no size limit for now), whatever the
	 * CPU's architecture. A Java CPU reads it as the name of a program class; a Lua CPU will read
	 * Lua. A new EEPROM comes flashed with the stand-in OS (the item's default); a wiped one holds "".
	 */
	public static final DataComponentType<String> EEPROM_IMAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("eeprom_image"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.stringUtf8(Integer.MAX_VALUE)).build());

	/**
	 * A computer's bus order (external devices by id, and the ones unplugged), kept on its item
	 * when it's broken, as a board's NVRAM keeps its boot order.
	 */
	public record SavedBusOrder(java.util.List<BusOrder.Entry> external, java.util.List<String> disconnected) {
		private static final Codec<BusOrder.Entry> ENTRY = RecordCodecBuilder.create(entry -> entry.group(
			Codec.STRING.fieldOf("key").forGetter(BusOrder.Entry::key),
			Codec.LONG.fieldOf("last_seen").forGetter(BusOrder.Entry::lastSeen)
		).apply(entry, BusOrder.Entry::new));
		static final Codec<SavedBusOrder> CODEC = RecordCodecBuilder.create(order -> order.group(
			ENTRY.listOf().fieldOf("external").forGetter(SavedBusOrder::external),
			Codec.STRING.listOf().fieldOf("disconnected").forGetter(SavedBusOrder::disconnected)
		).apply(order, SavedBusOrder::new));
	}

	public static final DataComponentType<SavedBusOrder> BUS_ORDER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bus_order"),
		DataComponentType.<SavedBusOrder>builder().persistent(SavedBusOrder.CODEC).build());

	private ModComponents() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}

	/** A device's label (user, 2026-10-24: set by programs, shown in its item's details), on its item: its one home. */
	public static final DataComponentType<String> LABEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("label"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	/** The label of the device an item is, if it has one. */
	public static String label(ItemStack stack) {
		return stack.get(LABEL);
	}

	/** Where a device's label lives: on its item, read and written there ({@code changed}: its block marks itself). */
	public static com.payonel.modularcomputers.bus.Label labelOn(java.util.function.Supplier<ItemStack> stack, Runnable changed) {
		return new com.payonel.modularcomputers.bus.Label(() -> label(stack.get()), label -> {
			if (label == null || label.isEmpty()) {
				stack.get().remove(LABEL);
			} else {
				stack.get().set(LABEL, label);
			}
			changed.run();
		});
	}

	/** The card's serial number, stamping a new one on if it has none yet. Server side only. */
	/** The ids an item carries: its own (a board's, a part's) and its parts' (a case's, a golem's, a hub's), each a thing in the world. */
	public static java.util.Set<UUID> identities(ItemStack stack) {
		java.util.Set<UUID> ids = new java.util.HashSet<>();
		UUID own = stack.get(SERIAL);
		if (own != null) {
			ids.add(own);
		}
		stack.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
			.nonEmptyItemCopyStream().forEach(part -> ids.addAll(identities(part)));
		return ids;
	}

	/**
	 * Whether placing {@code stack} would put a second of something into the world: one of its ids
	 * is in it already (a creative copy: {@link com.payonel.modularcomputers.board.PartHolds}).
	 */
	public static boolean copyOfOneInTheWorld(ItemStack stack, net.minecraft.world.level.Level level) {
		return level instanceof net.minecraft.server.level.ServerLevel server
			&& identities(stack).stream().anyMatch(id -> com.payonel.modularcomputers.board.PartHolds.held(id, server.getServer()));
	}

	/**
	 * An item placed (a block, a golem) is used up, in creative too, when it carries ids: it moved
	 * into the world, so no copy stays in the hand (the user's: a creative player's placing made
	 * copies writing the same disks). Creative puts a placed stack's count back, so it's taken out
	 * of the hand instead (the count goes back on a stack nobody holds).
	 */
	public static void placed(ItemStack stack, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
		if (player == null || !player.hasInfiniteMaterials() || identities(stack).isEmpty()) {
			return;
		}
		if (player.getItemInHand(hand) == stack) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		} else {
			stack.shrink(1);
		}
	}

	public static UUID serial(ItemStack card) {
		UUID serial = card.get(SERIAL);
		if (serial == null) {
			serial = UUID.randomUUID();
			card.set(SERIAL, serial);
		}
		return serial;
	}
}
