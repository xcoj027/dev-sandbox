/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox;

import com.dixon.sandbox.gui.MainFrame;
import com.dixon.sandbox.model.ServiceConfig;
import com.dixon.sandbox.service.ServiceCatalog;
import java.awt.Component;
import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class DevSandboxApp {
	private static final System.Logger LOGGER = System.getLogger(DevSandboxApp.class.getName());
	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DevSandboxApp.class);
	private static final String LAST_PROJECT_ROOT_KEY = "lastProjectRoot";

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to apply the light look and feel", exception);
		}

		if (args.length > 0 && "--scan".equals(args[0])) {
			File projectRoot = args.length > 1 ? new File(args[1]) : ServiceCatalog.findProjectRoot();
			printDetectedModules(projectRoot);
			return;
		}

		SwingUtilities.invokeLater(() -> {
			File projectRoot = args.length > 0 ? new File(args[0]) : findRememberedProjectRoot();
			if (projectRoot == null) {
				projectRoot = chooseProjectRoot(null, ServiceCatalog.findProjectRoot());
			}
			if (projectRoot == null) {
				return;
			}
			if (!ServiceCatalog.isSupportedProjectRoot(projectRoot)) {
				JOptionPane.showMessageDialog(null,
						"Choose a Gradle or Maven project root containing a build or settings file.",
						"Unsupported project root", JOptionPane.ERROR_MESSAGE);
				return;
			}
			rememberProjectRoot(projectRoot);
			ServiceCatalog catalog = new ServiceCatalog(projectRoot);
			MainFrame mainFrame = new MainFrame(catalog);
			mainFrame.setVisible(true);
		});
	}

	public static File chooseProjectRoot(Component parent, File suggestedRoot) {
		if (suggestedRoot == null) {
			suggestedRoot = ServiceCatalog.findProjectRoot();
		}
		File initialDirectory = suggestedRoot.getParentFile() != null ? suggestedRoot.getParentFile() : suggestedRoot;
		JFileChooser chooser = new JFileChooser(initialDirectory);
		chooser.setDialogTitle("Choose Gradle or Maven project root");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setSelectedFile(suggestedRoot);
		while (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
			File selectedRoot = chooser.getSelectedFile();
			if (ServiceCatalog.isSupportedProjectRoot(selectedRoot)) {
				return selectedRoot;
			}
			JOptionPane.showMessageDialog(parent,
					"Choose a Gradle or Maven project root containing a build or settings file.",
					"Unsupported project root", JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}

	private static File findRememberedProjectRoot() {
		try {
			String savedPath = PREFERENCES.get(LAST_PROJECT_ROOT_KEY, "");
			if (!savedPath.isBlank()) {
				File savedRoot = new File(savedPath);
				if (ServiceCatalog.isSupportedProjectRoot(savedRoot)) {
					return savedRoot;
				}
			}
		} catch (SecurityException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to read the last project root preference", exception);
		}
		return null;
	}

	public static void rememberProjectRoot(File projectRoot) {
		try {
			String normalizedPath = projectRoot.toPath().toAbsolutePath().normalize().toString();
			PREFERENCES.put(LAST_PROJECT_ROOT_KEY, normalizedPath);
			PREFERENCES.flush();
		} catch (BackingStoreException | SecurityException exception) {
			LOGGER.log(System.Logger.Level.WARNING, "Unable to remember the selected project root", exception);
		}
	}

	private static void printDetectedModules(File projectRoot) {
		if (!ServiceCatalog.isSupportedProjectRoot(projectRoot)) {
			System.err.println("Unsupported project root: " + projectRoot.getAbsolutePath());
			return;
		}
		ServiceCatalog catalog = new ServiceCatalog(projectRoot);
		System.out.println("Project root: " + catalog.getRootDir().getAbsolutePath());
		for (ServiceConfig service : catalog.getServices()) {
			System.out.println(service.getCategory() + "\t" + service.getName() + "\t" + service.getDefaultPort() + "\t"
					+ service.getCommand());
		}
		System.out.println("Detected modules: " + catalog.getServices().size());
	}
}
