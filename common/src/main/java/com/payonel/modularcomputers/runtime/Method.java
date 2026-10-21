package com.payonel.modularcomputers.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a device's method that programs can call through the motherboard API. The method takes
 * {@link Arguments} and returns a {@link Result}; its name is the method's name. Throwing is
 * allowed but never reaches the machine: it becomes a soft failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Method {
	/** What it does and how to call it, e.g. {@code "set(column, row, text): writes text at a cell"}. */
	String doc() default "";
}
