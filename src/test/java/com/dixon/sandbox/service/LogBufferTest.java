/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dixon.sandbox.model.ServiceCategory;
import com.dixon.sandbox.model.ServiceConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LogBufferTest {

	@Test
	void classifiesOnlyStructuredLogLevelMarkers() {
		LogBuffer buffer = new LogBuffer(null);

		buffer.appendWithFallback("2026-08-24 15:57:04.667 INFO service - Invalid cookie ignored",
				LogBuffer.LogLevel.INFO);
		buffer.appendWithFallback(" Note: further occurrences of this error will be logged at DEBUG level.",
				LogBuffer.LogLevel.INFO);
		buffer.appendWithFallback("2026-08-24 15:57:05.000 ERROR service - Database unavailable",
				LogBuffer.LogLevel.INFO);
		buffer.appendWithFallback("{\"log.level\":\"WARN\",\"message\":\"Retrying\"}", LogBuffer.LogLevel.INFO);

		assertThat(buffer.getEntries()).extracting(LogBuffer.LogEntry::getLevel).containsExactly(
				LogBuffer.LogLevel.INFO, LogBuffer.LogLevel.INFO, LogBuffer.LogLevel.ERROR, LogBuffer.LogLevel.WARN);
	}

	@Test
	void removesOnlyCopiedEntriesForTheClearedServiceWithoutReplayingConcurrentEntriesTwice() throws Exception {
		LogBuffer unifiedBuffer = new LogBuffer(null);
		ServiceConfig web = service("web", "Buyer Web");
		ServiceConfig gateway = service("gateway", "Gateway");

		unifiedBuffer.appendSystem("Sandbox ready");
		unifiedBuffer.appendFromService(web, "Web ready", LogBuffer.LogLevel.INFO);
		unifiedBuffer.appendFromService(gateway, "Gateway ready", LogBuffer.LogLevel.INFO);
		SwingUtilities.invokeAndWait(() -> {
		});
		List<String> displayedLines = new ArrayList<>();
		unifiedBuffer.addListener(new LogBuffer.LogListener() {
			@Override
			public void onLogAdded(LogBuffer.LogEntry entry) {
				displayedLines.add(entry.getText());
			}

			@Override
			public void onLogCleared() {
				displayedLines.clear();
			}
		});

		unifiedBuffer.removeEntriesForService(web.getId());
		unifiedBuffer.appendFromService(gateway, "Gateway after clear", LogBuffer.LogLevel.INFO);
		SwingUtilities.invokeAndWait(() -> {
		});

		assertThat(unifiedBuffer.getEntries()).extracting(LogBuffer.LogEntry::getServiceId).containsExactly("all",
				"gateway", "gateway");
		assertThat(displayedLines).containsExactly("[SANDBOX] Sandbox ready", "Gateway ready", "Gateway after clear");
	}

	private ServiceConfig service(String id, String name) {
		return new ServiceConfig(id, name, "Test service", ServiceCategory.WEB_APP, new File("."), "true", 0, null);
	}
}
