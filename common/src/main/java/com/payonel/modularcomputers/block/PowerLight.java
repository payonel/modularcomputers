package com.payonel.modularcomputers.block;

/**
 * A computer's power button light: whether it could run (a CPU and power), is on (green), and is
 * off after a fault (aborted: blinking amber, until it's next powered on).
 */
public record PowerLight(boolean ready, boolean powered, boolean aborted) {
}
