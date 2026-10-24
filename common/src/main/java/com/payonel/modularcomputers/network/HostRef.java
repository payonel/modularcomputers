package com.payonel.modularcomputers.network;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What a packet about a terminal or a computer is for: a block (a case, a screen), by where it
 * is, or an entity (a golem), by its id, as both sides know it. Each side finds it in its own
 * world, and the server checks the player can reach it.
 */
public sealed interface HostRef {
	/** A block, at {@code pos}. */
	record AtBlock(BlockPos pos) implements HostRef {
		@Override
		public Object find(Level level) {
			return level.getBlockEntity(pos);
		}

		@Override
		public boolean reachable(Player player, double margin) {
			return player.isWithinBlockInteractionRange(pos, margin);
		}
	}

	/** An entity, by its id. */
	record OfEntity(int id) implements HostRef {
		@Override
		public Object find(Level level) {
			return level.getEntity(id);
		}

		@Override
		public boolean reachable(Player player, double margin) {
			var entity = player.level().getEntity(id);
			return entity != null && player.isWithinEntityInteractionRange(entity, margin);
		}
	}

	StreamCodec<ByteBuf, HostRef> STREAM_CODEC = ByteBufCodecs.BOOL.dispatch(
		ref -> ref instanceof OfEntity,
		entity -> entity
			? ByteBufCodecs.VAR_INT.map(id -> (HostRef) new OfEntity(id), ref -> ((OfEntity) ref).id())
			: BlockPos.STREAM_CODEC.map(pos -> (HostRef) new AtBlock(pos), ref -> ((AtBlock) ref).pos()));

	static HostRef of(BlockPos pos) {
		return new AtBlock(pos);
	}

	static HostRef of(net.minecraft.world.entity.Entity entity) {
		return new OfEntity(entity.getId());
	}

	/** What it is in {@code level}: a block entity, an entity, or null. */
	Object find(Level level);

	/** Whether {@code player} can reach it, within its interaction range and {@code margin}. */
	boolean reachable(Player player, double margin);

	/** It in {@code level}, if it's a {@code kind}. */
	default <T> Optional<T> find(Level level, Class<T> kind) {
		Object found = level == null ? null : find(level);
		return kind.isInstance(found) ? Optional.of(kind.cast(found)) : Optional.empty();
	}
}
