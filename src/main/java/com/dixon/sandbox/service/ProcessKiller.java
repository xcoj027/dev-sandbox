/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProcessKiller {
	private static final System.Logger LOGGER = System.getLogger(ProcessKiller.class.getName());

	public static List<Long> findPidsByPort(int port) {
		List<Long> pids = new ArrayList<>();
		if (port <= 0) {
			return pids;
		}

		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
		try {
			ProcessBuilder pb;
			if (isWindows) {
				String netstatCommand = "netstat -ano -p tcp | findstr LISTENING | findstr :" + port;
				pb = new ProcessBuilder("cmd.exe", "/c", netstatCommand);
			} else {
				pb = new ProcessBuilder("lsof", "-nP", "-tiTCP:" + port, "-sTCP:LISTEN");
			}
			Process process = pb.start();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) {
						continue;
					}
					if (isWindows) {
						String[] parts = line.split("\\s+");
						if (parts.length > 4) {
							try {
								pids.add(Long.parseLong(parts[parts.length - 1]));
							} catch (NumberFormatException exception) {
								LOGGER.log(System.Logger.Level.DEBUG, "Ignoring an unparseable netstat process ID",
										exception);
							}
						}
					} else {
						try {
							pids.add(Long.parseLong(line));
						} catch (NumberFormatException exception) {
							LOGGER.log(System.Logger.Level.DEBUG, "Ignoring an unparseable lsof process ID", exception);
						}
					}
				}
			}
			process.waitFor();
		} catch (java.io.IOException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to discover the process using port " + port, exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.log(System.Logger.Level.WARNING, "Interrupted while discovering the process using port " + port,
					exception);
		}
		return pids;
	}

	public static List<Long> findPidsByKeyword(String keyword) {
		List<Long> pids = new ArrayList<>();
		if (keyword == null || keyword.isBlank()) {
			return pids;
		}

		long currentPid = ProcessHandle.current().pid();
		String lowerKeyword = keyword.toLowerCase();

		try {
			ProcessHandle.allProcesses().forEach(handle -> {
				if (handle.pid() == currentPid) {
					return;
				}
				handle.info().commandLine().ifPresent(cmdLine -> {
					if (cmdLine.toLowerCase().contains(lowerKeyword)) {
						pids.add(handle.pid());
					}
				});
			});
		} catch (RuntimeException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to inspect running processes for keyword " + keyword,
					exception);
		}

		return pids;
	}

	public static boolean forceKillPort(int port) {
		List<Long> pids = findPidsByPort(port);
		if (pids.isEmpty()) {
			return false;
		}

		for (Long pid : pids) {
			forceKillPid(pid);
		}
		return true;
	}

	public static void forceKillPid(long pid) {
		if (pid <= 0) {
			return;
		}

		// Try Java 9+ ProcessHandle first
		try {
			ProcessHandle.of(pid).ifPresent(handle -> {
				handle.descendants().forEach(h -> {
					try {
						h.destroyForcibly();
					} catch (RuntimeException exception) {
						LOGGER.log(System.Logger.Level.DEBUG, "Unable to terminate descendant process " + h.pid(),
								exception);
					}
				});
				try {
					handle.destroyForcibly();
				} catch (RuntimeException exception) {
					LOGGER.log(System.Logger.Level.DEBUG, "Unable to terminate process " + pid, exception);
				}
			});
		} catch (RuntimeException exception) {
			LOGGER.log(System.Logger.Level.DEBUG, "Java process termination failed for " + pid, exception);
		}

		// OS native command fallback (kill -9 or taskkill /F)
		try {
			boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
			ProcessBuilder pb = isWindows
					? new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid), "/T")
					: new ProcessBuilder("kill", "-9", String.valueOf(pid));
			Process process = pb.start();
			process.waitFor();
		} catch (java.io.IOException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Native process termination failed for " + pid, exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.log(System.Logger.Level.WARNING, "Interrupted while terminating process " + pid, exception);
		}
	}
}
