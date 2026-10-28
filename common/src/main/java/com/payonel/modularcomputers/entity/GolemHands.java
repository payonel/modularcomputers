package com.payonel.modularcomputers.entity;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.mixin.AttackStrength;
import com.payonel.modularcomputers.platform.Platform;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A golem's senses and its hand, in the world: what's in the six spaces round it, and what it does
 * with what it holds (its main hand), as a player would, through a fake player standing where it
 * is, looking the way it works ({@link Platform#fakePlayer}): the game's own interactions, so a
 * door, a button, a bucket or a block placed behave as a player's. Breaking a block takes a
 * player's time with the tool held, and what it drops goes to the golem's storage, else stays in
 * the world.
 */
final class GolemHands {
	/** Destroy progress a block needs, as a player's (the game's 1.0). */
	private static final float BROKEN = 1;
	/** The stages of a block's cracks, as the game draws them (0 to 9; -1 none). */
	private static final int CRACK_STAGES = 10;
	private static final int NO_CRACKS = -1;

	private final BotGolem golem;
	/** The block being broken (null: none), from which face, and how far along (to {@link #BROKEN}). */
	private BlockPos breaking;
	private String breakingFace;
	private BlockState breakingState;
	private ItemStack breakingWith = ItemStack.EMPTY;
	private float progress;
	/** The game time of its last swing at a mob: how strong the next is. */
	private long lastAttack = Long.MIN_VALUE / 2;

	GolemHands(BotGolem golem) {
		this.golem = golem;
	}

	/** The world direction of a face in its frame ({@link RoboticsDevice#FACES}): its left and right its own. */
	Direction direction(String face) {
		Direction front = golem.getDirection();
		return switch (face) {
			case "front" -> front;
			case "back" -> front.getOpposite();
			case "left" -> front.getCounterClockWise();
			case "right" -> front.getClockWise();
			case "up" -> Direction.UP;
			case "down" -> Direction.DOWN;
			default -> throw new IllegalArgumentException(face);
		};
	}

	/** A world direction's name in its frame (a door's facing, as it sees it). */
	private String face(Direction direction) {
		for (String face : RoboticsDevice.FACES) {
			if (direction(face) == direction) {
				return face;
			}
		}
		throw new IllegalArgumentException(direction.toString());
	}

	private ServerLevel level() {
		return (ServerLevel) golem.level();
	}

	private BlockPos at(String face) {
		return golem.blockPosition().relative(direction(face));
	}

	// ---- senses ----------------------------------------------------------------------------

	Result scan(String face) {
		if (face != null) {
			return Result.ok(space(face));
		}
		Map<String, Object> all = new LinkedHashMap<>();
		for (String each : RoboticsDevice.FACES) {
			all.put(each, space(each));
		}
		return Result.ok(all);
	}

	/** What's in the space at a face: a mob or an item first, then a fluid or a block, or air. */
	private Map<String, Object> space(String face) {
		BlockPos pos = at(face);
		BlockState state = level().getBlockState(pos);
		Map<String, Object> space = new LinkedHashMap<>();
		List<Entity> entities = entitiesAt(pos);
		if (!entities.isEmpty()) {
			space.put("kind", "entity");
			space.put("name", name(entities.getFirst()));
			List<Object> names = new ArrayList<>();
			for (Entity entity : entities) {
				names.add(name(entity));
			}
			space.put("entities", names);
			space.put("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
			return space;
		}
		FluidState fluid = state.getFluidState();
		if (state.isAir()) {
			space.put("kind", "air");
		} else if (!fluid.isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
			space.put("kind", "fluid");
			space.put("name", BuiltInRegistries.FLUID.getKey(fluid.getType()).toString());
			space.put("amount", fluid.getAmount());
			space.put("source", fluid.isSource());
		} else {
			space.put("kind", "block");
			space.put("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
			Map<String, Object> properties = new LinkedHashMap<>();
			for (Property<?> property : state.getProperties()) {
				Object value = state.getValue(property);
				// (a direction as it sees it: it knows no compass)
				properties.put(property.getName(), value instanceof Direction direction ? face(direction) : propertyValue(property, value));
			}
			space.put("state", properties);
			space.put("hardness", (double) state.getDestroySpeed(level(), pos));
			space.put("solid", !state.getCollisionShape(level(), pos).isEmpty()); // (it can't walk through it)
			Container container = HopperBlockEntity.getContainerAt(level(), pos);
			if (container != null) {
				space.put("container", container.getContainerSize());
			}
			if (!fluid.isEmpty()) {
				space.put("fluid", BuiltInRegistries.FLUID.getKey(fluid.getType()).toString()); // (waterlogged)
			}
		}
		return space;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object propertyValue(Property property, Object value) {
		return value instanceof Boolean || value instanceof Integer ? value : property.getName((Comparable) value);
	}

	private static String name(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	/** What's in a space (not the golem, nor what it carries). */
	private List<Entity> entitiesAt(BlockPos pos) {
		return level().getEntities(golem, new AABB(pos), entity -> entity.isAlive() && !entity.isSpectator() && !golem.hasPassenger(entity));
	}

	/** An item, as programs see it: its name, count, wear and label. */
	static Map<String, Object> describe(ItemStack stack) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		item.put("count", stack.getCount());
		if (stack.isDamageableItem()) {
			item.put("damage", stack.getDamageValue());
			item.put("maxDamage", stack.getMaxDamage());
		}
		if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			item.put("label", stack.getHoverName().getString());
		}
		return item;
	}

	Result held(boolean off) {
		ItemStack hand = off ? golem.getOffhandItem() : golem.getMainHandItem();
		return Result.ok(hand.isEmpty() ? null : describe(hand));
	}

	/** Swaps what its hands hold, as a player's swap key does. */
	Result swap() {
		ItemStack main = golem.getMainHandItem();
		golem.setItemSlot(EquipmentSlot.MAINHAND, golem.getOffhandItem());
		golem.setItemSlot(EquipmentSlot.OFFHAND, main);
		return Result.ok(true);
	}

	// ---- the hand ----------------------------------------------------------------------------

	/**
	 * Its hand as a player standing where it is, looking toward {@code face}, holding what it holds
	 * (and nothing else), on the ground when it's on its legs.
	 */
	private ServerPlayer hand(String face) {
		ServerPlayer player = Platform.INSTANCE.fakePlayer(level(), Platform.ROBOT);
		Direction toward = direction(face);
		Vec3 eye = golem.getEyePosition();
		float pitch = toward == Direction.UP ? -90 : toward == Direction.DOWN ? 90 : 0;
		float yaw = toward.getAxis().isHorizontal() ? toward.toYRot() : golem.getYRot();
		player.snapTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
		player.setYHeadRot(yaw);
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, golem.getMainHandItem());
		player.setOnGround(golem.onGround());
		player.setShiftKeyDown(false);
		return player;
	}

	/** Takes back what the hand holds now, and anything else it was given (a filled bucket from a stack), as it catches what it breaks. */
	private void done(ServerPlayer player) {
		golem.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack given = player.getInventory().removeItemNoUpdate(slot);
			if (!given.isEmpty()) {
				golem.keep(given);
			}
		}
	}

	Result use(String face) {
		BlockPos pos = at(face);
		Direction toward = direction(face);
		ServerPlayer player = hand(face);
		// Without legs it has no footing to work a tool: one in hand is used as an empty hand (a
		// button, a door, a lever, its legs popped up), and goes back in its hand after.
		ItemStack tool = !golem.legs() && player.getMainHandItem().isDamageableItem() ? player.getMainHandItem() : null;
		if (tool != null) {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		try {
			// A mob or an item there: used on it, as a player's click on it (shears on a sheep, legs popped up).
			for (Entity entity : entitiesAt(pos)) {
				if (entity instanceof ItemEntity) {
					continue;
				}
				InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND, entity.position());
				if (result.consumesAction()) {
					return Result.ok("used");
				}
			}
			BlockState state = level().getBlockState(pos);
			ItemStack held = player.getMainHandItem();
			if (!state.isAir() && !state.canBeReplaced()) {
				// Something there: clicked on its face toward it (a door opened, a button pressed, a log stripped).
				BlockHitResult hit = new BlockHitResult(faceCenter(pos, toward.getOpposite()), toward.getOpposite(), pos, false);
				InteractionResult result = player.gameMode.useItemOn(player, level(), held, InteractionHand.MAIN_HAND, hit);
				return result.consumesAction() ? Result.ok("used") : Result.fail("nothing happened");
			}
			if (!held.isEmpty()) {
				// An empty space (or one a block replaces: grass, water): placed into it, against a neighbour, as a player places.
				for (Direction against : againstOrder(toward)) {
					BlockPos neighbour = pos.relative(against);
					if (neighbour.equals(golem.blockPosition()) || level().getBlockState(neighbour).canBeReplaced()) {
						continue;
					}
					BlockHitResult hit = new BlockHitResult(faceCenter(neighbour, against.getOpposite()), against.getOpposite(), neighbour, false);
					InteractionResult result = player.gameMode.useItemOn(player, level(), held, InteractionHand.MAIN_HAND, hit);
					if (result.consumesAction()) {
						return Result.ok(level().getBlockState(pos).isAir() || level().getBlockState(pos).canBeReplaced() ? "used" : "placed");
					}
				}
				// Nothing to place against: used in the air, the way it looks (a bucket into water, a pearl thrown).
				InteractionResult result = player.gameMode.useItem(player, level(), held, InteractionHand.MAIN_HAND);
				if (result.consumesAction()) {
					return Result.ok("used");
				}
				return Result.fail("nothing to place against");
			}
			return Result.fail("nothing there");
		} finally {
			if (tool != null) {
				ItemStack given = player.getMainHandItem();
				player.setItemInHand(InteractionHand.MAIN_HAND, tool);
				if (!given.isEmpty()) {
					player.getInventory().add(given); // (what its empty hand was given: kept as the rest)
				}
			}
			done(player);
		}
	}

	/** Where a space's neighbours are tried, to place a block into it against: beyond it, below, its sides, above. */
	private static List<Direction> againstOrder(Direction toward) {
		List<Direction> order = new ArrayList<>();
		order.add(toward);
		order.add(Direction.DOWN);
		for (Direction side : Direction.values()) {
			if (side.getAxis() != toward.getAxis() && side.getAxis().isHorizontal()) {
				order.add(side);
			}
		}
		order.add(Direction.UP);
		order.remove(toward.getOpposite()); // (its own space is behind it)
		return order.stream().distinct().toList();
	}

	private static Vec3 faceCenter(BlockPos pos, Direction side) {
		return Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
	}

	Result swing(String face) {
		if (!golem.legs()) {
			return Result.fail("no legs");
		}
		BlockPos pos = at(face);
		List<Entity> there = entitiesAt(pos).stream().filter(entity -> entity instanceof LivingEntity).toList();
		if (!there.isEmpty()) {
			return attack(face, there.getFirst());
		}
		BlockState state = level().getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
			return Result.fail("nothing there");
		}
		if (state.getDestroySpeed(level(), pos) < 0) {
			return Result.fail("unbreakable");
		}
		stopBreaking();
		breaking = pos.immutable();
		breakingFace = face;
		breakingState = state;
		breakingWith = golem.getMainHandItem();
		progress = 0;
		return Result.ok(RoboticsDevice.BREAKING);
	}

	/** Hits a mob with what it holds, as a player's swing: the weapon's damage, as strong as the time since its last swing allows. */
	private Result attack(String face, Entity target) {
		ServerPlayer player = hand(face);
		Multimap<Holder<Attribute>, AttributeModifier> weapon = HashMultimap.create();
		player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, weapon::put);
		player.getAttributes().addTransientAttributeModifiers(weapon);
		try {
			long now = level().getGameTime();
			((AttackStrength) player).modularcomputers$setAttackStrengthTicker((int) Math.min(Integer.MAX_VALUE, now - lastAttack));
			lastAttack = now;
			player.attack(target);
			return Result.ok("attacked");
		} finally {
			player.getAttributes().removeAttributeModifiers(weapon);
			done(player);
		}
	}

	/** A tick of breaking: the cracks grow at a player's pace with the tool held; broken, what it drops is kept. */
	void tick() {
		if (breaking == null) {
			return;
		}
		BlockState state = level().getBlockState(breaking);
		// Another block there ends it; the same block with its state changed doesn't (leaves losing
		// their log, a crop growing), as a player's breaking goes on.
		if (!state.is(breakingState.getBlock())) {
			fail("the block changed");
			return;
		}
		if (!ItemStack.isSameItem(golem.getMainHandItem(), breakingWith)) {
			fail("what it held changed");
			return;
		}
		ServerPlayer player = hand(breakingFace);
		try {
			progress += state.getDestroyProgress(player, level(), breaking);
			if (progress < BROKEN) {
				level().destroyBlockProgress(golem.getId(), breaking, Math.min(CRACK_STAGES - 1, (int) (progress * CRACK_STAGES)));
				return;
			}
			BlockPos pos = breaking;
			String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			stopBreaking();
			// What it drops is caught as it falls, and kept (the items that weren't there before).
			AABB around = new AABB(pos).inflate(1);
			Set<Integer> before = level().getEntitiesOfClass(ItemEntity.class, around, entity -> true).stream().map(Entity::getId).collect(Collectors.toSet());
			if (!player.gameMode.destroyBlock(pos)) {
				golem.robotics().failed("couldn't break it");
				return;
			}
			level().levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK, pos, net.minecraft.world.level.block.Block.getId(state));
			int dropped = 0;
			for (ItemEntity drop : level().getEntitiesOfClass(ItemEntity.class, around, entity -> !before.contains(entity.getId()))) {
				ItemStack left = golem.catchItem(drop.getItem().copy());
				if (left.isEmpty()) {
					drop.discard();
				} else {
					drop.setItem(left);
					dropped += left.getCount();
				}
			}
			golem.robotics().broke(name, dropped);
		} finally {
			done(player);
		}
	}

	private void fail(String why) {
		stopBreaking();
		golem.robotics().failed(why);
	}

	/** Stops breaking, its cracks gone. */
	void stopBreaking() {
		if (breaking != null) {
			level().destroyBlockProgress(golem.getId(), breaking, NO_CRACKS);
			breaking = null;
		}
	}

	// ---- containers ----------------------------------------------------------------------------

	/** The container at a face (a block's, or a chest minecart's there), or null. */
	Container container(String face) {
		return HopperBlockEntity.getContainerAt(level(), at(face));
	}

	Result contents(String face) {
		Container container = container(face);
		if (container == null) {
			return Result.fail("no container there");
		}
		List<Object> slots = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("slot", slot + 1);
				item.putAll(describe(stack));
				slots.add(item);
			}
		}
		return Result.ok(slots);
	}

	/**
	 * Puts a stack into a container from {@code side}: into its slot {@code to} (from 1) when given, as
	 * far as that slot takes it (what it allows there, from that side), else where it fits, as a
	 * hopper does: what didn't go.
	 */
	static ItemStack put(Container container, int to, ItemStack stack, Direction side) {
		if (to == 0) {
			return HopperBlockEntity.addItem(null, container, stack, side);
		}
		int slot = to - 1;
		if (slot >= container.getContainerSize() || !container.canPlaceItem(slot, stack) || container instanceof WorldlyContainer worldly
			&& (!worldly.canPlaceItemThroughFace(slot, stack, side) || java.util.Arrays.stream(worldly.getSlotsForFace(side)).noneMatch(each -> each == slot))) {
			return stack;
		}
		ItemStack here = container.getItem(slot);
		int most = Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize());
		if (here.isEmpty()) {
			int moving = Math.min(most, stack.getCount());
			container.setItem(slot, stack.split(moving));
		} else if (ItemStack.isSameItemSameComponents(here, stack)) {
			int moving = Math.min(most - here.getCount(), stack.getCount());
			if (moving > 0) {
				here.grow(moving);
				stack.shrink(moving);
			}
		}
		return stack;
	}

	Result store(String face, int count, int to) {
		Container container = container(face);
		if (container == null) {
			return Result.fail("no container there");
		}
		ItemStack hand = golem.getMainHandItem();
		if (hand.isEmpty()) {
			return Result.ok(true, 0); // (nothing left to store)
		}
		int moving = Math.min(count, hand.getCount());
		if (to > container.getContainerSize()) {
			return Result.fail("no slot " + to + " (it has " + container.getContainerSize() + ")");
		}
		ItemStack left = put(container, to, hand.split(moving), direction(face).getOpposite());
		hand.grow(left.getCount());
		golem.setItemSlot(EquipmentSlot.MAINHAND, hand);
		container.setChanged();
		// (one answer says it all: true when none of it is left, else false and how many are)
		return left.isEmpty() ? Result.ok(true, 0) : Result.ok(false, left.getCount());
	}

	Result take(String face, int slot, int count) {
		Container container = container(face);
		if (container == null) {
			return Result.fail("no container there");
		}
		if (slot > container.getContainerSize()) {
			return Result.fail("no slot " + slot + " (it has " + container.getContainerSize() + ")");
		}
		Direction side = direction(face).getOpposite();
		ItemStack hand = golem.getMainHandItem();
		int from = slot - 1;
		if (slot == 0) {
			from = -1;
			for (int each = 0; each < container.getContainerSize() && from < 0; each++) {
				if (takeable(container, each, side, hand)) {
					from = each;
				}
			}
			if (from < 0) {
				return Result.ok(0);
			}
		} else if (!takeable(container, from, side, hand)) {
			return Result.ok(0);
		}
		ItemStack stack = container.getItem(from);
		int room = hand.isEmpty() ? stack.getMaxStackSize() : hand.getMaxStackSize() - hand.getCount();
		int moving = Math.min(count, Math.min(room, stack.getCount()));
		ItemStack taken = container.removeItem(from, moving);
		if (hand.isEmpty()) {
			hand = taken;
		} else {
			hand.grow(taken.getCount());
		}
		golem.setItemSlot(EquipmentSlot.MAINHAND, hand);
		container.setChanged();
		return Result.ok(taken.getCount());
	}

	/** Whether the hand can take from a slot: something there, the same as it holds (or it holds nothing), and the container lets it out that side. */
	static boolean takeable(Container container, int slot, Direction side, ItemStack hand) {
		ItemStack stack = container.getItem(slot);
		if (stack.isEmpty() || !hand.isEmpty() && (!ItemStack.isSameItemSameComponents(stack, hand) || hand.getCount() >= hand.getMaxStackSize())) {
			return false;
		}
		return !(container instanceof WorldlyContainer worldly) || worldly.canTakeItemThroughFace(slot, stack, side)
			&& java.util.Arrays.stream(worldly.getSlotsForFace(side)).anyMatch(each -> each == slot);
	}
}
