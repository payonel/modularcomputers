package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.runtime.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every command has one door, its own method (the user's): no device method stands for others (a
 * set taking an action, a get repeating another's answer), so an event's name is always the method
 * called. A /dev file's lines and reads are its driver's, calling the methods themselves.
 */
class OneDoorTest {
	private static List<String> methods(Class<?> device) {
		return Arrays.stream(device.getMethods()).filter(m -> m.isAnnotationPresent(Method.class)).map(java.lang.reflect.Method::getName).toList();
	}

	@Test
	void noDeviceHasASetOrGetStandingForItsOtherMethods() {
		for (Class<?> device : List.of(RoboticsDevice.class, FieldCard.class, CraftingCard.class, BluetoothCard.class, OperatorCard.class, DiskStation.class)) {
			assertThat(methods(device)).as(device.getSimpleName() + ": its actions are its methods").doesNotContain("set");
		}
		assertThat(methods(CraftingCard.class)).as("list is what its slots hold").doesNotContain("get");
		assertThat(methods(BluetoothCard.class)).as("getLink and getBoard say it").doesNotContain("get");
	}
}
