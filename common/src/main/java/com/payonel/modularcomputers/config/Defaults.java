package com.payonel.modularcomputers.config;

/**
 * Every setting's default, here and nowhere else (the user's, 2026-11-18). A setting's key in the
 * settings file is its path in this class: its groups' names and its own, lowercase, joined by dots,
 * each name's words in snake_case ({@code Power.Cost.Robot.WALK} is {@code power.cost.robot.walk},
 * {@code Power.Rate.CHARGING_PAD} is {@code power.rate.charging_pad}). Grouped as OpenComputers
 * grouped its own ({@code computer}, {@code power} with its {@code draw}s and {@code cost}s,
 * {@code internet}, {@code robot}).
 * <p>
 * Only defaults live here: code reads a setting's value as loaded, through {@link ModConfig} and the
 * holders it configures, never from this class. A setting with no default of its own (a device
 * type's own ring depth) has no constant: its group's comment names it and what it falls back to.
 * {@code ModConfigTest} checks that every constant here is a setting the file explains, and every
 * setting the file has is a constant here. (The power yardstick, {@code PowerUnits}' running
 * computer, is built from two of these on purpose: a computer at the default draws, a fixed unit.)
 * <p>
 * Draws are in power units a tick (a watt each), whole numbers. Power costs are in ticks of a
 * running CPU's power ({@code PowerUnits.CPU} units: a tier 1's default draw, fixed whatever its
 * draw is set to), as the manual says them; fractions work.
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

	/** Power: batteries, the rates blocks give it at, what devices draw, and what their actions cost. */
	public static final class Power {
		private Power() {
		}

		/**
		 * Batteries: how long each tier runs a computer, and tier 2's recharging. A day is of the
		 * yardstick computer (its CPU and screen at their default draws), so a battery holds a fixed
		 * amount of power: draws set higher run it down sooner.
		 */
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

		/**
		 * What devices draw each tick they have power, in power units a tick (a watt each): whole
		 * numbers, 0 or more. A device's work costs more on top ({@link Cost}).
		 */
		public static final class Draw {
			/** A screen (a terminal), each tick it has power: the yardstick's screen. */
			public static final int TERMINAL = 10;
			/** A sound card. */
			public static final int SOUND_CARD = 6;
			/** A motion sensor card. */
			public static final int MOTION_SENSOR = 9;
			/** An audio sensor card, as a motion sensor. */
			public static final int AUDIO_SENSOR = 9;
			/** An internet card, at rest. */
			public static final int INTERNET = 4;
			/** A Disk Station, at rest. */
			public static final int DISK_STATION = 1;
			/** A photomask, idling: very little, as a tier 3 CPU waiting (user, 2026-10-12). */
			public static final int PHOTOMASK = 5;

			private Draw() {
			}

			/**
			 * CPUs, by tier: while they run, and while they wait for an interrupt (off, nothing).
			 * Higher tiers run hungrier and idle leaner, as real chips with deeper idle states do.
			 */
			public static final class Cpu {
				private Cpu() {
				}

				public static final class Tier1 {
					/** Each tick it runs: the yardstick's CPU. */
					public static final int RUNNING = 100;
					/** Each tick it waits for an interrupt. */
					public static final int WAITING = 50;

					private Tier1() {
					}
				}

				public static final class Tier2 {
					/** Each tick it runs. */
					public static final int RUNNING = 150;
					/** Each tick it waits for an interrupt. */
					public static final int WAITING = 20;

					private Tier2() {
					}
				}

				public static final class Tier3 {
					/** Each tick it runs. */
					public static final int RUNNING = 200;
					/** Each tick it waits for an interrupt. */
					public static final int WAITING = 5;

					private Tier3() {
					}
				}
			}

			/** Network cards, at rest. */
			public static final class Network {
				/** A wired card. */
				public static final int WIRED = 2;
				/** A WLAN card (its radio listening). */
				public static final int WIRELESS = 5;

				private Network() {
				}
			}

			/** Disks, by tier: higher tiers draw less (user design, 2026-10-11). */
			public static final class Disk {
				/** A tier 1 disk. */
				public static final int TIER1 = 8;
				/** A tier 2 disk. */
				public static final int TIER2 = 5;
				/** A tier 3 disk. */
				public static final int TIER3 = 2;

				private Disk() {
				}
			}
		}

		/** What devices' actions cost, in running CPUs' ticks. */
		public static final class Cost {
			private Cost() {
			}

			/** The bot golem's robotics. */
			public static final class Robot {
				/** Each block walked (a part of a block, its part). */
				public static final double WALK = 1;
				/** Each turn, however far. */
				public static final double TURN = 1;
				/** Each action with its hand: a use, a swing, a store into a container, a take from one. */
				public static final double HAND = 1;
				/** Each tick its thrusters fire (flying or hovering). */
				public static final double THRUSTERS = 5;

				private Robot() {
				}
			}

			/** The photomask. */
			public static final class Photomask {
				/**
				 * Each turn of its work (a wafer takes several). A turn of its crank by hand charges
				 * this and its idle draw till its next turn, and its buffer holds a few cranks.
				 */
				public static final double TURN = 1;

				private Photomask() {
				}
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

		/** The motion sensor card's looks at the world. */
		public static final class MotionSensor {
			/** How often it looks for creatures that moved, in ticks: four times a second. */
			public static final int INTERVAL = 5;
			/**
			 * How far a creature must have moved since its last look to be an event, in blocks, along
			 * any one axis: a tenth of a block. 0 makes every creature it sees at a look an event.
			 */
			public static final double THRESHOLD = 0.1;

			private MotionSensor() {
			}
		}
	}

	/** The internet card's reach, buffers and limits. */
	public static final class Internet {
		/** Whether internet cards work at all. */
		public static final boolean ENABLED = true;
		/** Hosts, domains or ranges reached though they'd be refused, comma-separated: none. */
		public static final String ALLOW = "";
		/** Hosts, domains or ranges refused beyond the default (the public internet only), comma-separated: none. */
		public static final String DENY = "";
		/** How much of a response's body a request holds unread before its download waits, in KiB. */
		public static final int RESPONSE_BUFFER_KIB = 64;
		/** How long a request may take to connect, in seconds. */
		public static final double CONNECT_TIMEOUT = 10;
		/** How long a request may wait with nothing read, in seconds. */
		public static final double READ_TIMEOUT = 30;
		/** How long a request may take in all, in seconds, time its download waits for room counted: ten minutes (a server dripping a byte a while can't keep its thread for ever). */
		public static final double REQUEST_TIMEOUT = 600;
		/** The most a response's status line and headers may take together, in KiB. */
		public static final int MAX_HEADER_KIB = 64;
		/** The most a request's own body (an upload) may be, in KiB: 1 MiB. */
		public static final int MAX_UPLOAD_KIB = 1024;
		/** How many requests a card has open at once. */
		public static final int REQUESTS_PER_CARD = 4;
		/** How many requests run at once on the whole server, every card's: however many computers players build, their threads are bounded. */
		public static final int REQUESTS_PER_SERVER = 64;

		private Internet() {
		}
	}

	/** The bot golem (what its robotics costs is {@link Power.Cost.Robot}'s). */
	public static final class Robot {
		/** How far its pathfinding searches, in blocks walked: the game's own pathfinding range for its mobs. */
		public static final int PATHFINDING_RANGE = 96;
		/** How far it turns a tick, in degrees: a right angle in five ticks. */
		public static final double TURN_SPEED = 18;

		private Robot() {
		}
	}
}
