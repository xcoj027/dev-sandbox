/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.service;

import com.dixon.sandbox.model.ServiceConfig;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class ServiceCatalog {

	private final File rootDir;
	private final List<ServiceConfig> services;

	public ServiceCatalog(File rootDir) {
		this.rootDir = rootDir.toPath().toAbsolutePath().normalize().toFile();
		this.services = new ProjectModuleScanner(this.rootDir).scan();
	}

	public static File findProjectRoot() {
		File current = new File(System.getProperty("user.dir"));
		while (current != null) {
			if (isSupportedProjectRoot(current)) {
				return current;
			}
			current = current.getParentFile();
		}
		return new File(System.getProperty("user.dir"));
	}

	public static boolean isSupportedProjectRoot(File directory) {
		return directory != null && directory.isDirectory() && (new File(directory, "settings.gradle").isFile()
				|| new File(directory, "settings.gradle.kts").isFile() || new File(directory, "build.gradle").isFile()
				|| new File(directory, "build.gradle.kts").isFile() || new File(directory, "pom.xml").isFile());
	}

	public List<ServiceConfig> getServices() {
		return Collections.unmodifiableList(services);
	}

	public ServiceConfig getServiceById(String id) {
		for (ServiceConfig config : services) {
			if (config.getId().equals(id)) {
				return config;
			}
		}
		return null;
	}

	public File getRootDir() {
		return rootDir;
	}
}
