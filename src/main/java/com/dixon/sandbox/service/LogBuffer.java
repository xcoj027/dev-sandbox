/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceConfig;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

public class LogBuffer {

	public static final int MAX_BUFFER_LINES = 5000;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[;?0-9]*[ -/]*[@-~]");
	private static final Pattern JSON_LEVEL_PATTERN = Pattern
			.compile("(?i)\\\"log\\.level\\\"\\s*:\\s*\\\"(ERROR|WARN|WARNING|INFO|DEBUG)\\\"");
	private static final Pattern CONSOLE_LEVEL_PATTERN = Pattern.compile(
			"(?i)^(?:\\[[^]]+\\]\\s*)?(?:\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\s+)?(ERROR|WARN|WARNING|INFO|DEBUG)(?:\\s|:|\\]|$)");

	public enum LogLevel {
		INFO, WARN, ERROR, DEBUG, SYSTEM
	}

	public static class LogEntry {

		private final String timestamp;
		private final String serviceId;
		private final String serviceName;
		private final LogLevel level;
		private final String text;

		public LogEntry(String serviceId, String serviceName, LogLevel level, String text) {
			this.timestamp = LocalDateTime.now().format(TIME_FORMAT);
			this.serviceId = serviceId;
			this.serviceName = serviceName;
			this.level = level;
			this.text = text;
		}

		public String getTimestamp() {
			return timestamp;
		}

		public String getServiceId() {
			return serviceId;
		}

		public String getServiceName() {
			return serviceName;
		}

		public LogLevel getLevel() {
			return level;
		}

		public String getText() {
			return text;
		}

		public String getFormattedLine() {
			return String.format("[%s] [%s] [%-5s] %s", timestamp, serviceName, level.name(), text);
		}
	}

	public interface LogListener {

		void onLogAdded(LogEntry entry);

		void onLogCleared();
	}

	private final ServiceConfig serviceConfig;
	private final LinkedList<LogEntry> entries = new LinkedList<>();
	private final List<LogListener> listeners = new CopyOnWriteArrayList<>();
	private LogLevel lastStreamLevel;

	public LogBuffer(ServiceConfig serviceConfig) {
		this.serviceConfig = serviceConfig;
	}

	public synchronized void append(String line, LogLevel overrideLevel) {
		if (line == null) {
			return;
		}

		LogLevel level = overrideLevel != null ? overrideLevel : classifyLine(line);
		LogEntry entry = new LogEntry(serviceConfig != null ? serviceConfig.getId() : "all",
				serviceConfig != null ? serviceConfig.getName() : "System", level, line);

		entries.addLast(entry);
		if (entries.size() > MAX_BUFFER_LINES) {
			entries.removeFirst();
		}

		notifyListeners(entry);
	}

	public synchronized void appendWithFallback(String line, LogLevel fallbackLevel) {
		if (line == null) {
			return;
		}

		LogLevel detectedLevel = detectExplicitLevel(line);
		LogLevel level = detectedLevel != null ? detectedLevel : fallbackLevel;
		if (detectedLevel == null && isExceptionContinuation(line) && lastStreamLevel != null) {
			level = lastStreamLevel;
		}
		lastStreamLevel = level;
		append(line, level);
	}

	public synchronized void appendSystem(String text) {
		append("[SANDBOX] " + text, LogLevel.SYSTEM);
	}

	public synchronized void appendFromService(ServiceConfig source, String line, LogLevel fallbackLevel) {
		LogLevel detectedLevel = detectExplicitLevel(line);
		LogLevel level = detectedLevel != null ? detectedLevel : fallbackLevel;
		LogEntry entry = new LogEntry(source.getId(), source.getName(), level, line);
		entries.addLast(entry);
		if (entries.size() > MAX_BUFFER_LINES) {
			entries.removeFirst();
		}
		notifyListeners(entry);
	}

	private LogLevel classifyLine(String text) {
		LogLevel detectedLevel = detectExplicitLevel(text);
		if (detectedLevel != null) {
			return detectedLevel;
		}

		String upper = ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("").toUpperCase();
		if (upper.contains("EXCEPTION") || upper.contains("FATAL")) {
			return LogLevel.ERROR;
		}
		return LogLevel.INFO;
	}

	private LogLevel detectExplicitLevel(String text) {
		String plainText = ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("");
		java.util.regex.Matcher jsonMatcher = JSON_LEVEL_PATTERN.matcher(plainText);
		if (jsonMatcher.find()) {
			return parseLevel(jsonMatcher.group(1));
		}
		java.util.regex.Matcher consoleMatcher = CONSOLE_LEVEL_PATTERN.matcher(plainText);
		return consoleMatcher.find() ? parseLevel(consoleMatcher.group(1)) : null;
	}

	private LogLevel parseLevel(String level) {
		return switch (level.toUpperCase()) {
			case "ERROR" -> LogLevel.ERROR;
			case "WARN", "WARNING" -> LogLevel.WARN;
			case "DEBUG" -> LogLevel.DEBUG;
			default -> LogLevel.INFO;
		};
	}

	private boolean isExceptionContinuation(String text) {
		String plainText = ANSI_ESCAPE_PATTERN.matcher(text).replaceAll("");
		return plainText.matches("^\\s+at\\s+.*") || plainText.matches("^\\s*Caused by:.*")
				|| plainText.matches("^\\s*Suppressed:.*") || plainText.matches("^\\s*\\.\\.\\. \\d+ more$");
	}

	private void notifyListeners(LogEntry entry) {
		SwingUtilities.invokeLater(() -> {
			for (LogListener listener : listeners) {
				listener.onLogAdded(entry);
			}
		});
	}

	public synchronized void clear() {
		entries.clear();
		lastStreamLevel = null;
		SwingUtilities.invokeLater(() -> {
			for (LogListener listener : listeners) {
				listener.onLogCleared();
			}
		});
	}

	public synchronized void removeEntriesForService(String serviceId) {
		entries.removeIf(entry -> serviceId.equals(entry.getServiceId()));
		List<LogEntry> retainedEntries = new ArrayList<>(entries);
		SwingUtilities.invokeLater(() -> {
			for (LogListener listener : listeners) {
				listener.onLogCleared();
				for (LogEntry entry : retainedEntries) {
					listener.onLogAdded(entry);
				}
			}
		});
	}

	public synchronized List<LogEntry> getEntries() {
		return new ArrayList<>(entries);
	}

	public synchronized List<LogEntry> filterEntries(String query, boolean useRegex) {
		if (query == null || query.isBlank()) {
			return getEntries();
		}

		List<LogEntry> result = new ArrayList<>();
		Pattern pattern = null;
		if (useRegex) {
			try {
				pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
			} catch (Exception e) {
				// Fallback to literal search if regex fails
			}
		}

		String lowerQuery = query.toLowerCase();
		for (LogEntry entry : entries) {
			if (pattern != null) {
				if (pattern.matcher(entry.getText()).find()) {
					result.add(entry);
				}
			} else {
				if (entry.getText().toLowerCase().contains(lowerQuery)
						|| entry.getServiceName().toLowerCase().contains(lowerQuery)) {
					result.add(entry);
				}
			}
		}
		return result;
	}

	public void addListener(LogListener listener) {
		listeners.add(listener);
	}

	public void removeListener(LogListener listener) {
		listeners.remove(listener);
	}

	public synchronized void exportToFile(File file) throws IOException {
		try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
			for (LogEntry entry : entries) {
				writer.println(entry.getFormattedLine());
			}
		}
	}
}
