package com.payonel.modularcomputers.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * The unit tests' {@link Platform}: no loader, the mod's resources plain folders on the class
 * path. Only what plain-Java tests reach is here.
 */
public final class TestPlatform implements Platform {
	@Override
	public String version() {
		return "test";
	}

	@Override
	public Path configDir() {
		throw new UnsupportedOperationException("no config folder in unit tests");
	}

	@Override
	public List<String> files(String folder) {
		URL url = TestPlatform.class.getResource(folder);
		if (url == null) {
			return List.of();
		}
		try (var paths = Files.walk(Path.of(url.toURI()))) {
			Path root = Path.of(url.toURI());
			return paths.filter(Files::isRegularFile).map(path -> root.relativize(path).toString()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public boolean canReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return false;
	}

	@Override
	public CreativeModeTab.Builder creativeTab(Supplier<List<ItemStack>> items) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> menuType(BlockMenu<M> factory) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, BlockPos pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> hostMenuType(HostMenu<M> factory) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void openMenu(Player player, MenuProvider provider, com.payonel.modularcomputers.network.HostRef host) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void entityAttributes(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type,
		java.util.function.Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier> attributes) {
	}

	@Override
	public net.minecraft.server.level.ServerPlayer fakePlayer(net.minecraft.server.level.ServerLevel level, com.mojang.authlib.GameProfile profile) {
		throw new UnsupportedOperationException("no world in unit tests");
	}
}
