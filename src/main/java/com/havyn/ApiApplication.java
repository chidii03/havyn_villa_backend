package com.havyn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ApiApplication {

	public static void main(String[] args) {
		loadLocalEnv();
		SpringApplication.run(ApiApplication.class, args);
	}

	private static void loadLocalEnv() {
		List<Path> candidates = List.of(
				Path.of(".env"),
				Path.of("../../.env"),
				Path.of("../web/.env"));
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				loadEnvFile(candidate);
			}
		}
	}

	private static void loadEnvFile(Path path) {
		try {
			for (String rawLine : Files.readAllLines(path)) {
				String line = rawLine.trim();
				if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
					continue;
				}
				String[] parts = line.split("=", 2);
				String key = parts[0].trim();
				String value = parts[1].trim();
				if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
					value = value.substring(1, value.length() - 1);
				}
				if (!key.isBlank() && System.getenv(key) == null && System.getProperty(key) == null) {
					System.setProperty(key, value);
				}
			}
		} catch (IOException ignored) {
		}
	}

}
