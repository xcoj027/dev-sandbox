/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.model;

public enum ServiceCategory {
	WEB_APP("Web Applications"), JAVA_APP("Java Applications");

	private final String displayName;

	ServiceCategory(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
