package com.payonel.modularcomputers.config;

/**
 * Every setting's default, here and nowhere else (the user's, 2026-11-18). A setting's key in the
 * settings file is its path in this class: its groups' names and its own, lowercase, joined by dots,
 * each name's words in snake_case ({@code Robot.Cost.WALK} is {@code robot.cost.walk}, {@code
 * Power.Rate.CHARGING_PAD} is {@code power.rate.charging_pad}). Grouped as OpenComputers grouped its
 * own ({@code computer}, {@code power} with its {@code cost}s, {@code internet}, {@code robot}).
 * <p>
 * Only defaults live here: code reads a setting's value as loaded, through {@link ModConfig} and the
 * holders it configures, never from this class. A setting with no default of its own (a device
 * type's own ring depth) has no constant: its group's comment names it and what it falls back to.
 * {@code ModConfigTest} checks that every constant here is a setting the file explains, and every
 * setting the file has is a constant here.
 * <p>
 * Power costs are in ticks of a running CPU's power ({@code PowerUnits.CPU} units), as the manual
 * says them; fractions work.
 */
public final class Defaults {
	private Defaults() {
	}

	/** The computers themselves. */
	public static final class Computer {
		/** How many computers run Lua at once: the server's cores they share (OpenComputers' threads). */
		public static final int CPU_CORES = 4;

		private Computer() {
		}
	}

	/** Power: batteries, the rates blocks give it at, and what devices' actions cost. */
	public static final class Power {
		private Power() {
		}

		/** Batteries: how long each tier runs a computer, and tier 2's recharging. */
		public static final class Battery {
			private Battery() {
			}

			public static final class Tier1 {
				/** Minecraft days a full one runs a computer (10% a day); 0 never runs out. */
				public static final double DURATION = 10;

				private Tier1() {
				}
			}

			public static final class Tier2 {
				/** Minecraft days a full one runs a computer: twice a tier 1. */
				public static final double DURATION = 20;
				/** What it regains a tick at rest, as a share of a running computer's draw: two seconds of rest charge one of running. */
				public static final double RECHARGE_RATE = 0.5;

				private Tier2() {
				}
			}

			public static final class Tier3 {
				/** Minecraft days a full one runs a computer: 0, it never runs out. */
				public static final double DURATION = 0;

				private Tier3() {
				}
			}
		}

		/** The rates at which blocks give power, in running CPUs' ticks a tick (OpenComputers' power.rate). */
		public static final class Rate {
			/** What a charging pad gives a robot a tick: ten, so a robot charges far faster than it runs down. */
			public static final double CHARGING_PAD = 10;

			private Rate() {
			}
		}

		/** What devices' actions cost, in running CPUs' ticks (the bot golem's are {@link Robot.Cost}). */
		public static final class Cost {
			private Cost() {
			}

			/** The redstone card. */
			public static final class Redstone {
				/** Each change of a face's output. */
				public static final double CHANGE = 1;

				private Redstone() {
				}
			}

			/** The robotic piston. */
			public static final class RoboticPiston {
				/** Each push, extend or retract, moved anything or not. */
				public static final double PUSH = 5;

				private RoboticPiston() {
				}
			}

			/** The energy field card. */
			public static final class Field {
				/** Each tick it holds something. */
				public static final double HOLD = 2;
				/** Each block it moves what it holds. */
				public static final double MOVE = 1;

				private Field() {
				}
			}

			/** The crafting card. */
			public static final class Crafting {
				/** Each craft. */
				public static final double CRAFT = 1;
				/** Each move of items: between its slots, into its robot's hand, to or from a container, a pick up, a drop. */
				public static final double MOVE = 1;

				private Crafting() {
				}
			}

			/** The Disk Station. */
			public static final class DiskStation {
				/** Each action: a format, wipe, clone, install or update. */
				public static final double ACTION = 20;

				private DiskStation() {
				}
			}

			/** Network cards, wired and WLAN. */
			public static final class Network {
				/** Each frame a wired card sends. */
				public static final double WIRED_SEND = 0.1;
				/** Each frame a WLAN card sends, for each block of its strength (OpenComputers' wirelessCostPerRange): a tier 1 at its strongest (16 blocks) a whole tick. */
				public static final double WIRELESS_SEND_PER_BLOCK = 1.0 / 16;

				private Network() {
				}
			}

			/** The internet card. */
			public static final class Internet {
				/** Each request. */
				public static final double REQUEST = 1;
				/** Each 4 KiB of a body read (a part of 4 KiB costs it whole). */
				public static final double DATA = 0.1;

				private Internet() {
				}
			}

			/** Terminals (screens). */
			public static final class Terminal {
				/** Each write to it, whatever its length: a hundredth of a tick, one power unit. */
				public static final double WRITE = 0.01;

				private Terminal() {
				}
			}
		}
	}

	/** Every device on a bus. */
	public static final class Device {
		private Device() {
		}

		/**
		 * How deep a device's rings are: the events it keeps for its program. {@link #ALL} is every
		 * device's; a type's own, {@code device.ring_depth.<type>} (its /sys class's name: {@code
		 * robotics}, {@code motion}, {@code network}...), is opt-in, with no default of its own: unset,
		 * the type takes {@code device.ring_depth.all}.
		 */
		public static final class RingDepth {
			/** Every device's depth, unless its type sets its own. */
			public static final int ALL = 512;

			private RingDepth() {
			}
		}
	}

	/** The internet card's reach and buffers. */
	public static final class Internet {
		/** Whether internet cards work at all. */
		public static final boolean ENABLED = true;
		/** Hosts, domains or ranges reached though they'd be refused, comma-separated: none. */
		public static final String ALLOW = "";
		/** Hosts, domains or ranges refused beyond the default (the public internet only), comma-separated: none. */
		public static final String DENY = "";
		/** How much of a response's body a request holds unread before its download waits, in KiB. */
		public static final int RESPONSE_BUFFER_KIB = 64;

		private Internet() {
		}
	}

	/** The bot golem. */
	public static final class Robot {
		/** How far its pathfinding searches, in blocks walked: the game's own pathfinding range for its mobs. */
		public static final int PATHFINDING_RANGE = 96;

		private Robot() {
		}

		/** What its robotics costs, in running CPUs' ticks. */
		public static final class Cost {
			/** Each block walked (a part of a block, its part). */
			public static final double WALK = 1;
			/** Each turn, however far. */
			public static final double TURN = 1;
			/** Each action with its hand: a use, a swing, a store into a container, a take from one. */
			public static final double HAND = 1;
			/** Each tick its thrusters fire (flying or hovering). */
			public static final double THRUSTERS = 5;

			private Cost() {
			}
		}
	}
}
