package com.payonel.modularcomputers.config;

import com.payonel.modularcomputers.power.PowerUnits;

/**
 * What devices' actions cost and the rates blocks give power at, as loaded ({@link ModConfig#costs}: its defaults till then),
 * in power units: the settings' running CPUs' ticks made units ({@link #units}). A whole action's
 * cost is a whole number of units; a cost by the block ({@link #walk}, {@link #wirelessSendPerBlock})
 * stays exact, its total rounded up as it's charged, as before it was a setting.
 */
public record PowerCosts(double walk, int turn, int hand, int thrusters, int redstoneChange, int pistonPush, int fieldHold,
	int fieldMove, int craft, int craftingMove, int diskStationAction, int wiredSend, double wirelessSendPerBlock, int internetRequest,
	int internetData, int terminalWrite, int chargingPadRate, int photomaskTurn) {
	/** Running CPUs' ticks as whole power units, to the nearest. */
	public static int units(double cpuTicks) {
		return (int) Math.min(Integer.MAX_VALUE, Math.round(cpuTicks * PowerUnits.CPU));
	}

	/** Running CPUs' ticks as power units, exactly (a cost by the block). */
	public static double exact(double cpuTicks) {
		return cpuTicks * PowerUnits.CPU;
	}
}
