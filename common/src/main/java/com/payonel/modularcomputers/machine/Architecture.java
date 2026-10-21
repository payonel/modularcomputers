package com.payonel.modularcomputers.machine;

/**
 * What a CPU executes from the EEPROM's text (user design, 2026-10-12), as a real chip's
 * instruction set decides what firmware it can boot: every crafted CPU is a Lua machine; the Java
 * CPU (creative only) runs the Java program class the text names.
 */
public enum Architecture {
	LUA,
	JAVA,
}
