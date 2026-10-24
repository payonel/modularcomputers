package com.payonel.modularcomputers.device;

/** The card device for a kind: the ones that work, or a plain {@link Card}. */
public final class Cards {
	private Cards() {
	}

	public static Card make(CardKind kind) {
		return switch (kind) {
			case REDSTONE -> new RedstoneCard();
			case WEATHER_SENSOR -> new WeatherCard();
			case MOTION_SENSOR -> new MotionSensorCard();
			case AUDIO_SENSOR -> new AudioSensorCard();
			case BLUETOOTH -> new BluetoothCard();
			case SOUND -> new SoundCard();
			case INTERNET -> new InternetCard();
			case OPERATOR -> new OperatorCard();
			case NETWORK, TIER1_WLAN, TIER2_WLAN -> new NetworkCard(kind);
			default -> new Card(kind);
		};
	}
}
