package com.payonel.modularcomputers.runtime;

/**
 * A value a device hands a program to use later, like an open file: Lua's userdata. It offers
 * methods as a device does ({@link Method}), called through {@link Board#invoke(Handle, String,
 * Object...)}. On the host it holds the device that made it and whatever that needs (a real file).
 * It stops working when closed, or when its device loses power or leaves the bus.
 */
public interface Handle {
	boolean isOpen();

	/**
	 * The program let go of it without closing it (Lua collected it): it gives back what it holds
	 * (memory charged to the program, a disk's reserved space). Server thread.
	 */
	default void abandon() {
	}
}
