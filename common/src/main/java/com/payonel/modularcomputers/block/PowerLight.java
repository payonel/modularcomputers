package com.payonel.modularcomputers.block;

/**
 * A computer's power button light: whether it could run (a CPU and power), is on, and its CPU
 * halted (green, then amber).
 */
public record PowerLight(boolean ready, boolean powered, boolean halted) {
}
