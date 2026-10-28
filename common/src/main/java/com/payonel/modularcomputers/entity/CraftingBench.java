package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.device.CraftingCard;
import com.payonel.modularcomputers.menu.CraftingStorage;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A crafting card's work, in the world: its slots ({@link CraftingStorage}, on the card), the
 * game's crafting recipes, and the robot holding it (its hand, the containers round it, the items
 * lying near it). With no robot (a case's card) there are no robotics to work it.
 */
public final class CraftingBench implements CraftingCard.Bench {
	/** How far round the robot it picks items up, in blocks. */
	private static final double PICKUP_REACH = 1.5;
	/** How far toward a face a dropped item starts, beside the robot (its space there solid), in blocks, and how fast it's tossed. */
	private static final double DROP_AHEAD = 0.6;
	private static final double DROP_SPEED = 0.1;

	private final CraftingStorage slots;
	private final Supplier<BotGolem> robot;
	private final Supplier<ServerLevel> level;

	public CraftingBench(CraftingStorage slots, Supplier<BotGolem> robot, Supplier<ServerLevel> level) {
		this.slots = slots;
		this.robot = robot;
		this.level = level;
	}

	@Override
	public boolean robotics() {
		return robot.get() != null;
	}

	@Override
	public com.payonel.modularcomputers.device.RoboticsDevice robot() {
		BotGolem golem = robot.get();
		return golem == null ? null : golem.robotics();
	}

	@Override
	public Result list() {
		List<Object> list = new ArrayList<>();
		for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
			ItemStack stack = slots.getItem(slot);
			if (!stack.isEmpty()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("slot", slot + 1);
				item.putAll(GolemHands.describe(stack));
				list.add(item);
			}
		}
		return Result.ok(list);
	}

	@Override
	public Result craft() {
		CraftingInput.Positioned positioned = slots.grid().asPositionedCraftInput();
		CraftingInput input = positioned.input();
		if (input.isEmpty()) {
			return Result.fail("empty grid");
		}
		ServerLevel world = level.get();
		Optional<RecipeHolder<CraftingRecipe>> recipe = world.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, world);
		if (recipe.isEmpty()) {
			return Result.fail("no recipe");
		}
		ItemStack result = recipe.get().value().assemble(input);
		if (result.isEmpty() || !result.isItemEnabled(world.enabledFeatures())) {
			return Result.fail("no recipe");
		}
		NonNullList<ItemStack> remaining = recipe.get().value().getRemainingItems(input);
		// Worked out on copies first: the grid as it'll be, and its storage with what it made (and what
		// the grid can't take back: a bucket left where more milk is), all or nothing.
		NonNullList<ItemStack> after = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
		for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
			after.set(slot, slots.getItem(slot).copy());
		}
		List<ItemStack> toStorage = new ArrayList<>();
		toStorage.add(result.copy());
		for (int y = 0; y < input.height(); y++) {
			for (int x = 0; x < input.width(); x++) {
				int slot = x + positioned.left() + (y + positioned.top()) * 3;
				ItemStack here = after.get(slot);
				if (!here.isEmpty()) {
					here.shrink(1);
				}
				ItemStack replacement = remaining.get(x + y * input.width());
				if (replacement.isEmpty()) {
					continue;
				}
				if (here.isEmpty()) {
					after.set(slot, replacement.copy());
				} else if (ItemStack.isSameItemSameComponents(here, replacement) && here.getCount() + replacement.getCount() <= here.getMaxStackSize()) {
					here.grow(replacement.getCount());
				} else {
					toStorage.add(replacement.copy());
				}
			}
		}
		for (ItemStack stack : toStorage) {
			if (!insert(after, stack).isEmpty()) {
				return Result.fail("no room");
			}
		}
		apply(after);
		Map<String, Object> made = GolemHands.describe(result);
		return Result.ok(made);
	}

	/** Puts every slot as {@code after} has it. */
	private void apply(NonNullList<ItemStack> after) {
		for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
			slots.setItem(slot, after.get(slot).isEmpty() ? ItemStack.EMPTY : after.get(slot));
		}
	}

	/** Puts a stack into its storage (not its grid), with what's like it first, then empty slots: what didn't fit. */
	private static ItemStack insert(NonNullList<ItemStack> items, ItemStack stack) {
		for (int slot = CraftingCard.GRID; slot < CraftingCard.SLOTS && !stack.isEmpty(); slot++) {
			ItemStack here = items.get(slot);
			if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, stack)) {
				int moving = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
				here.grow(moving);
				stack.shrink(moving);
			}
		}
		for (int slot = CraftingCard.GRID; slot < CraftingCard.SLOTS && !stack.isEmpty(); slot++) {
			if (items.get(slot).isEmpty()) {
				items.set(slot, stack.copy());
				stack.setCount(0);
			}
		}
		return stack;
	}

	/** Puts a stack into its storage (what the robot broke, or picked up): what didn't fit. */
	public ItemStack storeAway(ItemStack stack) {
		if (!slots.present()) {
			return stack;
		}
		NonNullList<ItemStack> items = NonNullList.withSize(CraftingCard.SLOTS, ItemStack.EMPTY);
		for (int slot = 0; slot < CraftingCard.SLOTS; slot++) {
			items.set(slot, slots.getItem(slot));
		}
		ItemStack left = insert(items, stack.copy());
		for (int slot = CraftingCard.GRID; slot < CraftingCard.SLOTS; slot++) {
			if (items.get(slot) != slots.getItem(slot)) {
				slots.setItem(slot, items.get(slot)); // (a stack put in an empty slot)
			}
		}
		slots.setChanged();
		return left;
	}

	/** Puts a stack into one of its slots (its grid's too), as far as it fits there: what didn't. */
	private ItemStack into(int slot, ItemStack stack) {
		ItemStack here = slots.getItem(slot);
		if (here.isEmpty()) {
			slots.setItem(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
		} else if (ItemStack.isSameItemSameComponents(here, stack)) {
			int moving = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
			here.grow(moving);
			stack.shrink(moving);
			slots.setChanged();
		}
		return stack;
	}

	@Override
	public Result move(int from, int to, int count) {
		ItemStack source = slots.getItem(from - 1);
		ItemStack target = slots.getItem(to - 1);
		if (source.isEmpty() || from == to) {
			return Result.ok(0);
		}
		if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(source, target)) {
			return Result.fail("slot taken: " + to);
		}
		int room = target.isEmpty() ? source.getMaxStackSize() : target.getMaxStackSize() - target.getCount();
		int moving = Math.min(count, Math.min(room, source.getCount()));
		ItemStack moved = source.split(moving);
		if (target.isEmpty()) {
			slots.setItem(to - 1, moved);
		} else {
			target.grow(moved.getCount());
		}
		slots.setChanged();
		return Result.ok(moving);
	}

	@Override
	public Result equip(int slot) {
		BotGolem golem = robot.get();
		ItemStack held = golem.getMainHandItem();
		ItemStack stack = slots.getItem(slot - 1);
		golem.setItemSlot(EquipmentSlot.MAINHAND, stack);
		slots.setItem(slot - 1, held);
		return Result.ok(true);
	}

	@Override
	public Result pickup() {
		BotGolem golem = robot.get();
		int picked = 0;
		for (ItemEntity item : level.get().getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(PICKUP_REACH), ItemEntity::isAlive)) {
			ItemStack stack = item.getItem();
			ItemStack left = storeAway(stack.copy());
			picked += stack.getCount() - left.getCount();
			if (left.isEmpty()) {
				item.discard();
			} else {
				item.setItem(left);
			}
		}
		return Result.ok(picked);
	}

	/** The slots asked for: one ({@code slot}, from 1), or with 0, all its storage's (not its grid's). */
	private static int[] chosen(int slot) {
		return slot == 0 ? java.util.stream.IntStream.range(CraftingCard.GRID, CraftingCard.SLOTS).toArray() : new int[] {slot - 1};
	}

	@Override
	public Result drop(String face, int slot, int count) {
		BotGolem golem = robot.get();
		Container container = golem.hands().container(face);
		Direction toward = golem.hands().direction(face);
		int left = count;
		int dropped = 0;
		for (int each : chosen(slot)) {
			ItemStack stack = slots.getItem(each);
			if (stack.isEmpty() || left == 0) {
				continue;
			}
			ItemStack moving = stack.split(Math.min(left, stack.getCount()));
			left -= moving.getCount();
			if (container != null) {
				moving = HopperBlockEntity.addItem(null, container, moving, toward.getOpposite());
			}
			if (!moving.isEmpty()) {
				dropped += moving.getCount();
				toss(golem, face, toward, moving);
			}
		}
		slots.setChanged();
		if (container != null) {
			container.setChanged();
		}
		return Result.ok(dropped);
	}

	/** Puts a stack into the world at a face: in the space there when it's open, else at the robot's side toward it. */
	private void toss(BotGolem golem, String face, Direction toward, ItemStack stack) {
		net.minecraft.core.BlockPos space = golem.blockPosition().relative(toward);
		Vec3 at;
		if (level.get().getBlockState(space).getCollisionShape(level.get(), space).isEmpty()) {
			at = Vec3.atCenterOf(space);
		} else {
			at = golem.position().add(toward.getStepX() * DROP_AHEAD / 2, golem.getBbHeight() / 2, toward.getStepZ() * DROP_AHEAD / 2);
		}
		ItemEntity item = new ItemEntity(level.get(), at.x, at.y, at.z, stack, toward.getStepX() * DROP_SPEED, 0, toward.getStepZ() * DROP_SPEED);
		item.setDefaultPickUpDelay();
		level.get().addFreshEntity(item);
	}

	@Override
	public Result store(String face, int slot, int count, int to) {
		BotGolem golem = robot.get();
		Container container = golem.hands().container(face);
		if (container == null) {
			return Result.fail("no container there");
		}
		if (to > container.getContainerSize()) {
			return Result.fail("no slot: " + to);
		}
		Direction side = golem.hands().direction(face).getOpposite();
		int left = count;
		int remaining = 0;
		for (int each : chosen(slot)) {
			ItemStack stack = slots.getItem(each);
			if (stack.isEmpty() || left == 0) {
				continue;
			}
			int moving = Math.min(left, stack.getCount());
			left -= moving;
			ItemStack rest = GolemHands.put(container, to, stack.split(moving), side);
			stack.grow(rest.getCount());
			remaining += rest.getCount();
		}
		slots.setChanged();
		container.setChanged();
		return remaining == 0 ? Result.ok(true, 0) : Result.ok(false, remaining);
	}

	@Override
	public Result take(String face, int slot, int count, int to) {
		BotGolem golem = robot.get();
		Container container = golem.hands().container(face);
		if (container == null) {
			return Result.fail("no container there");
		}
		if (slot > container.getContainerSize()) {
			return Result.fail("no slot: " + slot);
		}
		Direction side = golem.hands().direction(face).getOpposite();
		int taken = 0;
		for (int each = slot == 0 ? 0 : slot - 1; each < (slot == 0 ? container.getContainerSize() : slot) && taken < count; each++) {
			ItemStack stack = container.getItem(each);
			if (stack.isEmpty() || !GolemHands.takeable(container, each, side, ItemStack.EMPTY)) {
				continue;
			}
			ItemStack moving = stack.copyWithCount(Math.min(count - taken, stack.getCount()));
			ItemStack left = to == 0 ? storeAway(moving) : into(to - 1, moving);
			int moved = moving.getCount() - left.getCount();
			if (moved > 0) {
				container.removeItem(each, moved);
				taken += moved;
			}
			if (slot == 0 && taken > 0) {
				break; // (the first it could take)
			}
		}
		container.setChanged();
		return Result.ok(taken);
	}
}
