/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceConfig;
import com.dixon.sandbox.model.ServiceStatus;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingWorker;

public class ProcessRunner {

	private final Map<String, LogBuffer> logBuffers = new ConcurrentHashMap<>();
	private final LogBuffer unifiedLogBuffer;
	private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
	private volatile boolean shutdownRequested;

	public ProcessRunner(LogBuffer unifiedLogBuffer) {
		this.unifiedLogBuffer = unifiedLogBuffer;
	}

	public LogBuffer getOrCreateLogBuffer(ServiceConfig config) {
		return logBuffers.computeIfAbsent(config.getId(), id -> new LogBuffer(config));
	}

	public synchronized void startAll(List<ServiceConfig> services) {
		for (ServiceConfig config : services) {
			if (!config.isRunning()) {
				startProcess(config);
			}
		}
	}

	public synchronized void stopAll(List<ServiceConfig> services) {
		for (ServiceConfig config : services) {
			if (config.isRunning()) {
				stopProcess(config);
			}
		}
	}

	public void forceKillProcess(ServiceConfig config, Runnable completion) {
		LogBuffer log = getOrCreateLogBuffer(config);
		log.appendSystem("⚡ Attempting to find process ID and force kill for service '" + config.getName() + "'...");
		config.setStatus(ServiceStatus.STOPPING);

		// Build a set of PIDs that must never be killed: the sandbox JVM itself and
		// every ancestor in its process tree. This prevents keyword-based discovery
		// from accidentally matching the sandbox's own classpath string and killing
		// the sandbox application.
		Set<Long> safePids = buildSandboxPidSet();

		new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				Set<Long> pidsToKill = new LinkedHashSet<>();

				// 1. Tracked PID if spawned by sandbox
				if (config.getPid() > 0) {
					pidsToKill.add(config.getPid());
				}

				// 2. Port-based PID discovery
				if (config.getDefaultPort() > 0) {
					List<Long> portPids = ProcessKiller.findPidsByPort(config.getDefaultPort());
					pidsToKill.addAll(portPids);
				}

				// 3. Command/keyword PID discovery — working dir path is more precise than
				// service ID which may appear in the sandbox classpath
				String workDir = config.getWorkingDir() != null ? config.getWorkingDir().getAbsolutePath() : null;
				if (workDir != null && !workDir.isBlank()) {
					List<Long> keywordPids = ProcessKiller.findPidsByKeyword(workDir);
					pidsToKill.addAll(keywordPids);
				}

				// Remove any sandbox-owned PIDs to avoid self-kill
				pidsToKill.removeAll(safePids);

				if (pidsToKill.isEmpty()) {
					log.appendSystem("No active process ID found for service '" + config.getName() + "'.");
				} else {
					for (Long pid : pidsToKill) {
						log.appendSystem("Found process ID " + pid + " for service '" + config.getName()
								+ "'. Force killing process...");
						ProcessKiller.forceKillPid(pid);
					}
				}

				if (config.getProcess() != null) {
					try {
						config.getProcess().destroyForcibly();
					} catch (RuntimeException exception) {
						log.appendSystem("Unable to force-destroy the tracked process: " + exception.getMessage());
					}
				}

				config.setProcess(null);
				config.setStatus(ServiceStatus.STOPPED);
				log.appendSystem("Force kill completed for service '" + config.getName() + "'.");
				if (unifiedLogBuffer != null) {
					unifiedLogBuffer.appendSystem("[" + config.getName() + "] ⚡ Force killed.");
				}
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (Exception exception) {
					config.setStatus(ServiceStatus.ERROR);
					log.appendSystem("Force kill failed: " + exception.getMessage());
				} finally {
					completion.run();
				}
			}
		}.execute();
	}

	/**
	 * Builds the set of PIDs belonging to the sandbox JVM itself and all its
	 * ancestors. These PIDs must never be targeted by force-kill operations.
	 */
	private Set<Long> buildSandboxPidSet() {
		Set<Long> safe = new LinkedHashSet<>();
		try {
			ProcessHandle current = ProcessHandle.current();
			safe.add(current.pid());
			// Walk up the parent chain
			ProcessHandle cursor = current;
			while (cursor.parent().isPresent()) {
				cursor = cursor.parent().get();
				safe.add(cursor.pid());
			}
		} catch (RuntimeException exception) {
			if (unifiedLogBuffer != null) {
				unifiedLogBuffer
						.appendSystem("Unable to inspect the sandbox process ancestry: " + exception.getMessage());
			}
		}
		return safe;
	}

	public synchronized void startProcess(ServiceConfig config) {
		if (shutdownRequested) {
			getOrCreateLogBuffer(config).appendSystem("Start ignored because the sandbox is shutting down.");
			return;
		}
		if (config.isRunning()) {
			config.setStatus(ServiceStatus.RUNNING);
			LogBuffer log = getOrCreateLogBuffer(config);
			log.appendSystem("Service is already running (PID: " + config.getPid() + ")");
			return;
		}

		if (config.getDefaultPort() > 0) {
			List<Long> portPids = ProcessKiller.findPidsByPort(config.getDefaultPort());
			if (!portPids.isEmpty()) {
				LogBuffer log = getOrCreateLogBuffer(config);
				config.setStatus(ServiceStatus.ERROR);
				log.appendSystem("Cannot start service because port " + config.getDefaultPort()
						+ " is already used by listener PID(s): " + portPids
						+ ". Wait for health detection or stop the conflicting process.");
				return;
			}
		}

		config.setStatus(ServiceStatus.STARTING);
		LogBuffer log = getOrCreateLogBuffer(config);
		log.appendSystem("Starting service: " + config.getName() + "...");
		log.appendSystem("Working Directory: " + config.getWorkingDir().getAbsolutePath());
		log.appendSystem("Command: " + config.getCommand());

		if (!config.getWorkingDir().exists()) {
			config.setStatus(ServiceStatus.ERROR);
			log.appendSystem("Error: Working directory does not exist: " + config.getWorkingDir().getAbsolutePath());
			if (unifiedLogBuffer != null) {
				unifiedLogBuffer.appendSystem("[" + config.getName() + "] Error: Directory missing: "
						+ config.getWorkingDir().getAbsolutePath());
			}
			return;
		}

		try {
			ProcessBuilder pb = buildProcess(config);
			Process process = pb.start();
			config.setProcess(process);
			config.setStatus(ServiceStatus.RUNNING);

			log.appendSystem("Process spawned successfully. PID: " + config.getPid());

			// Capture stdout asynchronously
			ioExecutor.submit(() -> readStream(config, process.getInputStream(), false));
			// Capture stderr asynchronously
			ioExecutor.submit(() -> readStream(config, process.getErrorStream(), true));

			// Monitor process exit asynchronously
			ioExecutor.submit(() -> {
				try {
					int exitCode = process.waitFor();
					config.setProcess(null);
					config.setStatus(exitCode == 0 || shutdownRequested ? ServiceStatus.STOPPED : ServiceStatus.ERROR);
					log.appendSystem("Process exited with code: " + exitCode);
					if (exitCode != 0 && !shutdownRequested) {
						log.appendSystem("Service failed. The complete startup/runtime stacktrace is shown above.");
					}
					if (unifiedLogBuffer != null) {
						unifiedLogBuffer
								.appendSystem("[" + config.getName() + "] Stopped (Exit code: " + exitCode + ")");
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});

		} catch (Exception e) {
			config.setStatus(ServiceStatus.ERROR);
			config.setProcess(null);
			log.appendSystem("Failed to start process: " + e.getMessage());
			if (unifiedLogBuffer != null) {
				unifiedLogBuffer.appendSystem("[" + config.getName() + "] Error: " + e.getMessage());
			}
		}
	}

	private ProcessBuilder buildProcess(ServiceConfig config) {
		List<String> commandList = parseCommand(config.getCommand());
		ProcessBuilder pb = new ProcessBuilder(commandList);
		pb.directory(config.getWorkingDir());

		Map<String, String> env = pb.environment();
		String currentPath = env.getOrDefault("PATH", "");
		String home = System.getProperty("user.home", "");
		StringBuilder extraPath = new StringBuilder();
		List<String> candidatePaths = List.of("/usr/local/bin", "/opt/homebrew/bin", "/opt/homebrew/sbin",
				home + "/.nvm/versions/node/current/bin", home + "/.nvm/current/bin");

		for (String candidate : candidatePaths) {
			if (new java.io.File(candidate).exists() && !currentPath.contains(candidate)) {
				extraPath.append(candidate).append(":");
			}
		}

		java.io.File nvmNodeDir = new java.io.File(home + "/.nvm/versions/node");
		if (nvmNodeDir.exists() && nvmNodeDir.isDirectory()) {
			java.io.File[] versions = nvmNodeDir.listFiles();
			if (versions != null) {
				for (java.io.File versionDir : versions) {
					java.io.File binDir = new java.io.File(versionDir, "bin");
					if (binDir.exists() && !currentPath.contains(binDir.getAbsolutePath())) {
						extraPath.append(binDir.getAbsolutePath()).append(":");
					}
				}
			}
		}

		if (extraPath.length() > 0) {
			env.put("PATH", extraPath.toString() + currentPath);
		}
		env.putAll(config.getEnvironmentVars());

		return pb;
	}

	private List<String> parseCommand(String commandStr) {
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> result = new ArrayList<>();

		if (isWindows) {
			result.add("cmd.exe");
			result.add("/c");
			result.add(commandStr);
		} else {
			String shell = new java.io.File("/bin/zsh").exists() ? "/bin/zsh" : "/bin/sh";
			result.add(shell);
			result.add("-c");
			result.add("source ~/.zshrc 2>/dev/null || source ~/.bashrc 2>/dev/null || true; " + commandStr);
		}
		return result;
	}

	private void readStream(ServiceConfig config, java.io.InputStream stream, boolean isError) {
		LogBuffer log = getOrCreateLogBuffer(config);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				LogBuffer.LogLevel fallbackLevel = isError ? LogBuffer.LogLevel.WARN : LogBuffer.LogLevel.INFO;
				log.appendWithFallback(line, fallbackLevel);

				if (unifiedLogBuffer != null) {
					unifiedLogBuffer.appendFromService(config, line, fallbackLevel);
				}
			}
		} catch (java.io.IOException exception) {
			// Stream closed or process terminated
			log.appendSystem("Process output stream closed: " + exception.getMessage());
		}
	}

	public synchronized void stopProcess(ServiceConfig config) {
		Process process = config.getProcess();
		if (process == null || !process.isAlive()) {
			long detectedPid = config.getPid();
			if (detectedPid > 0 && ProcessHandle.of(detectedPid).map(ProcessHandle::isAlive).orElse(false)) {
				LogBuffer log = getOrCreateLogBuffer(config);
				config.setStatus(ServiceStatus.STOPPING);
				log.appendSystem("Stopping adopted process (PID: " + detectedPid + ")...");
				ProcessKiller.forceKillPid(detectedPid);
				log.appendSystem("Adopted process stopped successfully.");
			}
			config.setStatus(ServiceStatus.STOPPED);
			config.setProcess(null);
			return;
		}

		config.setStatus(ServiceStatus.STOPPING);
		LogBuffer log = getOrCreateLogBuffer(config);
		log.appendSystem("Stopping process (PID: " + config.getPid() + ")...");

		try {
			// Terminate child process tree first
			process.descendants().forEach(handle -> {
				try {
					handle.destroy();
				} catch (RuntimeException exception) {
					log.appendSystem(
							"Unable to stop descendant process " + handle.pid() + ": " + exception.getMessage());
				}
			});

			process.destroy();

			// Wait up to 3 seconds for graceful shutdown
			long startTime = System.currentTimeMillis();
			while (process.isAlive() && (System.currentTimeMillis() - startTime) < 3000) {
				Thread.sleep(100);
			}

			if (process.isAlive()) {
				log.appendSystem("Process did not stop gracefully. Forcibly killing process tree...");
				process.descendants().forEach(handle -> {
					try {
						handle.destroyForcibly();
					} catch (RuntimeException exception) {
						log.appendSystem("Unable to force-stop descendant process " + handle.pid() + ": "
								+ exception.getMessage());
					}
				});
				process.destroyForcibly();
			}

			config.setProcess(null);
			config.setStatus(ServiceStatus.STOPPED);
			log.appendSystem("Process stopped successfully.");

		} catch (Exception e) {
			log.appendSystem("Error stopping process: " + e.getMessage());
			config.setStatus(ServiceStatus.ERROR);
		}
	}

	public synchronized boolean shutdownAll(List<ServiceConfig> services) {
		shutdownRequested = true;
		Set<Long> safePids = buildSandboxPidSet();
		Map<ServiceConfig, Set<Long>> servicePids = new java.util.LinkedHashMap<>();
		Set<Long> allPids = new LinkedHashSet<>();

		for (ServiceConfig config : services) {
			if (!config.isRunning()) {
				config.setStatus(ServiceStatus.STOPPED);
				continue;
			}
			config.setStatus(ServiceStatus.STOPPING);
			Set<Long> targets = new LinkedHashSet<>();
			if (config.getPid() > 0) {
				targets.add(config.getPid());
			}
			if (config.getDefaultPort() > 0) {
				targets.addAll(ProcessKiller.findPidsByPort(config.getDefaultPort()));
			}
			targets.removeAll(safePids);
			servicePids.put(config, targets);
			allPids.addAll(targets);
			getOrCreateLogBuffer(config).appendSystem("Sandbox is closing; terminating application PID(s): " + targets);
		}

		allPids.forEach(ProcessKiller::forceKillPid);
		waitForExit(allPids, 3000);
		Set<Long> remainingPids = alivePids(allPids);
		if (!remainingPids.isEmpty()) {
			remainingPids.forEach(ProcessKiller::forceKillPid);
			waitForExit(remainingPids, 2000);
		}

		boolean allStopped = true;
		for (Map.Entry<ServiceConfig, Set<Long>> entry : servicePids.entrySet()) {
			ServiceConfig config = entry.getKey();
			Set<Long> alive = alivePids(entry.getValue());
			config.setProcess(null);
			if (alive.isEmpty()) {
				config.setStatus(ServiceStatus.STOPPED);
				getOrCreateLogBuffer(config).appendSystem("Application terminated before sandbox exit.");
			} else {
				allStopped = false;
				config.setStatus(ServiceStatus.ERROR);
				getOrCreateLogBuffer(config)
						.appendSystem("Unable to confirm application termination for PID(s): " + alive);
			}
		}
		return allStopped;
	}

	private void waitForExit(Set<Long> pids, long timeoutMillis) {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (!alivePids(pids).isEmpty() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private Set<Long> alivePids(Set<Long> pids) {
		Set<Long> alive = new LinkedHashSet<>();
		for (Long pid : pids) {
			if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
				alive.add(pid);
			}
		}
		return alive;
	}

	public void shutdown() {
		ioExecutor.shutdownNow();
		try {
			if (!ioExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS) && unifiedLogBuffer != null) {
				unifiedLogBuffer.appendSystem("Process output workers did not finish before sandbox exit.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	public void resumeAfterFailedShutdown() {
		shutdownRequested = false;
	}
}
