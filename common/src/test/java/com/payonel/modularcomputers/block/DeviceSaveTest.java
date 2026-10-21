package com.payonel.modularcomputers.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A device's saved state goes into the block's NBT and comes back with its types: integers stay integers, floats floats. */
class DeviceSaveTest {
	@Test
	void valuesComeBackAsTheyWere() {
		Map<Object, Object> event = new LinkedHashMap<>();
		event.put("name", "motion");
		event.put("x", 1.5);
		event.put("count", 3L);
		event.put("open", true);
		event.put(2L, "an integer key");
		List<Object> values = new ArrayList<>(Arrays.asList("a", 2L, 2.0, false, event, List.of()));
		Object back = DeviceSave.value(DeviceSave.tag(values));
		assertThat(back).isEqualTo(values);
		assertThat(((List<?>) back).get(1)).isInstanceOf(Long.class);
		assertThat(((List<?>) back).get(2)).isInstanceOf(Double.class);
	}

	@Test
	void nilStaysNil() {
		assertThat(DeviceSave.value(DeviceSave.tag(Arrays.asList("a", null, "c")))).isEqualTo(Arrays.asList("a", null, "c"));
	}
}
