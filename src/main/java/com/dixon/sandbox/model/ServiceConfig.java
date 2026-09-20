/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.model;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServiceConfig {

	private final String id;
	private final String name;
	private final String description;
	private final ServiceCategory category;
	private final File workingDir;
	private String command;
	private int defaultPort;
	private String healthCheckUrl;
	private final Map<String, String> environmentVars = new LinkedHashMap<>();

	private volatile ServiceStatus status = ServiceStatus.STOPPED;
	private volatile ServiceStatus pendingStatus;
	private volatile Process process;
	private volatile long pid = -1;
	private long lifecycleRevision;
	private boolean lifecycleOperationActive;

	public ServiceConfig(String id, String name, String description, ServiceCategory category, File workingDir,
			String command, int defaultPort, String healthCheckUrl) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.workingDir = workingDir;
		this.command = command;
		this.defaultPort = defaultPort;
		this.healthCheckUrl = healthCheckUrl;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public ServiceCategory getCategory() {
		return category;
	}

	public File getWorkingDir() {
		return workingDir;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public int getDefaultPort() {
		return defaultPort;
	}

	public void setDefaultPort(int defaultPort) {
		this.defaultPort = defaultPort;
	}

	public String getHealthCheckUrl() {
		return healthCheckUrl;
	}

	public void setHealthCheckUrl(String healthCheckUrl) {
		this.healthCheckUrl = healthCheckUrl;
	}

	public Map<String, String> getEnvironmentVars() {
		return Collections.unmodifiableMap(environmentVars);
	}

	public void addEnvVar(String key, String value) {
		this.environmentVars.put(key, value);
	}

	public void setEnvVars(Map<String, String> vars) {
		this.environmentVars.clear();
		if (vars != null) {
			this.environmentVars.putAll(vars);
		}
	}

	public ServiceStatus getStatus() {
		return status;
	}

	public void setStatus(ServiceStatus status) {
		this.status = status;
	}

	public synchronized long tryBeginLifecycleOperation(ServiceStatus newPendingStatus) {
		if (lifecycleOperationActive) {
			return -1;
		}
		lifecycleRevision++;
		lifecycleOperationActive = true;
		pendingStatus = newPendingStatus;
		status = newPendingStatus;
		return lifecycleRevision;
	}

	public synchronized void finishLifecycleOperation(long expectedRevision) {
		if (!lifecycleOperationActive || lifecycleRevision != expectedRevision) {
			return;
		}
		lifecycleOperationActive = false;
		pendingStatus = null;
		lifecycleRevision++;
	}

	public synchronized boolean isLifecycleOperationActive() {
		return lifecycleOperationActive;
	}

	public synchronized ServiceStatus getDisplayStatus() {
		return lifecycleOperationActive ? pendingStatus : status;
	}

	public synchronized long snapshotHealthRevision() {
		return lifecycleOperationActive ? -1 : lifecycleRevision;
	}

	public synchronized boolean applyHealthResult(long expectedRevision, boolean healthy, long healthyPid) {
		if (lifecycleOperationActive || lifecycleRevision != expectedRevision) {
			return false;
		}
		if (healthy) {
			if (process == null) {
				setDetectedPid(healthyPid);
			}
			status = ServiceStatus.RUNNING;
		} else if (!isRunning()) {
			setDetectedPid(-1);
			status = ServiceStatus.STOPPED;
		}
		return true;
	}

	public Process getProcess() {
		return process;
	}

	public void setProcess(Process process) {
		this.process = process;
		if (process != null) {
			try {
				this.pid = process.pid();
			} catch (UnsupportedOperationException e) {
				this.pid = -1;
			}
		} else {
			this.pid = -1;
		}
	}

	public void setDetectedPid(long pid) {
		if (process == null) {
			this.pid = pid > 0 ? pid : -1;
		}
	}

	public long getPid() {
		return pid;
	}

	public boolean isRunning() {
		if (process != null && process.isAlive()) {
			return true;
		}
		return pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
	}
}
