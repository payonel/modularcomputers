package com.payonel.modularcomputers.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.payonel.modularcomputers.bus.InterruptLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A device's events (the user's, 2026-11-11): news a window, lost said; a command's end never dropped. */
class EventQueueTest {
	private final EventQueue queue = new EventQueue(new InterruptLine(), () -> { }, () -> true);

	private List<Map<String, Object>> all() {
		List<Map<String, Object>> taken = new java.util.ArrayList<>();
		for (Map<String, Object> event; (event = queue.pull()) != null; ) {
			taken.add(event);
		}
		return taken;
	}

	@Test
	void aCommandsEndIsNeverDroppedNewsGoesFirst() {
		queue.ended("work", null);
		int over = 5;
		for (int i = 0; i < EventQueue.window() + over; i++) {
			queue.add(Map.of("event", "news"));
		}
		List<Map<String, Object>> taken = all();
		assertThat(taken.stream().filter(event -> "work".equals(event.get("event"))).count()).as("its end, kept").isEqualTo(1);
		assertThat(taken.stream().filter(event -> "news".equals(event.get("event"))).count()).as("a window of news").isEqualTo(EventQueue.window());
		assertThat(taken.stream().mapToLong(event -> event.get("lost") instanceof Long n ? n : 0).sum()).as("every one that went, said").isEqualTo(over);
	}

	@Test
	void itsEndsFillItTillOneIsRead() {
		for (int i = 0; i < EventQueue.ends(); i++) {
			assertThat(queue.endsFull()).isFalse();
			queue.ended("work", null);
		}
		assertThat(queue.endsFull()).as("as many ends as it holds: its next command refused").isTrue();
		queue.pull();
		assertThat(queue.endsFull()).as("one read: room again").isFalse();
	}

	@Test
	void aLoadKeepsWhichAreEnds() {
		queue.ended("work", null);
		Map<String, Object> saved = new LinkedHashMap<>();
		queue.save(saved);
		EventQueue loaded = new EventQueue(new InterruptLine(), () -> { }, () -> true);
		loaded.restore(saved);
		for (int i = 0; i < EventQueue.window() + 1; i++) {
			loaded.add(Map.of("event", "news"));
		}
		assertThat(loaded.pull()).as("its end, still the oldest: news went first").containsEntry("event", "work");
	}
}
