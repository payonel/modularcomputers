package com.payonel.modularcomputers.neoforge;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/** The common code's {@link Platform}, on NeoForge. */
public final class NeoForgePlatform implements Platform {
	@Override
	public String version() {
		return ModList.get().getModContainerById(ModularComputers.MOD_ID)
			.map(mod -> mod.getModInfo().getVersion().toString())
			.orElse("unknown");
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public List<String> files(String folder) {
		String prefix = folder.substring(1) + "/";
		List<String> files = new ArrayList<>();
		ModList.get().getModFileById(ModularComputers.MOD_ID).getFile().getContents().visitContent(folder.substring(1), (path, resource) -> {
			if (path.startsWith(prefix)) {
				files.add(path.substring(prefix.length()));
			}
		});
		return files;
	}

	@Override
	public boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection.hasChannel(type);
	}

	@Override
	public CreativeModeTab.Builder creativeTab(Supplier<List<ItemStack>> items) {
		return CreativeModeTab.builder().displayItems((parameters, output) -> output.acceptAll(items.get()));
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> menuType(BlockMenu<M> factory) {
		return IMenuTypeExtension.create((id, inventory, data) -> factory.create(id, inventory, BlockPos.STREAM_CODEC.decode(data)));
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider, pos);
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> hostMenuType(HostMenu<M> factory) {
		return IMenuTypeExtension.create((id, inventory, data) -> factory.create(id, inventory, com.payonel.modularcomputers.network.HostRef.STREAM_CODEC.decode(data)));
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, com.payonel.modularcomputers.network.HostRef host) {
		player.openMenu(provider, buffer -> com.payonel.modularcomputers.network.HostRef.STREAM_CODEC.encode(buffer, host));
	}

	/** The mod's entity types' attributes, given to NeoForge's event when it comes ({@link ModularComputersNeoForge}). */
	static final java.util.Map<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>,
		java.util.function.Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier>> ATTRIBUTES = new java.util.LinkedHashMap<>();

	@Override
	public void entityAttributes(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type,
		java.util.function.Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier> attributes) {
		ATTRIBUTES.put(type, attributes);
	}

	@Override
	public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level, com.mojang.authlib.GameProfile profile) {
		return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile);
	}
}
