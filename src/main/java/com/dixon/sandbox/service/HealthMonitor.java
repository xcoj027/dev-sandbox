/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceConfig;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

public class HealthMonitor {

	public interface HealthChangeListener {

		void onHealthCheckCompleted();
	}

	private final ServiceCatalog catalog;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private HealthChangeListener listener;

	public HealthMonitor(ServiceCatalog catalog) {
		this.catalog = catalog;
	}

	public void setListener(HealthChangeListener listener) {
		this.listener = listener;
	}

	public void start() {
		scheduler.scheduleAtFixedRate(this::performCheck, 1, 3, TimeUnit.SECONDS);
	}

	public void performCheck() {
		List<ServiceConfig> services = catalog.getServices();
		for (ServiceConfig config : services) {
			if (config.getHealthCheckUrl() != null && !config.getHealthCheckUrl().isBlank()) {
				long healthRevision = config.snapshotHealthRevision();
				if (healthRevision < 0) {
					continue;
				}
				boolean healthy = isHttpHealthy(config.getHealthCheckUrl());
				long detectedPid = -1;
				if (healthy) {
					if (config.getProcess() == null && config.getDefaultPort() > 0) {
						List<Long> listenerPids = ProcessKiller.findPidsByPort(config.getDefaultPort());
						detectedPid = listenerPids.isEmpty() ? -1 : listenerPids.getFirst();
					}
				}
				config.applyHealthResult(healthRevision, healthy, detectedPid);
			}
		}

		if (listener != null) {
			SwingUtilities.invokeLater(() -> listener.onHealthCheckCompleted());
		}
	}

	public boolean isHttpHealthy(String urlStr) {
		try {
			URL url = URI.create(urlStr).toURL();
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(1500);
			conn.setReadTimeout(1500);
			conn.setRequestMethod("GET");
			int code = conn.getResponseCode();
			return code >= 200 && code < 400;
		} catch (Exception e) {
			return false;
		}
	}

	public void stop() {
		scheduler.shutdownNow();
	}
}
