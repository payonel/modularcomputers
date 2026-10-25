package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.block.BoardHolder;
import com.payonel.modularcomputers.block.PowerLight;
import com.payonel.modularcomputers.board.BoardAssembly;
import com.payonel.modularcomputers.board.BoardHost;
import com.payonel.modularcomputers.device.RoboticsDevice;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.GolemLayout;
import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.network.HostRef;
import com.payonel.modularcomputers.network.ModNetwork;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import com.payonel.modularcomputers.network.ScreenRowsPayload;
import com.payonel.modularcomputers.platform.Platform;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The bot golem (user design, 2026-10-24): a robot a block in size, an iron golem's look, walking
 * as fast as a player walks. It holds a computer's board ({@link BoardAssembly}, as a case does: its
 * parts not soldered, in {@link GolemLayout}'s slots), its face the tier 1 screen in its gold slot,
 * and its robotics device soldered on: the only way it moves. It never acts on its own (no goals:
 * it doesn't wander, look about or flee). A player's hit picks it up: it drops as its item, with its
 * parts; nothing else harms it.
 */
public final class BotGolem extends PathfinderMob implements BoardHolder, MenuProvider {
	/** A player's movement speed (its attribute's base), and how hard its walking key pushes (the game's 0.98). */
	private static final double PLAYER_SPEED = 0.1;
	private static final double PLAYER_INPUT = 0.98;
	/**
	 * Its movement speed, for a player's walking pace: a mob's speed is both its push and its speed
	 * (Mob.setSpeed sets its forward input to it too), so it moves as a player at its square.
	 */
	public static final double WALKING_SPEED = Math.sqrt(PLAYER_SPEED * PLAYER_INPUT);
	/** How far its pathfinding looks, in blocks: a walk's farthest, and some (the way may wind). */
	public static final double FOLLOW_RANGE = RoboticsDevice.MAX_WALK * 1.5;
	/** How far it turns a tick, in degrees: a right angle in five ticks. */
	public static final float TURN_SPEED = 18;
	/** How near the point a walk ends counts as there, in blocks (a path ends at a block's center). */
	private static final double ARRIVED = 1.0;
	/** How far its parts view reaches, in blocks (a container's). */
	private static final double PARTS_REACH = 4.0;

	private static final EntityDataAccessor<Boolean> READY = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> HALTED = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> SCREEN = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	/** It stands on its legs (else it's its torso alone: detached, flying in its field, or fallen). */
	private static final EntityDataAccessor<Boolean> LEGS = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	/** Its legs' height, in blocks: three pixels (its model's); without them it's that much shorter. */
	public static final float LEG_HEIGHT = 3 / 16f;
	/** How far from its legs it can attach them, in blocks (their middles). */
	public static final double LEGS_REACH = 3;
	/** How fast it goes onto its legs, in blocks a tick. */
	private static final double ATTACH_SPEED = 0.15;
	/** How near its legs counts as on them, in blocks. */
	private static final double ON_LEGS = 0.05;
	private static final String LEGS_SAVED = "legs";

	private final BoardAssembly board;
	private final RoboticsDevice robotics = new RoboticsDevice();
	/** Its senses and its hand, in the world. */
	private final GolemHands hands = new GolemHands(this);
	/** Where a walk is going (null: none). */
	private Vec3 target;
	/** The heading a turn is going to, in degrees (NaN: none). */
	private float turnTo = Float.NaN;
	/** Where it was last tick, while walking: what it walked since. */
	private Vec3 walkedFrom;
	/** Its walk's way is found and under way (it's planned at a tick it stands on something). */
	private boolean pathed;
	/** Its board has been set up in its world (at its first tick there: the plain game has no hook for an entity's arrival). */
	private boolean placed;
	/** The legs it's going onto (null: none). */
	private GolemLegs attaching;

	public BotGolem(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		board = new BoardAssembly(new GolemHost(), GolemLayout.BOT_GOLEM, TerminalTier.TIER_1);
		robotics.moveWith(new Legs());
		board.solder(Motherboard.exp(GolemLayout.ROBOTICS_LANE), robotics);
		setPersistenceRequired();
	}

	/** Its attributes: a player's walking speed, a pathfinding range for its longest walk. */
	public static AttributeSupplier attributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MOVEMENT_SPEED, WALKING_SPEED)
			.add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
			.build();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(READY, false).define(POWERED, false).define(HALTED, false).define(SCREEN, false).define(LEGS, true);
	}

	/** What the board needs from the golem: where it is, which way it faces, and its entity data. */
	private final class GolemHost implements BoardHost {
		@Override
		public Level level() {
			return BotGolem.this.level();
		}

		@Override
		public BlockPos pos() {
			return blockPosition();
		}

		@Override
		public Direction front() {
			return getDirection();
		}

		/** (An entity is saved with its chunk, whatever changed.) */
		@Override
		public void markChanged() {
		}

		@Override
		public void setChanged() {
		}

		/** Its board's state, to the players who have it near (its parts view's bus panel, its screen). */
		@Override
		public void syncToClients() {
			if (level() instanceof ServerLevel server) {
				var payload = com.payonel.modularcomputers.network.BoardStatePayload.of(HostRef.of(BotGolem.this), board, server.registryAccess());
				for (ServerPlayer player : server.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(blockPosition()), false)) {
					ModNetwork.send(player, payload);
				}
			}
		}

		@Override
		public boolean showPowerState(boolean ready, boolean powered, boolean halted, boolean screen) {
			if (level().isClientSide()) {
				return false;
			}
			boolean changed = entityData.get(READY) != ready || entityData.get(POWERED) != powered
				|| entityData.get(HALTED) != halted || entityData.get(SCREEN) != screen;
			entityData.set(READY, ready);
			entityData.set(POWERED, powered);
			entityData.set(HALTED, halted);
			entityData.set(SCREEN, screen);
			if (changed) {
				syncToClients(); // (its board's state with it, as a case's block update carries its block entity)
			}
			return changed;
		}

		/** Its screen's changed rows, to the players who have it near. */
		@Override
		public void syncDisplay(Terminal display) {
			if (level() instanceof ServerLevel server) {
				ScreenRowsPayload payload = ScreenRowsPayload.changes(HostRef.of(BotGolem.this), display);
				if (payload != null) {
					for (ServerPlayer player : server.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(blockPosition()), false)) {
						ModNetwork.send(player, payload);
					}
				}
			}
		}

		@Override
		public void redstoneChanged() {
			level().updateNeighborsAt(blockPosition(), level().getBlockState(blockPosition()).getBlock());
		}

		@Override
		public void bluetoothChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return isAlive() && player.isWithinEntityInteractionRange(BotGolem.this, PARTS_REACH);
		}

		@Override
		public BotGolem robot() {
			return BotGolem.this;
		}

		@Override
		public String describe() {
			return "bot golem " + board.boardId() + " at " + blockPosition().toShortString() + " in " + level().dimension().identifier();
		}
	}

	/**
	 * Its legs, as its robotics device drives them: a walk is a path to the point, in its own frame
	 * (forward its heading, right to its right), at a player's walking speed; a turn, its heading
	 * changed a step a tick.
	 */
	private final class Legs implements RoboticsDevice.Body {
		@Override
		public String walk(double forward, double right, double up) {
			if (!legs()) {
				return "no legs";
			}
			if (EnergyField.held(BotGolem.this)) {
				return "held in a field: its field moves it";
			}
			float yaw = getYRot() * Mth.DEG_TO_RAD;
			// Minecraft's heading: 0 faces south (+Z), 90 west; to its right is a quarter turn more.
			double x = -Mth.sin(yaw) * forward - Mth.cos(yaw) * right;
			double z = Mth.cos(yaw) * forward - Mth.sin(yaw) * right;
			turnTo = Float.NaN;
			getNavigation().stop();
			hands.stopBreaking();
			target = position().add(x, up, z);
			walkedFrom = position();
			pathed = false; // (its tick finds the way, once it stands: the game plans no path for a mob in the air)
			return null;
		}

		@Override
		public String turn(double degrees) {
			getNavigation().stop();
			hands.stopBreaking();
			target = null;
			turnTo = Mth.wrapDegrees(getYRot() + (float) degrees);
			return null;
		}

		@Override
		public void stop() {
			getNavigation().stop();
			hands.stopBreaking();
			target = null;
			turnTo = Float.NaN;
		}

		@Override
		public Result scan(String face) {
			return hands.scan(face);
		}

		@Override
		public boolean legs() {
			return BotGolem.this.legs();
		}

		@Override
		public Result detachLegs() {
			if (!(level() instanceof ServerLevel server)) {
				return Result.fail("not in a world");
			}
			GolemLegs legs = ModEntities.GOLEM_LEGS.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			legs.snapTo(getX(), getY(), getZ(), getYRot(), 0);
			BotGolem.this.legs(false);
			if (EnergyField.held(BotGolem.this)) {
				setPos(getX(), getY() + LEG_HEIGHT, getZ()); // (its torso where it was, its legs below it)
				EnergyField.fieldOf(BotGolem.this).shift(new Vec3(0, LEG_HEIGHT, 0)); // (held there)
			} else if (machine().isPowered()) {
				machine().pressPower(); // nothing holding it up: it falls over, and powers off
			}
			server.addFreshEntity(legs);
			return Result.ok(true);
		}

		@Override
		public Result attachLegs() {
			GolemLegs near = level().getEntitiesOfClass(GolemLegs.class, getBoundingBox().inflate(LEGS_REACH), GolemLegs::isAlive).stream()
				.filter(legs -> legs.position().distanceTo(position()) <= LEGS_REACH)
				.min(java.util.Comparator.comparingDouble(legs -> legs.position().distanceTo(position()))).orElse(null);
			if (near == null) {
				return Result.fail("no legs within " + (int) LEGS_REACH + " blocks");
			}
			EnergyField field = EnergyField.fieldOf(BotGolem.this);
			if (field != null) {
				field.lose("landing on its legs"); // (it goes onto them itself)
			}
			attaching = near;
			setNoGravity(true);
			return Result.ok(true);
		}

		@Override
		public Result held(boolean off) {
			return hands.held(off);
		}

		@Override
		public Result swap() {
			return hands.swap();
		}

		@Override
		public Result use(String face) {
			return hands.use(face);
		}

		@Override
		public Result swing(String face) {
			getNavigation().stop();
			target = null;
			turnTo = Float.NaN;
			return hands.swing(face);
		}

		@Override
		public Result contents(String face) {
			return hands.contents(face);
		}

		@Override
		public Result store(String face, int count, int to) {
			return hands.store(face, count, to);
		}

		@Override
		public Result take(String face, int slot, int count) {
			return hands.take(face, slot, count);
		}
	}

	/**
	 * Takes what it came by (a broken block's drops, what its hand was given: a filled bucket):
	 * into its storage first (a crafting card's), else its off hand catches it (in the background:
	 * empty, or holding the same), else it stays in the world: what didn't go (in the world).
	 */
	ItemStack catchItem(ItemStack stack) {
		ItemStack left = store(stack);
		if (left.isEmpty()) {
			return left;
		}
		ItemStack off = getOffhandItem();
		if (off.isEmpty()) {
			setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, left.split(Math.min(left.getCount(), left.getMaxStackSize())));
		} else if (ItemStack.isSameItemSameComponents(off, left)) {
			int moving = Math.min(left.getCount(), off.getMaxStackSize() - off.getCount());
			off.grow(moving);
			left.shrink(moving);
		}
		return left;
	}

	/** Takes what its hand was given, as {@link #catchItem}; what it can't hold is dropped at its feet. */
	void keep(ItemStack stack) {
		ItemStack left = catchItem(stack);
		if (!left.isEmpty() && level() instanceof ServerLevel server) {
			spawnAtLocation(server, left);
		}
	}

	/** Puts items into its storage (its crafting cards', in their order): what didn't fit. */
	ItemStack store(ItemStack stack) {
		for (com.payonel.modularcomputers.device.Card card : board.cards()) {
			if (!stack.isEmpty() && card instanceof com.payonel.modularcomputers.device.CraftingCard crafting
				&& crafting.bench() instanceof CraftingBench bench) {
				stack = bench.storeAway(stack);
			}
		}
		return stack;
	}

	/** Whether a crafting card is in it (its parts as the server sent them, on a client): a chest on its back. */
	public boolean hasCraftingCard() {
		for (int lane = 0; lane < CaseMenu.LANES; lane++) {
			if (ModItems.cardKind(board.parts().getItem(CaseMenu.EXP_SLOTS + lane)).orElse(null) == com.payonel.modularcomputers.device.CardKind.CRAFTING) {
				return true;
			}
		}
		return false;
	}

	/** Its senses and hand. */
	GolemHands hands() {
		return hands;
	}

	/** It does nothing on its own: no goals. */
	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	/*
	 * On its legs it holds its ground (the user's): nothing shoves it, not a player walking into it,
	 * a mob, a blast's knockback; in the air it's pushed as anything is.
	 */
	/** It's solid, as a boat is: a player walks into it, not through it (the user's), and can stand on it. */
	@Override
	public boolean canBeCollidedWith(net.minecraft.world.entity.Entity other) {
		return isAlive();
	}

	@Override
	public boolean isPushable() {
		return !onGround() && super.isPushable();
	}

	@Override
	public void push(net.minecraft.world.entity.Entity other) {
		if (!onGround()) {
			super.push(other);
		}
	}

	@Override
	public void push(double x, double y, double z) {
		if (!onGround()) {
			super.push(x, y, z);
		}
	}

	@Override
	public void knockback(double strength, double x, double z, DamageSource source, float amount, boolean onGround) {
		if (!onGround()) {
			super.knockback(strength, x, z, source, amount, onGround);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel) {
			if (!placed) {
				placed = true;
				board.placed();
			}
			move();
			attach();
			hands.tick();
			board.tick();
		}
	}

	/** Going onto its legs, a tick of it: straight to them, and on, its legs gone into it. */
	private void attach() {
		if (attaching == null) {
			return;
		}
		if (!attaching.isAlive()) {
			attaching = null;
			setNoGravity(false);
			robotics.failed("its legs are gone");
			return;
		}
		Vec3 to = attaching.position();
		Vec3 left = to.subtract(position());
		if (left.length() > ON_LEGS) {
			Vec3 step = left.scale(Math.min(1, ATTACH_SPEED / left.length()));
			setPos(position().add(step));
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		face(attaching.getYRot());
		setPos(to);
		attaching.discard();
		attaching = null;
		setNoGravity(false);
		legs(true);
		robotics.attached();
	}

	/** Whether it stands on its legs. */
	public boolean legs() {
		return entityData.get(LEGS);
	}

	/** Puts its legs on, or takes them off (it's its torso alone then: shorter). */
	public void legs(boolean on) {
		entityData.set(LEGS, on);
		refreshDimensions();
	}

	/** Without legs it's shorter by them. */
	@Override
	protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		var size = super.getDefaultDimensions(pose);
		return entityData.get(LEGS) ? size : size.scale(1, (size.height() - LEG_HEIGHT) / size.height());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (LEGS.equals(accessor)) {
			refreshDimensions();
		}
	}

	/** Without legs it presses no pressure plates and trips no tripwires (nothing stands on them). */
	@Override
	public boolean isIgnoringBlockTriggers() {
		return !legs();
	}

	/** Its walk or turn under way, a tick of it: what it walked paid, and how it ended told. */
	private void move() {
		if (target != null && !pathed) {
			if (!onGround()) {
				return; // (in the air: it walks once it lands)
			}
			Path path = getNavigation().createPath(target.x, target.y, target.z, 0);
			if (path == null) {
				target = null;
				robotics.blocked("no way there");
				return;
			}
			getNavigation().moveTo(path, 1.0);
			pathed = true;
		}
		if (target != null) {
			robotics.walked(position().distanceTo(walkedFrom));
			walkedFrom = position();
			if (getNavigation().isDone()) {
				boolean there = position().subtract(target).horizontalDistance() < ARRIVED;
				target = null;
				if (there) {
					robotics.arrived();
				} else {
					robotics.blocked("no way there");
				}
			}
		}
		if (!Float.isNaN(turnTo)) {
			float left = Mth.wrapDegrees(turnTo - getYRot());
			float step = Mth.clamp(left, -TURN_SPEED, TURN_SPEED);
			face(getYRot() + step);
			if (Math.abs(left) <= TURN_SPEED) {
				face(turnTo);
				turnTo = Float.NaN;
				robotics.turned();
			}
		}
	}

	/** Faces {@code yaw}, all of it: its body, its head. */
	private void face(float yaw) {
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	@Override
	public BoardAssembly board() {
		return board;
	}

	/** Its robotics device. */
	public RoboticsDevice robotics() {
		return robotics;
	}

	public Machine machine() {
		return board.machine();
	}

	@Override
	public Terminal terminal() {
		return board.terminal();
	}

	/** Its face shows a screen: one in its gold slot (as its entity data says, on clients too). */
	@Override
	public boolean showsScreen() {
		return entityData.get(SCREEN);
	}

	@Override
	public PowerLight powerLight() {
		return new PowerLight(entityData.get(READY), entityData.get(POWERED), entityData.get(HALTED));
	}

	@Override
	public OpenDisplayPayload openDisplayPayload() {
		HostRef here = HostRef.of(this);
		return new OpenDisplayPayload(here, terminal().buffer().resolution(), Optional.of(here), true);
	}

	@Override
	public void openParts(ServerPlayer player) {
		Platform.INSTANCE.openMenu(player, this, HostRef.of(this));
	}

	@Override
	public Optional<Machine.PowerPress> pressPowerButton() {
		return Optional.of(board.pressPower());
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new CaseMenu(ModMenus.GOLEM, id, inventory, board.parts(), board.powerData(), blockPosition(), HostRef.of(this),
			board.parts()::stillValid, GolemLayout.BOT_GOLEM, new HandSlot());
	}

	/** Its hands as a container of two slots, for its parts view: its main hand and its off hand, read and written there. */
	private final class HandSlot implements net.minecraft.world.Container {
		private static final net.minecraft.world.entity.EquipmentSlot[] HANDS = {net.minecraft.world.entity.EquipmentSlot.MAINHAND,
			net.minecraft.world.entity.EquipmentSlot.OFFHAND};

		@Override
		public int getContainerSize() {
			return HANDS.length;
		}

		@Override
		public boolean isEmpty() {
			return getMainHandItem().isEmpty() && getOffhandItem().isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return getItemBySlot(HANDS[slot]);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack taken = getItem(slot).split(count);
			setChanged();
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack taken = getItem(slot);
			setItemSlot(HANDS[slot], ItemStack.EMPTY);
			return taken;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			setItemSlot(HANDS[slot], stack);
		}

		@Override
		public void setChanged() {
			for (var hand : HANDS) {
				setItemSlot(hand, getItemBySlot(hand)); // (an emptied stack goes)
			}
		}

		@Override
		public boolean stillValid(Player player) {
			return board.parts().stillValid(player);
		}

		@Override
		public void clearContent() {
			setItem(0, ItemStack.EMPTY);
			setItem(1, ItemStack.EMPTY);
		}
	}

	@Override
	public Component getDisplayName() {
		return getName();
	}

	/** Right-click: its screen, when its face has one; else (or crouching) its parts. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			if (!player.isSecondaryUseActive() && showsScreen()) {
				ModNetwork.send(server, openDisplayPayload());
			} else {
				openParts(server);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** A player's hit picks it up (its item, its parts in it); nothing else harms it, but the world's end. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player) {
			spawnAtLocation(level, asItem());
			discard();
			return true;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		return false;
	}

	/** It as its item: its parts and its board's id with it, as a case keeps them when broken. */
	public ItemStack asItem() {
		ItemStack item = new ItemStack(legs() ? ModItems.BOT_GOLEM : ModItems.BOT_GOLEM_TORSO);
		List<ItemStack> parts = new ArrayList<>();
		for (int slot = 0; slot < board.parts().getContainerSize(); slot++) {
			parts.add(board.parts().getItem(slot).copy());
		}
		if (parts.stream().anyMatch(stack -> !stack.isEmpty())) {
			item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
		}
		item.set(ModComponents.SERIAL, board.boardId());
		if (!getMainHandItem().isEmpty()) {
			item.set(ModComponents.HELD, getMainHandItem().copy());
		}
		if (!getOffhandItem().isEmpty()) {
			item.set(ModComponents.OFF_HELD, getOffhandItem().copy());
		}
		if (hasCustomName()) {
			item.set(DataComponents.CUSTOM_NAME, getCustomName());
		}
		return item;
	}

	/** Takes what its item kept: its parts and its board's id. */
	public void fromItem(ItemStack item) {
		java.util.UUID id = item.get(ModComponents.SERIAL);
		if (id != null) {
			board.restoreBoardId(id);
		}
		NonNullList<ItemStack> parts = NonNullList.withSize(CaseMenu.PART_SLOTS, ItemStack.EMPTY);
		item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(parts);
		board.restoreParts(parts);
		setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, item.getOrDefault(ModComponents.HELD, ItemStack.EMPTY).copy());
		setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, item.getOrDefault(ModComponents.OFF_HELD, ItemStack.EMPTY).copy());
		if (item.has(DataComponents.CUSTOM_NAME)) {
			setCustomName(item.get(DataComponents.CUSTOM_NAME));
		}
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (reason.shouldDestroy()) {
			board.removed(); // picked up, or gone: a power cut (an unload only freezes it)
		}
		board.unloaded();
	}

	/** A player who starts seeing it gets its board's state and its whole screen. */
	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		ModNetwork.send(player, com.payonel.modularcomputers.network.BoardStatePayload.of(HostRef.of(this), board, player.level().registryAccess()));
		ModNetwork.send(player, ScreenRowsPayload.whole(HostRef.of(this), terminal()));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(LEGS_SAVED, legs());
		board.save(output, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		legs(input.getBooleanOr(LEGS_SAVED, true));
		board.load(input);
	}
}
