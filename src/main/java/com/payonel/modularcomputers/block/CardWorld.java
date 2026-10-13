package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.Card;
import com.payonel.modularcomputers.device.MotionSensorCard;
import com.payonel.modularcomputers.device.RedstoneCard;
import com.payonel.modularcomputers.device.WeatherCard;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * What cards see of the world where they're installed (a computer, a hub): the glue between the
 * world and the plain-Java card devices. The block holding a card calls {@link #tick} each server
 * tick; block changes reach motion sensors through {@link #blockChanged} (a hook on the server's
 * block updates, {@code mixin/ServerLevelBlockUpdates}).
 */
public final class CardWorld {
	/** How often a motion sensor looks for entities that moved, in ticks. */
	private static final int MOTION_TICKS = 5;
	/** How far an entity must move to count. */
	private static final double MOVED = 0.1;
	/** Motion sensors at work, and where: block changes go to those in range. */
	private static final Map<MotionSensorCard, Watch> WATCHING = new WeakHashMap<>();

	private CardWorld() {
	}

	/** Where a motion sensor is, and the entities it saw last, where. */
	private static final class Watch {
		ServerLevel level;
		BlockPos pos;
		long seen;
		final Map<UUID, double[]> entities = new HashMap<>();
	}

	/** Each server tick, for each card in the block at {@code pos}. A card works while a running computer powers it. */
	public static void tick(Card card, ServerLevel level, BlockPos pos, long now) {
		if (card.power() == null || !card.power().powered()) {
			return;
		}
		switch (card) {
			case RedstoneCard redstone -> {
				for (Direction side : Direction.values()) {
					redstone.sense(side.ordinal(), level.getSignal(pos.relative(side), side));
				}
			}
			case WeatherCard weather -> {
				if (now % 20 == 0) {
					weather.sense(weatherAt(level, pos));
				}
			}
			case MotionSensorCard motion -> {
				Watch watch = WATCHING.computeIfAbsent(motion, m -> new Watch());
				watch.level = level;
				watch.pos = pos;
				watch.seen = now;
				if (now % MOTION_TICKS == 0) {
					entities(motion, watch);
				}
			}
			default -> {
			}
		}
	}

	/** The signal the redstone cards in a block put out toward a neighbour: the strongest. {@code direction} is from the neighbour to the block. */
	public static int redstone(Iterable<? extends Card> cards, Direction direction) {
		int side = direction.getOpposite().ordinal(); // the block's face toward that neighbour
		int strongest = 0;
		for (Card card : cards) {
			if (card instanceof RedstoneCard redstone) {
				strongest = Math.max(strongest, redstone.output(side));
			}
		}
		return strongest;
	}

	/** The weather where the block is, whether or not it sees the sky: clear, rain, snow or thunder. */
	static String weatherAt(ServerLevel level, BlockPos pos) {
		return weather(level.isRaining(), level.isThundering(), level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()));
	}

	/** The weather a card reports, from the world's and its biome's: clear, rain, snow or thunder. */
	public static String weather(boolean raining, boolean thundering, Biome.Precipitation precipitation) {
		if (!raining) {
			return "clear";
		}
		return switch (precipitation) {
			case NONE -> "clear"; // a desert's
			case SNOW -> "snow";
			case RAIN -> thundering ? "thunder" : "rain";
		};
	}

	private static void entities(MotionSensorCard motion, Watch watch) {
		int range = motion.range();
		AABB box = new AABB(watch.pos).inflate(range);
		Map<UUID, double[]> now = new HashMap<>();
		for (Entity entity : watch.level.getEntities((Entity) null, box, Entity::isAlive)) {
			double[] at = {entity.getX(), entity.getY(), entity.getZ()};
			now.put(entity.getUUID(), at);
			double[] was = watch.entities.get(entity.getUUID());
			if (was != null && Math.abs(at[0] - was[0]) < MOVED && Math.abs(at[1] - was[1]) < MOVED && Math.abs(at[2] - was[2]) < MOVED) {
				continue;
			}
			Map<String, Object> event = where(watch.pos, at[0], at[1], at[2]);
			event.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
			if (entity instanceof ItemEntity item) {
				event.put("item", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
				event.put("count", (long) item.getItem().getCount());
				motion.sense("drop", event);
			} else {
				event.put("id", entity.getUUID().toString());
				motion.sense("entity", event);
			}
		}
		watch.entities.clear();
		watch.entities.putAll(now);
	}

	/** A block changed somewhere (the server's block updates): motion sensors in range see fluid newly flowing, and doors. */
	public static void blockChanged(ServerLevel level, BlockPos pos, BlockState old, BlockState now) {
		if (WATCHING.isEmpty() || old == now) {
			return;
		}
		String kind = null;
		Map<String, Object> details = new LinkedHashMap<>();
		if (!now.getFluidState().isEmpty() && !now.getFluidState().equals(old.getFluidState())) {
			kind = "fluid";
			details.put("type", BuiltInRegistries.FLUID.getKey(now.getFluidState().getType()).toString());
		} else if (now.hasProperty(BlockStateProperties.OPEN) && old.hasProperty(BlockStateProperties.OPEN) && old.is(now.getBlock())
			&& old.getValue(BlockStateProperties.OPEN) != now.getValue(BlockStateProperties.OPEN)) {
			kind = "door";
			details.put("type", BuiltInRegistries.BLOCK.getKey(now.getBlock()).toString());
			details.put("open", now.getValue(BlockStateProperties.OPEN));
		}
		if (kind == null) {
			return;
		}
		long time = level.getGameTime();
		for (var watching : Map.copyOf(WATCHING).entrySet()) {
			Watch watch = watching.getValue();
			int range = watching.getKey().range();
			if (watch.level != level || time - watch.seen > 20 || Math.abs(pos.getX() - watch.pos.getX()) > range
				|| Math.abs(pos.getY() - watch.pos.getY()) > range || Math.abs(pos.getZ() - watch.pos.getZ()) > range) {
				continue;
			}
			Map<String, Object> event = where(watch.pos, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			event.putAll(details);
			watching.getKey().sense(kind, event);
		}
	}

	/** Where something is, relative to the block (its center), to a tenth of a block. */
	private static Map<String, Object> where(BlockPos pos, double x, double y, double z) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("x", Math.round((x - pos.getX() - 0.5) * 10) / 10.0);
		event.put("y", Math.round((y - pos.getY() - 0.5) * 10) / 10.0);
		event.put("z", Math.round((z - pos.getZ() - 0.5) * 10) / 10.0);
		return event;
	}
}
