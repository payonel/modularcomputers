package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The server game tests, for each loader's test mod to register: a function for each
 * {@link GameTest} method (the game's test functions are a registry it fills at start), and a
 * test instance running it (the game's tests are data, in a registry each world loads).
 */
public final class GameTests {
	/** Every class with game tests. */
	public static final List<Class<?>> CLASSES = List.of(ModularComputersGameTest.class, AllInOneGameTest.class, CableGameTest.class,
		CardHolderGameTest.class, BluetoothGameTest.class, BatteryGameTest.class, CaseTierGameTest.class, DiskGameTest.class,
		RoboticPistonGameTest.class, CardGameTest.class, RecipeGameTest.class, KeepPartsGameTest.class, PhotomaskGameTest.class,
		AncientLabGameTest.class);

	/** Ticks a test may take unless it says otherwise (the game's usual). */
	public static final int MAX_TICKS = 20;

	/** Where each test runs: an empty 8x8x8 space (scripts/data/empty_test_structure.py). */
	public static final Identifier STRUCTURE = ModularComputers.id("gametest/empty");

	/** The environment the tests share: the world as it is (none of its settings changed). */
	public static final Identifier ENVIRONMENT = ModularComputers.id("default");

	/** A test: its name, what it runs, and how long it may take. */
	public record Test(Identifier name, Consumer<GameTestHelper> function, int maxTicks) {
		public ResourceKey<Consumer<GameTestHelper>> functionKey() {
			return ResourceKey.create(Registries.TEST_FUNCTION, name);
		}

		/** The test instance that runs it, in that environment. */
		public GameTestInstance instance(Holder<TestEnvironmentDefinition<?>> environment) {
			return new FunctionGameTestInstance(functionKey(), new TestData<>(environment, Level.OVERWORLD, STRUCTURE, maxTicks, 0, true,
				Rotation.NONE, false, 1, 1, false, 0));
		}
	}

	/**
	 * What the loader's test mod does to a stand-in player's connection so packets reach it as a
	 * real client's would (NeoForge refuses to send to a connection that never negotiated channels).
	 */
	public static volatile Consumer<ServerPlayer> mockConnection = player -> {
	};

	private GameTests() {
	}

	/** A stand-in server player in the test's level, whose client takes every packet (menus open for it). */
	public static ServerPlayer serverPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		mockConnection.accept(player);
		return player;
	}

	/** Every test, named {@code modularcomputers:<class>/<method>} (lowercased: names allow no capitals). */
	public static List<Test> all() {
		List<Test> tests = new ArrayList<>();
		for (Class<?> type : CLASSES) {
			Object instance = newInstance(type);
			for (Method method : type.getMethods()) {
				GameTest test = method.getAnnotation(GameTest.class);
				if (test == null) {
					continue;
				}
				Object target = Modifier.isStatic(method.getModifiers()) ? null : instance;
				Identifier name = ModularComputers.id((type.getSimpleName() + "/" + method.getName()).toLowerCase(Locale.ROOT));
				tests.add(new Test(name, helper -> invoke(method, target, helper), test.maxTicks()));
			}
		}
		return tests;
	}

	private static Object newInstance(Class<?> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("game test class " + type.getName() + " needs a public no-argument constructor", e);
		}
	}

	private static void invoke(Method method, Object target, GameTestHelper helper) {
		try {
			method.invoke(target, helper);
		} catch (InvocationTargetException e) {
			// The test's own failure (an assertion, an exception), as the game expects it.
			if (e.getCause() instanceof RuntimeException failure) {
				throw failure;
			} else if (e.getCause() instanceof Error error) {
				throw error;
			}
			throw new RuntimeException(e.getCause());
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
}
