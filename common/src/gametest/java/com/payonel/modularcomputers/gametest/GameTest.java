package com.payonel.modularcomputers.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A server game test: a public method taking a {@code GameTestHelper}, in a class listed in
 * {@link GameTests#CLASSES}. Each loader's test mod registers them with the game (as Fabric's own
 * annotation of this name does, but the same on every loader).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
	/** Ticks it may take before it fails. */
	int maxTicks() default GameTests.MAX_TICKS;
}
