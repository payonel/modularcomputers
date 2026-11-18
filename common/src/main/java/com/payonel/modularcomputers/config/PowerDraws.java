package com.payonel.modularcomputers.config;

import java.util.ArrayList;
import java.util.List;

/**
 * What devices draw each tick they have power, as loaded ({@link ModConfig#draws}: its defaults
 * till then), in power units a tick. Devices read their draw from here as their power is settled
 * (power is lazy: {@code BusPower.collect}), never keeping a copy, so a reload reaches every device
 * already built, from the next settling on. A server sends its draws to its players with its other
 * shown settings ({@code SettingsPayload}): a CPU's and a disk's tooltips show them.
 *
 * @param cpu a CPU's, by tier (tier 1 first)
 * @param disk a disk's, by tier (tier 1 first)
 */
public record PowerDraws(List<Cpu> cpu, int terminal, int soundCard, int motionSensor, int audioSensor, int wiredCard, int wirelessCard,
	int internetCard, int diskStation, List<Integer> disk, int photomask) {
	/** A CPU tier's: while it runs, and while it waits for an interrupt. */
	public record Cpu(int running, int waiting) {
	}

	public PowerDraws {
		cpu = List.copyOf(cpu);
		disk = List.copyOf(disk);
	}

	/** Every draw, in one order ({@link #of}'s), to send. */
	public List<Integer> values() {
		List<Integer> values = new ArrayList<>();
		cpu.forEach(tier -> {
			values.add(tier.running());
			values.add(tier.waiting());
		});
		values.addAll(List.of(terminal, soundCard, motionSensor, audioSensor, wiredCard, wirelessCard, internetCard, diskStation));
		values.addAll(disk);
		values.add(photomask);
		return values;
	}

	/** Draws from {@link #values}' list, for these numbers of CPU and disk tiers; null if it isn't one. */
	public static PowerDraws of(List<Integer> values, int cpuTiers, int diskTiers) {
		if (values.size() != CPU_DRAWS * cpuTiers + UNTIERED + diskTiers + 1) {
			return null;
		}
		List<Cpu> cpu = new ArrayList<>();
		for (int tier = 0; tier < cpuTiers; tier++) {
			cpu.add(new Cpu(values.get(CPU_DRAWS * tier), values.get(CPU_DRAWS * tier + 1)));
		}
		int at = CPU_DRAWS * cpuTiers;
		int disks = at + UNTIERED;
		return new PowerDraws(cpu, values.get(at), values.get(at + 1), values.get(at + 2), values.get(at + 3), values.get(at + 4),
			values.get(at + 5), values.get(at + 6), values.get(at + 7), values.subList(disks, disks + diskTiers), values.get(disks + diskTiers));
	}

	/** A CPU tier's draws in the list: running, then waiting. */
	private static final int CPU_DRAWS = 2;
	/** The draws between the CPUs' and the disks': the terminal's to the Disk Station's. */
	private static final int UNTIERED = 8;
}
