/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceConfig;
import java.awt.Desktop;
import java.net.URI;

public class BrowserLauncher {
	private static final System.Logger LOGGER = System.getLogger(BrowserLauncher.class.getName());

	public static void openInBrowser(String urlStr) {
		if (urlStr == null || urlStr.isBlank()) {
			return;
		}

		String os = System.getProperty("os.name").toLowerCase();
		boolean isMac = os.contains("mac");
		boolean isWindows = os.contains("win");

		if (isMac) {
			try {
				new ProcessBuilder("open", "-a", "Google Chrome", urlStr).start();
				return;
			} catch (Exception exception) {
				LOGGER.log(System.Logger.Level.DEBUG, "Explicit Google Chrome launch failed, trying default open",
						exception);
			}
			try {
				new ProcessBuilder("open", urlStr).start();
				return;
			} catch (Exception exception) {
				LOGGER.log(System.Logger.Level.WARNING, "Mac open command failed for " + urlStr, exception);
			}
		} else if (isWindows) {
			try {
				new ProcessBuilder("cmd.exe", "/c", "start", "chrome", urlStr).start();
				return;
			} catch (Exception exception) {
				LOGGER.log(System.Logger.Level.DEBUG, "Windows chrome launch failed, trying default start", exception);
			}
			try {
				new ProcessBuilder("cmd.exe", "/c", "start", urlStr).start();
				return;
			} catch (Exception exception) {
				LOGGER.log(System.Logger.Level.WARNING, "Windows start command failed for " + urlStr, exception);
			}
		}

		try {
			URI uri = URI.create(urlStr);
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(uri);
				return;
			}
		} catch (Exception exception) {
			LOGGER.log(System.Logger.Level.DEBUG, "Desktop browser launch failed for " + urlStr, exception);
		}

		try {
			new ProcessBuilder("xdg-open", urlStr).start();
		} catch (Exception exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Fallback browser launch failed for " + urlStr, exception);
		}
	}

	public static String getBrowserUrl(ServiceConfig config) {
		if (config == null || config.getDefaultPort() <= 0) {
			return null;
		}
		if (config.getHealthCheckUrl() != null && !config.getHealthCheckUrl().isBlank()) {
			String url = config.getHealthCheckUrl();
			if (url.endsWith("/actuator/health")) {
				return url.substring(0, url.length() - "/actuator/health".length()) + "/";
			}
			return url;
		}
		return "http://localhost:" + config.getDefaultPort() + "/";
	}
}
