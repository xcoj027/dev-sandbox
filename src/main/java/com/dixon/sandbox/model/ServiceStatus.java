/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.model;

import java.awt.Color;

public enum ServiceStatus {
	STOPPED("STOPPED", new Color(100, 116, 139)), STARTING("STARTING...", new Color(180, 83, 9)), RUNNING("RUNNING",
			new Color(21, 128, 61)), STOPPING("STOPPING...",
					new Color(126, 34, 206)), ERROR("ERROR", new Color(185, 28, 28));

	private final String label;
	private final Color color;

	ServiceStatus(String label, Color color) {
		this.label = label;
		this.color = color;
	}

	public String getLabel() {
		return label;
	}

	public Color getColor() {
		return color;
	}
}
