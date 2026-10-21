package com.payonel.modularcomputers.device;

import com.payonel.modularcomputers.bus.InterruptLine;
import com.payonel.modularcomputers.runtime.Arguments;
import com.payonel.modularcomputers.runtime.Method;
import com.payonel.modularcomputers.runtime.Result;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A weather sensor card (user design, 2026-10-13): the weather where it's installed ({@code clear},
 * {@code rain}, {@code snow} or {@code thunder}), and an event (its interrupt) when it changes.
 */
public final class WeatherCard extends Card {
	private final InterruptLine line = new InterruptLine();
	/** It works while a running computer has it on its bus (it draws nothing to speak of). */
	private final com.payonel.modularcomputers.bus.BusPower power = new com.payonel.modularcomputers.bus.BusPower(0);
	private final EventQueue events = new EventQueue(line, this::changed, this::isAccepting);
	private String weather;

	public WeatherCard() {
		super(CardKind.WEATHER_SENSOR);
	}

	/** The weather where it is, seen in the world: a change is an event (not the first look). */
	public void sense(String now) {
		if (weather != null && !weather.equals(now)) {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("name", "weather");
			event.put("old", weather);
			event.put("new", now);
			events.add(event);
		}
		if (!now.equals(weather)) {
			changed();
		}
		weather = now;
	}


	@Method(doc = "getWeather(): clear, rain, snow or thunder")
	public Result getWeather(Arguments args) {
		return Result.ok(weather == null ? "clear" : weather);
	}

	@Method(doc = "pull(): the oldest change, as {name, old, new}, or nil")
	public Result pull(Arguments args) {
		return Result.ok(events.pull());
	}

	@Override
	public com.payonel.modularcomputers.bus.BusPower power() {
		return power;
	}

	@Override
	public String streamMethod() {
		return "pull";
	}

	/** Its events waiting, and the weather it saw last (so a load isn't a change). */
	@Override
	public Map<String, Object> saved() {
		Map<String, Object> saved = new LinkedHashMap<>();
		events.save(saved);
		if (weather != null) {
			saved.put("weather", weather);
		}
		return saved;
	}

	@Override
	public void restore(Map<String, Object> saved) {
		events.restore(saved);
		weather = saved.get("weather") instanceof String seen ? seen : null;
	}

	/** It stopped taking data: its events go. */
	@Override
	protected void dropData() {
		events.clear();
	}

	@Override
	public boolean interruptPending() {
		return events.pending();
	}

	@Override
	public InterruptLine interruptLine() {
		return line;
	}

	@Override
	public void powerOff() {
		events.clear();
	}
}
