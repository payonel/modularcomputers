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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	/** It stands on its legs (else it's its torso alone: flying on its thrusters, held in a field, or fallen). */
	private static final EntityDataAccessor<Boolean> LEGS = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	/** Its thrusters fire (flying, hovering or landing): beams from its leg sockets, a roar. */
	private static final EntityDataAccessor<Boolean> THRUSTING = SynchedEntityData.defineId(BotGolem.class, EntityDataSerializers.BOOLEAN);
	/** How fast its thrusters fly it, in blocks a tick: 25 a second (clearly faster than its field's 10; a player sprints 5.6). */
	public static final double THRUST_SPEED = 1.25;
	/** How fast it comes down onto its legs, in blocks a tick. */
	private static final double LANDING_SPEED = 0.25;
	/** How far from its legs it starts slowing, in blocks, and its slowest then (a share of its speed). */
	private static final double LANDING_EASE = 1;
	private static final double LANDING_SLOWEST = 0.1;
	/** How near a flight's point counts as there, in blocks. */
	private static final double FLOWN = 0.05;
	/** A tick's flight that makes less than this share of its step is blocked (something in the way). */
	private static final double BLOCKED_SHARE = 0.1;
	/** How much of a shove's speed it keeps each tick, hovering: it drifts, and stops. */
	private static final double HOVER_DRAG = 0.6;
	/** A drift slower than this, in blocks a tick, is still. */
	private static final double DRIFT = 0.005;
	/** Its leg sockets, each this far to its side of its middle, in blocks: where its legs' tops are (BotGolemModel's, 2 pixels), and its beams come from. */
	public static final float SOCKET_SIDE = 2 / 16f;
	/** What a tier 1 golem says to a flight on thrusters: it has none (it walks; a field can still carry it). */
	public static final String NO_THRUSTERS = "no thrusters";
	/** Its beams: red, as its sockets' redstone (scripts/art/bot_golem_textures.py's clamps), and how many motes a socket a tick. */
	private static final int BEAM_COLOR = 0xE8281E;
	private static final float BEAM_SIZE = 0.9f;
	private static final int BEAM_MOTES = 3;
	/** How far under its sockets its sparks start, in blocks (round the beam's middle). */
	private static final double BEAM_SPARKS_DOWN = 0.15;
	private static final String HOVERING_SAVED = "hovering";
	private static final String PATHFINDING_SAVED = "pathfinding";
	private static final String ORIGINS_SAVED = "origins";
	private static final com.mojang.serialization.Codec<Origin> ORIGIN_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(origin -> origin.group(
		Vec3.CODEC.fieldOf("at").forGetter(Origin::at),
		com.mojang.serialization.Codec.FLOAT.fieldOf("yaw").forGetter(Origin::yaw)
	).apply(origin, Origin::new));
	private static final com.mojang.serialization.Codec<Map<String, Origin>> ORIGINS_CODEC = com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, ORIGIN_CODEC);
	/** Its legs' height, in blocks: three pixels (its model's); without them it's that much shorter. */
	public static final float LEG_HEIGHT = 3 / 16f;
	/** How far from its legs it can attach them, in blocks (their middles). */
	public static final double LEGS_REACH = 3;
	/** How near its legs counts as on them, in blocks. */
	private static final double ON_LEGS = 0.05;
	private static final String LEGS_SAVED = "legs";

	private final BoardAssembly board;
	private final RoboticsDevice robotics = new RoboticsDevice();
	/** Its senses and its hand, in the world. */
	private final GolemHands hands = new GolemHands(this);
	/** Where a walk with pathfinding is going (null: none): its block's middle. */
	private Vec3 target;
	/** A walk without pathfinding: the blocks' middles still to step to, in order (null: none). */
	private java.util.ArrayDeque<Vec3> steps;
	/** The step under way (null: none): from one block's middle, on its floor, to the next's, and how far along (0 to 1). */
	private Vec3 stepFrom;
	private Vec3 stepTo;
	private double stepDone;
	/** The elevation its walk is to end at (the block its feet are in). */
	private int goal;
	/** Its heading as its move began: given back as it ends (a pathfinder turns it as it goes). */
	private float heading = Float.NaN;
	/** A flight with pathfinding: its way's points still to fly to, after {@link #flyTo} (null: none). */
	private java.util.ArrayDeque<Vec3> flightPath;
	/** Whether its moves find their way round things (the game's pathfinder) or go straight: off by default, lost when it's picked up. */
	private boolean pathfinding;
	/**
	 * Its origins, a dimension's each (by its id): where its position is measured from there, and the
	 * way its forward is. Kept through reboots and shoves; lost when it's picked up (an item keeps none).
	 */
	private final Map<String, Origin> origins = new HashMap<>();

	/** An origin: a block's middle, and its frame's forward (a yaw, a right angle). */
	record Origin(Vec3 at, float yaw) {
	}
	/** The heading a turn is going to, in degrees (NaN: none). */
	private float turnTo = Float.NaN;
	/** Where it was last tick, while walking: what it walked since. */
	private Vec3 walkedFrom;
	/** Its walk's way is found and under way (it's planned at a tick it stands on something). */
	private boolean pathed;
	/** Its board has been set up in its world (at its first tick there: the plain game has no hook for an entity's arrival). */
	private boolean placed;
	/** The legs it's landing on (null: none). */
	private GolemLegs landing;
	/** Where a flight is going (null: none). */
	private Vec3 flyTo;
	/** Its thrusters hold it up (flying, or still: hovering), until it lands or they're let go. */
	private boolean hovering;
	/** Something held it up last tick (its thrusters, a field): when nothing does, legless, it falls and powers off. */
	private boolean airborne;
	/** Its thrusters' roar, in a client's world (the client's hook: client.sound.ThrusterRoar). */
	public static java.util.function.Consumer<BotGolem> clientRoar = golem -> {
	};

	/** Its tier: its board's slots, and whether it has thrusters. */
	private final GolemLayout tier;

	public BotGolem(EntityType<? extends PathfinderMob> type, Level level, GolemLayout tier) {
		super(type, level);
		this.tier = tier;
		board = new BoardAssembly(new GolemHost(), tier, TerminalTier.TIER_1);
		robotics.moveWith(new Legs());
		board.solder(Motherboard.exp(GolemLayout.ROBOTICS_LANE), robotics);
		setPersistenceRequired();
	}

	/** Its tier: its board's slots, and whether it flies on thrusters (tier 2). */
	public GolemLayout tier() {
		return tier;
	}

	/** Why it can't fly on thrusters (a tier 1 has none), or null. */
	private String noThrusters() {
		return tier.thrusters() ? null : NO_THRUSTERS;
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
		builder.define(READY, false).define(POWERED, false).define(HALTED, false).define(SCREEN, false).define(LEGS, true).define(THRUSTING, false);
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
			begin();
			Vec3 start = middle();
			goal = GolemSteps.elevation(getY()) + (int) up;
			if (pathfinding) {
				target = start.add(inFrame(forward, right, 0)).add(0, up, 0);
				walkedFrom = BotGolem.this.position();
				pathed = false; // (its tick finds the way, once it stands: the game plans no path for a mob in the air)
				return null;
			}
			// Straight: forward, then right, a block's middle each, without turning.
			steps = new java.util.ArrayDeque<>();
			Vec3 at = start;
			Vec3 ahead = inFrame(Math.signum(forward), 0, 0);
			for (int i = 0; i < Math.abs(forward); i++) {
				at = at.add(ahead);
				steps.add(at);
			}
			Vec3 aside = inFrame(0, Math.signum(right), 0);
			for (int i = 0; i < Math.abs(right); i++) {
				at = at.add(aside);
				steps.add(at);
			}
			setNoGravity(true); // (it moves itself, step by step)
			return null;
		}

		@Override
		public String walkTo(double forward, double right, double up) {
			Vec3 to = fromOrigin(forward, right, up);
			Vec3 d = to.subtract(middle());
			double ahead = Math.round(d.dot(inFrame(1, 0, 0)));
			double aside = Math.round(d.dot(inFrame(0, 1, 0)));
			double rise = GolemSteps.elevation(to.y) - GolemSteps.elevation(getY());
			if (Math.abs(ahead) > RoboticsDevice.MAX_WALK || Math.abs(aside) > RoboticsDevice.MAX_WALK || Math.abs(rise) > RoboticsDevice.MAX_WALK) {
				return "too far: at most " + (int) RoboticsDevice.MAX_WALK + " blocks each way";
			}
			return walk(ahead, aside, rise);
		}

		@Override
		public String turn(double degrees) {
			getNavigation().stop();
			hands.stopBreaking();
			target = null;
			turnTo = Mth.wrapDegrees(rightAngle(getYRot()) + (float) degrees);
			return null;
		}

		@Override
		public void stop() {
			getNavigation().stop();
			hands.stopBreaking();
			if (target != null || steps != null) {
				if (stepFrom != null) {
					setPos(stepFrom); // (back onto the last block's middle it reached)
				} else {
					Vec3 middle = middle();
					setPos(middle.x, getY(), middle.z);
				}
				endWalk();
			}
			turnTo = Float.NaN;
			if (flyTo != null) {
				flyTo = null; // (flying or landing: it hovers there)
				flightPath = null;
				restoreHeading();
			}
			landing = null;
		}

		@Override
		public String fly(double forward, double right, double up) {
			if (noThrusters() != null) {
				return noThrusters();
			}
			if (EnergyField.held(BotGolem.this)) {
				return "held in a field: its field moves it";
			}
			begin();
			takeOff();
			Vec3 middle = middle();
			return flyToward(new Vec3(middle.x, getY(), middle.z).add(inFrame(forward, right, up)));
		}

		@Override
		public String flyTo(double forward, double right, double up) {
			if (noThrusters() != null) {
				return noThrusters();
			}
			if (EnergyField.held(BotGolem.this)) {
				return "held in a field: its field moves it";
			}
			Vec3 to = fromOrigin(forward, right, up).add(0, LEG_HEIGHT, 0); // (its torso's height, its feet there)
			if (to.distanceTo(BotGolem.this.position()) > RoboticsDevice.MAX_FLIGHT * Math.sqrt(3)) {
				return "too far: at most " + (int) RoboticsDevice.MAX_FLIGHT + " blocks each way";
			}
			begin();
			takeOff();
			return flyToward(to);
		}

		@Override
		public Result position() {
			return Result.ok(BotGolem.this.positionFromOrigin());
		}

		@Override
		public String setOrigin(long forward, long right, long up, int heading) {
			BotGolem.this.setOrigin(forward, right, up, heading);
			return null;
		}

		@Override
		public boolean pathfinding() {
			return pathfinding;
		}

		@Override
		public void pathfinding(boolean on) {
			pathfinding = on;
		}

		@Override
		public String hover() {
			if (noThrusters() != null) {
				return noThrusters();
			}
			if (EnergyField.held(BotGolem.this)) {
				return "held in a field: its field moves it";
			}
			takeOff();
			flyTo = null;
			return null;
		}

		@Override
		public String land() {
			if (noThrusters() != null) {
				return noThrusters();
			}
			if (legs()) {
				return "already on its legs";
			}
			// Its legs below it: within reach to its side, at its height or under it.
			GolemLegs below = level().getEntitiesOfClass(GolemLegs.class, getBoundingBox().inflate(LEGS_REACH, RoboticsDevice.MAX_FLIGHT, LEGS_REACH), GolemLegs::isAlive)
				.stream().filter(legs -> legs.getY() <= getY() + ON_LEGS && legs.position().subtract(BotGolem.this.position()).horizontalDistance() <= LEGS_REACH)
				.min(java.util.Comparator.comparingDouble(legs -> legs.position().distanceTo(BotGolem.this.position()))).orElse(null);
			if (below == null) {
				return "no legs there to land on";
			}
			EnergyField field = EnergyField.fieldOf(BotGolem.this);
			if (field != null) {
				field.lose("landing on its legs"); // (it goes onto them itself)
			}
			flyTo = null;
			hovering = true; // (its thrusters bring it down)
			landing = below;
			return null;
		}

		@Override
		public void release() {
			flyTo = null;
			landing = null;
			hovering = false;
		}

		@Override
		public boolean hovering() {
			return hovering;
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
			origin(); // (its first tick here, or a dimension it's new to: its origin, where it is)
			move();
			thrust();
			hands.tick();
			board.tick();
		}
	}

	// ---- its position and its moves' frame ---------------------------------------------------------

	/** A heading turned to the nearest right angle. */
	static float rightAngle(float yaw) {
		return Math.round(yaw / 90f) * 90f;
	}

	/** The middle of the block it stands in, at its height. */
	private Vec3 middle() {
		return new Vec3(Math.floor(getX()) + 0.5, getY(), Math.floor(getZ()) + 0.5);
	}

	/** Its feet's height: its torso alone stands its legs' height above them (it took off where they stood). */
	private double feet() {
		return getY() - (legs() ? 0 : LEG_HEIGHT);
	}

	/** This dimension's id: each has its own origin. */
	private String dimension() {
		return level().dimension().identifier().toString();
	}

	/** Its origin here, made now if it has none (its first power-on, or a dimension it's new to): where it stands. */
	private Origin origin() {
		Origin origin = origins.get(dimension());
		if (origin == null) {
			setOrigin(0, 0, 0, 0);
			origin = origins.get(dimension());
		}
		return origin;
	}

	/**
	 * Sets its origin here so that where it is (moved onto its block's middle, turned to the nearest
	 * right angle) is {@code forward}, {@code right}, {@code up} blocks from it, facing
	 * {@code heading} degrees to the origin's right (a right angle): all 0, its origin where it is.
	 */
	public void setOrigin(long forward, long right, long up, int heading) {
		Vec3 middle = middle();
		setPos(middle.x, getY(), middle.z);
		face(rightAngle(getYRot()));
		float originYaw = Mth.positiveModulo(getYRot() - heading, 360f);
		float yaw = originYaw * Mth.DEG_TO_RAD;
		Vec3 offset = new Vec3(-Mth.sin(yaw) * forward - Mth.cos(yaw) * right, up, Mth.cos(yaw) * forward - Mth.sin(yaw) * right);
		origins.put(dimension(), new Origin(new Vec3(middle.x, feet(), middle.z).subtract(offset), originYaw));
	}

	/** Where it is from its origin, in the origin's frame: {forward, right, up, heading} (the robotics device's position). */
	public Map<String, Object> positionFromOrigin() {
		Origin origin = origin();
		Vec3 d = new Vec3(getX(), feet(), getZ()).subtract(origin.at());
		float yaw = origin.yaw() * Mth.DEG_TO_RAD;
		Map<String, Object> position = new LinkedHashMap<>();
		position.put("forward", tidy(d.dot(new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)))));
		position.put("right", tidy(d.dot(new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)))));
		position.put("up", tidy(d.y));
		position.put("heading", tidy(Mth.positiveModulo(getYRot() - origin.yaw(), 360f)));
		return position;
	}

	/** A position's number: whole when it's within a hair of whole (a sine's rounding isn't a program's business). */
	private static Object tidy(double value) {
		double whole = Math.rint(value);
		if (Math.abs(value - whole) < HAIR) {
			return (long) whole;
		}
		return value;
	}

	/** How near a whole number a position's counts as it, in blocks or degrees. */
	private static final double HAIR = 1e-6;

	/** The point {@code forward}, {@code right} and {@code up} blocks from its origin, in the origin's frame. */
	private Vec3 fromOrigin(double forward, double right, double up) {
		Origin origin = origin();
		float yaw = origin.yaw() * Mth.DEG_TO_RAD;
		return origin.at().add(-Mth.sin(yaw) * forward - Mth.cos(yaw) * right, up, Mth.cos(yaw) * forward - Mth.sin(yaw) * right);
	}

	/** A move begins: what it was doing stops, and it faces the nearest right angle (given back as it ends). */
	private void begin() {
		turnTo = Float.NaN;
		getNavigation().stop();
		hands.stopBreaking();
		target = null;
		steps = null;
		stepFrom = stepTo = null;
		face(rightAngle(getYRot()));
		heading = getYRot();
	}

	/** Its heading back as its move began. */
	private void restoreHeading() {
		if (!Float.isNaN(heading)) {
			face(heading);
			heading = Float.NaN;
		}
	}

	/** A walk's over (arrived, blocked or stopped): its heading back, gravity its own. */
	private void endWalk() {
		target = null;
		steps = null;
		stepFrom = stepTo = null;
		setNoGravity(thrusting());
		restoreHeading();
	}

	/** A point {@code forward}, {@code right} and {@code up} blocks from it, in its own frame, as a step from where it is. */
	private Vec3 inFrame(double forward, double right, double up) {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		// Minecraft's heading: 0 faces south (+Z), 90 west; to its right is a quarter turn more.
		double x = -Mth.sin(yaw) * forward - Mth.cos(yaw) * right;
		double z = Mth.cos(yaw) * forward - Mth.sin(yaw) * right;
		if (getYRot() == rightAngle(getYRot())) { // (a right angle: its frame's steps whole, as its walks are)
			x = Math.rint(x * FRAME_GRAIN) / FRAME_GRAIN;
			z = Math.rint(z * FRAME_GRAIN) / FRAME_GRAIN;
		}
		return new Vec3(x, up, z);
	}

	/** How finely a right-angled frame's steps are kept: a sine's last bits dropped. */
	private static final double FRAME_GRAIN = 1e6;

	/** Its thrusters take over: its legs, if it stands on them, left standing where it is, its torso where it was. */
	private void takeOff() {
		getNavigation().stop();
		hands.stopBreaking();
		target = null;
		turnTo = Float.NaN;
		landing = null;
		if (legs() && level() instanceof ServerLevel server) {
			GolemLegs legs = ModEntities.GOLEM_LEGS.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			legs.snapTo(getX(), getY(), getZ(), getYRot(), 0);
			legs(false);
			setPos(getX(), getY() + LEG_HEIGHT, getZ()); // (its torso where it was, its legs below it)
			server.addFreshEntity(legs);
		}
		hovering = true;
		thrusters(true);
	}

	/**
	 * Its thrusters, a tick of them: firing while it flies, hovers or lands (their cost, their beams),
	 * a flight's step (straight, never through blocks), a landing's, or a hover's drift (a shove dies
	 * away). Legless, with nothing holding it up (its thrusters let go, a field's gone), it falls over,
	 * and powers off.
	 */
	private void thrust() {
		boolean firing = hovering;
		thrusters(firing);
		if (firing) {
			robotics.thrusted();
			beams();
			if (landing != null) {
				landStep();
			} else if (flyTo != null) {
				flyStep();
			} else {
				Vec3 drift = getDeltaMovement(); // (a shove's: the game doesn't move a mob held up so)
				if (drift.lengthSqr() > DRIFT * DRIFT) {
					move(net.minecraft.world.entity.MoverType.SELF, drift);
				}
				setDeltaMovement(drift.lengthSqr() > DRIFT * DRIFT ? drift.scale(HOVER_DRAG) : Vec3.ZERO);
			}
		}
		boolean up = entityData.get(THRUSTING) || EnergyField.held(this);
		if (airborne && !up && !legs() && machine().isPowered()) {
			machine().pressPower(); // nothing holding it up: it falls over, and powers off
		}
		airborne = up;
	}

	/** Its thrusters on or off: its synced flag (its beams, its roar), gravity, and their ignition's sound. */
	private void thrusters(boolean firing) {
		if (firing == entityData.get(THRUSTING)) {
			return;
		}
		entityData.set(THRUSTING, firing);
		setNoGravity(firing);
		if (firing) {
			level().playSound(null, getX(), getY(), getZ(), com.payonel.modularcomputers.sound.ModSounds.THRUSTER_ON, net.minecraft.sounds.SoundSource.NEUTRAL, 1, 1);
		}
	}

	/** Starts flying to a point: straight, or with pathfinding by the game's way for fliers (a bee's), round what's in the way; null, or why not. */
	private String flyToward(Vec3 to) {
		flightPath = null;
		if (pathfinding) {
			var navigation = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level());
			Path path = navigation.createPath(BlockPos.containing(to), 0);
			if (path == null || !path.canReach()) {
				return "no way there";
			}
			flightPath = new java.util.ArrayDeque<>();
			for (int i = 1; i < path.getNodeCount(); i++) {
				var node = path.getNode(i);
				flightPath.add(new Vec3(node.x + 0.5, node.y + LEG_HEIGHT, node.z + 0.5));
			}
			flightPath.add(to); // (and its point, exactly)
			flyTo = flightPath.poll();
			return null;
		}
		flyTo = to;
		return null;
	}

	/** A tick's flight toward its point: there, it hovers (arrived); held back, it hovers (blocked). */
	private void flyStep() {
		Vec3 left = flyTo.subtract(position());
		if (left.length() <= FLOWN) {
			setPos(flyTo);
			if (flightPath != null && !flightPath.isEmpty()) {
				flyTo = flightPath.poll(); // (the next of its way's points)
				return;
			}
			flyTo = null;
			flightPath = null;
			restoreHeading();
			robotics.arrived();
			robotics.hovering();
			return;
		}
		Vec3 step = left.length() > THRUST_SPEED ? left.normalize().scale(THRUST_SPEED) : left;
		Vec3 from = position();
		move(net.minecraft.world.entity.MoverType.SELF, step);
		setDeltaMovement(Vec3.ZERO);
		if (position().distanceTo(from) < step.length() * BLOCKED_SHARE) {
			flyTo = null;
			flightPath = null;
			restoreHeading();
			robotics.blocked("something in the way");
			robotics.hovering();
		}
	}

	/** A tick's landing: down to its legs (straight onto them once close: they'd stop a collision), and on, its legs gone into it. */
	private void landStep() {
		if (!landing.isAlive()) {
			landing = null;
			robotics.blocked("its legs are gone");
			robotics.hovering();
			return;
		}
		Vec3 left = landing.position().subtract(position());
		if (left.length() > ON_LEGS) {
			// Over its last block it slows, settling onto its legs (the user's), as a craft easing down.
			double speed = LANDING_SPEED * Math.max(LANDING_SLOWEST, Math.min(1, left.length() / LANDING_EASE));
			Vec3 step = left.scale(Math.min(1, speed / left.length()));
			if (left.length() > LEGS_REACH) {
				Vec3 from = position();
				move(net.minecraft.world.entity.MoverType.SELF, step);
				if (position().distanceTo(from) < step.length() * BLOCKED_SHARE) {
					landing = null;
					robotics.blocked("something in the way");
					robotics.hovering();
				}
			} else {
				setPos(position().add(step));
			}
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		setPos(landing.position()); // (facing as it was: a program's frame stays where it was)
		landing.discard();
		landing = null;
		hovering = false;
		thrusters(false);
		airborne = false;
		legs(true);
		robotics.landed();
	}

	/** Its beams, a tick of them: red motes streaming down from both leg sockets. */
	private void beams() {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		double sideX = -Mth.cos(yaw) * SOCKET_SIDE;
		double sideZ = -Mth.sin(yaw) * SOCKET_SIDE;
		var mote = new net.minecraft.core.particles.DustParticleOptions(BEAM_COLOR, BEAM_SIZE);
		for (int side = -1; side <= 1; side += 2) {
			server.sendParticles(mote, getX() + side * sideX, getY() - BEAM_SPARKS_DOWN, getZ() + side * sideZ, BEAM_MOTES, 0.02, 0.08, 0.02, 0);
		}
	}

	/** Whether its thrusters fire. */
	public boolean thrusting() {
		return entityData.get(THRUSTING);
	}

	/** Whether it's flying: on its thrusters (a field holding it isn't flying). Chickens and cows fear it then. */
	public boolean flying() {
		return thrusting();
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
		if (THRUSTING.equals(accessor) && level().isClientSide() && thrusting()) {
			clientRoar.accept(this); // (its roar, for as long as they fire)
		}
	}

	/** Without legs it presses no pressure plates and trips no tripwires (nothing stands on them). */
	@Override
	public boolean isIgnoringBlockTriggers() {
		return !legs();
	}

	/** Its walk or turn under way, a tick of it: what it walked paid, and how it ended told. */
	private void move() {
		if (steps != null) {
			stepWalk();
		}
		if (target != null && !pathed) {
			if (!onGround()) {
				return; // (in the air: it walks once it lands)
			}
			Path path = getNavigation().createPath(target.x, target.y, target.z, 0);
			if (path == null) {
				endWalk();
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
				Vec3 to = target;
				if (there) {
					setPos(to.x, getY(), to.z); // (onto its block's middle, exactly)
				} else {
					Vec3 middle = middle();
					setPos(middle.x, getY(), middle.z); // (the middle of the last block it reached)
				}
				boolean level = GolemSteps.elevation(getY()) == goal;
				endWalk();
				if (there && level) {
					robotics.arrived();
				} else {
					robotics.blocked(there ? "not at the height asked" : "no way there");
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

	/** How fast it steps, in blocks a tick: a player's walking pace (4.317 blocks a second). */
	private static final double STEP_SPEED = 4.317 / 20;
	/** A jump's share of a step it rises over (then it goes on, up there); a drop's share it goes before it falls. */
	private static final double JUMP_SHARE = 0.4;
	private static final double DROP_SHARE = 0.5;

	/** A walk without pathfinding, a tick of it: the next block's floor judged (GolemSteps), and stepped onto. */
	private void stepWalk() {
		if (stepTo == null) {
			Vec3 next = steps.peek();
			if (next == null) {
				boolean level = GolemSteps.elevation(getY()) == goal;
				endWalk();
				if (level) {
					robotics.arrived();
				} else {
					robotics.blocked("not at the height asked");
				}
				return;
			}
			String why = stepOnto(next);
			if (why != null) {
				endWalk();
				robotics.blocked(why);
				return;
			}
			steps.poll();
		}
		stepDone = Math.min(1, stepDone + STEP_SPEED);
		double rise = stepTo.y - stepFrom.y;
		double y;
		if (rise > 0) {
			y = stepFrom.y + rise * Math.min(1, stepDone / JUMP_SHARE);
		} else if (rise < 0) {
			y = stepFrom.y + (stepDone < DROP_SHARE ? 0 : rise * (stepDone - DROP_SHARE) / (1 - DROP_SHARE));
		} else {
			y = stepFrom.y;
		}
		setPos(Mth.lerp(stepDone, stepFrom.x, stepTo.x), y, Mth.lerp(stepDone, stepFrom.z, stepTo.z));
		setDeltaMovement(Vec3.ZERO);
		calculateEntityAnimation(false); // (its legs swing as it goes)
		if (stepDone >= 1) {
			setPos(stepTo);
			stepFrom = stepTo = null;
			robotics.walked(1);
		}
	}

	/** Starts a step onto the block whose middle is {@code next}: null, or why it can't (the walk's blocked there). */
	private String stepOnto(Vec3 next) {
		double here = getY();
		double floor = floorAt(next.x, next.z, here);
		if (Double.isNaN(floor)) {
			return fits(next.x, here, next.z) ? "too far down" : "a wall";
		}
		BlockPos feet = BlockPos.containing(next.x, floor + HAIR, next.z);
		var state = level().getBlockState(feet);
		if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
			return "lava";
		} else if (state.is(net.minecraft.tags.BlockTags.FIRE)) {
			return "fire";
		} else if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && level().getFluidState(feet.below()).is(net.minecraft.tags.FluidTags.WATER)) {
			return "deep water";
		}
		String why = GolemSteps.step(here, floor, goal);
		if (why != null) {
			return why;
		}
		if (floor > here && !fits(getX(), floor, getZ())) {
			return "no room to jump"; // (a ceiling over it)
		}
		stepFrom = position();
		stepTo = new Vec3(next.x, floor, next.z);
		stepDone = 0;
		return null;
	}

	/**
	 * Where its feet would stand at the block whose middle is {@code x}, {@code z}: the highest floor
	 * there, from a jump above {@code feet} to a drop below, its body fitting on it; NaN for none.
	 */
	private double floorAt(double x, double z, double feet) {
		int top = Mth.floor(feet + GolemSteps.MAX_JUMP);
		int bottom = Mth.floor(feet) - GolemSteps.MAX_DROP - 1;
		for (int y = top; y >= bottom; y--) {
			BlockPos at = BlockPos.containing(x, y, z);
			var shape = level().getBlockState(at).getCollisionShape(level(), at, net.minecraft.world.phys.shapes.CollisionContext.of(this));
			if (shape.isEmpty()) {
				continue;
			}
			double surface = y + shape.max(net.minecraft.core.Direction.Axis.Y);
			if (surface > feet + GolemSteps.MAX_JUMP + HAIR || surface < feet - GolemSteps.MAX_DROP - HAIR) {
				continue;
			}
			if (fits(x, surface, z)) {
				return surface;
			}
		}
		return Double.NaN;
	}

	/** Whether its body fits with its feet at {@code x}, {@code y}, {@code z}. */
	private boolean fits(double x, double y, double z) {
		return level().noCollision(this, getBoundingBox().move(x - getX(), y - getY(), z - getZ()));
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
		return new CaseMenu(ModMenus.golem(tier), id, inventory, board.parts(), board.powerData(), blockPosition(), HostRef.of(this),
			board.parts()::stillValid, tier, new HandSlot());
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
		ItemStack item = new ItemStack(tier == GolemLayout.TIER_1 ? ModItems.TIER1_BOT_GOLEM : legs() ? ModItems.TIER2_BOT_GOLEM : ModItems.TIER2_BOT_GOLEM_TORSO);
		List<ItemStack> parts = new ArrayList<>();
		for (int slot = 0; slot < board.parts().getContainerSize(); slot++) {
			parts.add(board.parts().getItem(slot).copy());
		}
		if (parts.stream().anyMatch(stack -> !stack.isEmpty())) {
			item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
		}
		item.set(ModComponents.SERIAL, board.boardId());
		if (!getMainHandItem().isEmpty() || !getOffhandItem().isEmpty()) {
			item.set(ModComponents.HANDS, ItemContainerContents.fromItems(List.of(getMainHandItem().copy(), getOffhandItem().copy())));
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
		NonNullList<ItemStack> hands = NonNullList.withSize(ModComponents.HAND_SLOTS, ItemStack.EMPTY);
		item.getOrDefault(ModComponents.HANDS, ItemContainerContents.EMPTY).copyInto(hands);
		setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hands.get(ModComponents.MAIN_HAND));
		setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, hands.get(ModComponents.OFF_HAND));
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
		output.putBoolean(HOVERING_SAVED, hovering); // (a save isn't a power cut: it hovers on)
		output.putBoolean(PATHFINDING_SAVED, pathfinding);
		output.store(ORIGINS_SAVED, ORIGINS_CODEC, Map.copyOf(origins));
		board.save(output, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		legs(input.getBooleanOr(LEGS_SAVED, true));
		hovering = input.getBooleanOr(HOVERING_SAVED, false);
		pathfinding = input.getBooleanOr(PATHFINDING_SAVED, false);
		origins.clear();
		input.read(ORIGINS_SAVED, ORIGINS_CODEC).ifPresent(origins::putAll);
		board.load(input);
	}
}
