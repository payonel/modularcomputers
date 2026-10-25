package com.payonel.modularcomputers.fabric;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.platform.Platform;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The common code's {@link Platform}, on Fabric. */
public final class FabricPlatform implements Platform {
	@Override
	public String version() {
		return FabricLoader.getInstance().getModContainer(ModularComputers.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public List<String> files(String folder) {
		var root = FabricLoader.getInstance().getModContainer(ModularComputers.MOD_ID).orElseThrow().findPath(folder.substring(1));
		if (root.isEmpty()) {
			return List.of();
		}
		try (var paths = Files.walk(root.get())) {
			return paths.filter(Files::isRegularFile).map(path -> root.get().relativize(path).toString()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	@Override
	public CreativeModeTab.Builder creativeTab(Supplier<List<ItemStack>> items) {
		return FabricCreativeModeTab.builder().displayItems((parameters, output) -> output.acceptAll(items.get()));
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> menuType(BlockMenu<M> factory) {
		return new ExtendedMenuType<>(factory::create, BlockPos.STREAM_CODEC);
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> hostMenuType(HostMenu<M> factory) {
		return new ExtendedMenuType<>(factory::create, com.payonel.modularcomputers.network.HostRef.STREAM_CODEC);
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, com.payonel.modularcomputers.network.HostRef host) {
		player.openMenu(new ExtendedMenuProvider<com.payonel.modularcomputers.network.HostRef>() {
			@Override
			public com.payonel.modularcomputers.network.HostRef getScreenOpeningData(ServerPlayer player) {
				return host;
			}

			@Override
			public Component getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
				return provider.createMenu(id, inventory, player);
			}
		});
	}

	@Override
	public void entityAttributes(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type,
		Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier> attributes) {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(type, attributes.get());
	}

	@Override
	public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level, com.mojang.authlib.GameProfile profile) {
		return net.fabricmc.fabric.api.entity.FakePlayer.get(level, profile);
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, BlockPos pos) {
		player.openMenu(new ExtendedMenuProvider<BlockPos>() {
			@Override
			public BlockPos getScreenOpeningData(ServerPlayer player) {
				return pos;
			}

			@Override
			public Component getDisplayName() {
				return provider.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
				return provider.createMenu(id, inventory, player);
			}
		});
	}
}
