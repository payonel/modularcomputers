package com.payonel.modularcomputers.machine;

/** A firmware chip. The CPU loads its program from the motherboard's EEPROM slot at power-on. */
public record Eeprom(Program program) {
}
