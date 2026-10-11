package com.payonel.modularcomputers.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The methods a device offers programs: its public {@link Method}-annotated methods, found once
 * per class. Calling one never throws: a bad argument, an unknown method or a device's own
 * exception all come back as a soft failure.
 */
public final class DeviceMethods {
	private static final Map<Class<?>, Map<String, java.lang.reflect.Method>> CACHE = new ConcurrentHashMap<>();

	private DeviceMethods() {
	}

	/** The device's methods by name, in name order. */
	public static Map<String, java.lang.reflect.Method> of(Object device) {
		return CACHE.computeIfAbsent(device.getClass(), DeviceMethods::find);
	}

	private static Map<String, java.lang.reflect.Method> find(Class<?> type) {
		Map<String, java.lang.reflect.Method> methods = new TreeMap<>();
		for (java.lang.reflect.Method method : type.getMethods()) {
			if (!method.isAnnotationPresent(Method.class)) {
				continue;
			}
			boolean shape = Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
				&& method.getParameterCount() == 1 && method.getParameterTypes()[0] == Arguments.class
				&& method.getReturnType() == Result.class;
			if (!shape) {
				throw new IllegalStateException(type.getSimpleName() + "." + method.getName() + " must be public Result name(Arguments)");
			}
			methods.put(method.getName(), method);
		}
		return Collections.unmodifiableMap(methods); // still in name order
	}

	public static Optional<String> doc(Object device, String name) {
		return Optional.ofNullable(of(device).get(name)).map(method -> method.getAnnotation(Method.class).doc());
	}

	/** Calls a device's method. Never throws. */
	public static Result invoke(Object device, String name, Arguments args) {
		java.lang.reflect.Method method = of(device).get(name);
		if (method == null) {
			return Result.fail("no such method: " + name);
		}
		try {
			Result result = (Result) method.invoke(device, args);
			return result == null ? Result.ok() : result;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			return Result.fail(cause instanceof Arguments.ArgumentError ? cause.getMessage() : "device error: " + cause);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Result.fail("device error: " + e);
		}
	}
}
