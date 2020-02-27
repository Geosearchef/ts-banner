package de.geosearchef;

import com.google.gson.Gson;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class Settings {

	private static final Path SETTINGS_FILE = Paths.get("tsbanner.settings");
	private static final Gson gson = new Gson();
	private static Settings INSTANCE;

	private List<UserSettings> users = new ArrayList<UserSettings>();

	public static synchronized void setUserSettings(UserSettings userSettings) {
		System.err.println("Setting user settings for: " + userSettings.getIp());
		INSTANCE.getUsers().removeIf(s -> Objects.equals(s.getIp(), userSettings.getIp()));
		INSTANCE.getUsers().add(userSettings);

		save();
	}

	public static synchronized UserSettings getUserSettings(String ip) {
		return INSTANCE.getUsers().stream().filter(s -> Objects.equals(ip, s.getIp())).findFirst().orElse(new UserSettings(ip, null));
	}

	private static void save() {
		try {
			Files.write(SETTINGS_FILE, gson.toJson(INSTANCE).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		} catch (IOException e) {
			System.err.println("Could not save user settings");
			e.printStackTrace();
		}
	}

	@Data
	public static class UserSettings {
		private final String ip;
		private final String backgroundName;
		//TODO: font, position, ...?
	}

	public static void init() {
		if(! Files.exists(SETTINGS_FILE)) {
			INSTANCE = new Settings();
		} else {
			try {
				INSTANCE = gson.fromJson(new String(Files.readAllBytes(SETTINGS_FILE)), Settings.class);
			} catch (IOException e) {
				System.err.println("Couldn't load settings from file");
				e.printStackTrace();
			}
		}
	}
}
