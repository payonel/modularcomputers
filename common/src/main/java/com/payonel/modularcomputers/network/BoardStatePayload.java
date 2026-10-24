package com.payonel.modularcomputers.network;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.block.BoardHolder;
import com.payonel.modularcomputers.board.BoardAssembly;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Server to client: a board's state as players see it (its parts, bus order, power, screens: its
 * save without the program or its devices' data), for a board an entity holds (a golem's): a block
 * sends its own in its block update, an entity has none.
 */
public record BoardStatePayload(HostRef host, CompoundTag state) implements CustomPacketPayload {
	public static final Type<BoardStatePayload> TYPE = new Type<>(ModularComputers.id("board_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BoardStatePayload> CODEC = StreamCodec.composite(
		HostRef.STREAM_CODEC, BoardStatePayload::host,
		ByteBufCodecs.COMPOUND_TAG, BoardStatePayload::state,
		BoardStatePayload::new);

	/** {@code board}'s state now, for {@code host}'s players. */
	public static BoardStatePayload of(HostRef host, BoardAssembly board, HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		board.save(output, true);
		return new BoardStatePayload(host, output.buildResult());
	}

	/** On the client: into its copy of the board. */
	public void apply(Level level) {
		host.find(level, BoardHolder.class).ifPresent(holder ->
			holder.board().load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), state)));
	}

	@Override
	public Type<BoardStatePayload> type() {
		return TYPE;
	}
}
