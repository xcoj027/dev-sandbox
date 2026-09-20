/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import com.dixon.sandbox.DevSandboxApp;
import com.dixon.sandbox.model.ServiceConfig;
import com.dixon.sandbox.model.ServiceStatus;
import com.dixon.sandbox.service.HealthMonitor;
import com.dixon.sandbox.service.LogBuffer;
import com.dixon.sandbox.service.ProcessRunner;
import com.dixon.sandbox.service.ServiceCatalog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;

public class MainFrame extends JFrame
		implements
			TopToolbar.ToolbarActionListener,
			ServiceListPanel.ServiceItemActionListener,
			HealthMonitor.HealthChangeListener {

	private final ServiceCatalog catalog;
	private final LogBuffer unifiedLogBuffer;
	private final ProcessRunner processRunner;
	private final HealthMonitor healthMonitor;

	private final TopToolbar topToolbar;
	private final ServiceListPanel serviceListPanel;
	private final ConsolePanel consolePanel;
	private boolean batchActionActive;
	private boolean shutdownActive;

	public MainFrame(ServiceCatalog catalog) {
		super(catalog.getRootDir().getName() + " — Dev Sandbox · TNQ");
		this.catalog = catalog;

		this.unifiedLogBuffer = new LogBuffer(null);
		this.processRunner = new ProcessRunner(unifiedLogBuffer);
		this.healthMonitor = new HealthMonitor(catalog);

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setSize(1400, 850);
		setMinimumSize(new Dimension(1000, 600));
		setLocationRelativeTo(null);

		setLayout(new BorderLayout());

		// Header Toolbar
		topToolbar = new TopToolbar(catalog.getRootDir().getName(), catalog.getServices().size());
		topToolbar.setActionListener(this);
		add(topToolbar, BorderLayout.NORTH);

		// Left: Service List, Right: Console View
		serviceListPanel = new ServiceListPanel(catalog);
		serviceListPanel.setListener(this);

		consolePanel = new ConsolePanel(unifiedLogBuffer, processRunner, catalog.getServices());

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, serviceListPanel, consolePanel);
		splitPane.setDividerLocation(500);
		splitPane.setDividerSize(6);
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(true);
		splitPane.setBorder(null);

		add(splitPane, BorderLayout.CENTER);

		// Health Monitor
		healthMonitor.setListener(this);
		healthMonitor.start();

		// Cleanup processes on window close
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdownSandbox();
			}
		});

		unifiedLogBuffer.appendSystem("Dev Sandbox initialized successfully.");
		unifiedLogBuffer.appendSystem("Project Root: " + catalog.getRootDir().getAbsolutePath());
		unifiedLogBuffer.appendSystem("Detected runnable modules: " + catalog.getServices().size());
	}

	@Override
	public void onStart(ServiceConfig config) {
		onSelect(config);
		LifecycleOperation operation = beginIndividualOperation(config, ServiceStatus.STARTING);
		if (operation != null) {
			runLifecycleAction(() -> processRunner.startProcess(config), List.of(operation), false);
		}
	}

	@Override
	public void onStop(ServiceConfig config) {
		onSelect(config);
		LifecycleOperation operation = beginIndividualOperation(config, ServiceStatus.STOPPING);
		if (operation != null) {
			runLifecycleAction(() -> processRunner.stopProcess(config), List.of(operation), false);
		}
	}

	@Override
	public void onForceKill(ServiceConfig config) {
		onSelect(config);
		LifecycleOperation operation = beginIndividualOperation(config, ServiceStatus.STOPPING);
		if (operation != null) {
			processRunner.forceKillProcess(config, () -> finishLifecycleAction(List.of(operation), false));
		}
	}

	@Override
	public void onSelect(ServiceConfig config) {
		serviceListPanel.setSelectedServiceId(config.getId());
		consolePanel.selectTab(config.getId());
	}

	@Override
	public void onEditConfig(ServiceConfig config) {
		onSelect(config);
		ServiceConfigDialog dialog = new ServiceConfigDialog(this, config);
		dialog.setVisible(true);
		if (dialog.isSaved()) {
			serviceListPanel.refreshStatus();
		}
	}

	@Override
	public void onStartAll() {
		unifiedLogBuffer.appendSystem("Batch action: Starting all services...");
		List<LifecycleOperation> operations = beginBatchOperation(ServiceStatus.STARTING, false);
		if (operations == null) {
			return;
		}
		runLifecycleAction(() -> processRunner.startAll(catalog.getServices()), operations, true);
	}

	@Override
	public void onStopAll() {
		unifiedLogBuffer.appendSystem("Batch action: Stopping all services...");
		List<LifecycleOperation> operations = beginBatchOperation(ServiceStatus.STOPPING, true);
		if (operations == null) {
			return;
		}
		runLifecycleAction(() -> processRunner.stopAll(catalog.getServices()), operations, true);
	}

	@Override
	public void onRestartAll() {
		unifiedLogBuffer.appendSystem("Batch action: Restarting all services...");
		List<LifecycleOperation> operations = beginRestartBatchOperation();
		if (operations == null) {
			return;
		}
		runLifecycleAction(() -> {
			processRunner.stopAll(catalog.getServices());
			processRunner.startAll(catalog.getServices());
		}, operations, true);
	}

	@Override
	public void onClearAllLogs() {
		consolePanel.clearAllLogs();
	}

	@Override
	public void onChangeProjectRoot() {
		if (shutdownActive || batchActionActive
				|| catalog.getServices().stream().anyMatch(ServiceConfig::isLifecycleOperationActive)) {
			JOptionPane.showMessageDialog(this,
					"Wait for the current application operation to finish before changing the project root.",
					"Application operation in progress", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		File selectedRoot = DevSandboxApp.chooseProjectRoot(this, catalog.getRootDir());
		if (selectedRoot == null) {
			return;
		}
		File normalizedRoot = selectedRoot.toPath().toAbsolutePath().normalize().toFile();
		if (normalizedRoot.equals(catalog.getRootDir())) {
			return;
		}
		switchProjectRoot(normalizedRoot);
	}

	private LifecycleOperation beginIndividualOperation(ServiceConfig config, ServiceStatus pendingStatus) {
		if (batchActionActive) {
			unifiedLogBuffer.appendSystem("Ignored " + pendingStatus.getLabel() + " for " + config.getName()
					+ " because a batch action is active.");
			return null;
		}
		long revision = config.tryBeginLifecycleOperation(pendingStatus);
		if (revision < 0) {
			return null;
		}
		serviceListPanel.refreshStatus();
		return new LifecycleOperation(config, revision);
	}

	private List<LifecycleOperation> beginBatchOperation(ServiceStatus pendingStatus, boolean runningOnly) {
		if (!canBeginBatchOperation()) {
			return null;
		}
		batchActionActive = true;
		List<LifecycleOperation> operations = new ArrayList<>();
		for (ServiceConfig service : catalog.getServices()) {
			if (runningOnly != service.isRunning()) {
				continue;
			}
			long revision = service.tryBeginLifecycleOperation(pendingStatus);
			operations.add(new LifecycleOperation(service, revision));
		}
		serviceListPanel.refreshStatus();
		return operations;
	}

	private List<LifecycleOperation> beginRestartBatchOperation() {
		if (!canBeginBatchOperation()) {
			return null;
		}
		batchActionActive = true;
		List<LifecycleOperation> operations = new ArrayList<>();
		for (ServiceConfig service : catalog.getServices()) {
			ServiceStatus pendingStatus = service.isRunning() ? ServiceStatus.STOPPING : ServiceStatus.STARTING;
			long revision = service.tryBeginLifecycleOperation(pendingStatus);
			operations.add(new LifecycleOperation(service, revision));
		}
		serviceListPanel.refreshStatus();
		return operations;
	}

	private boolean canBeginBatchOperation() {
		if (!batchActionActive && catalog.getServices().stream().noneMatch(ServiceConfig::isLifecycleOperationActive)) {
			return true;
		}
		unifiedLogBuffer.appendSystem("Ignored batch action because another lifecycle action is active.");
		topToolbar.finishBatchAction();
		return false;
	}

	private void runLifecycleAction(Runnable action, List<LifecycleOperation> operations, boolean batchAction) {
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				action.run();
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					markOperationsFailed(operations, exception);
				} catch (ExecutionException exception) {
					markOperationsFailed(operations, exception.getCause());
				}
				finishLifecycleAction(operations, batchAction);
			}
		}.execute();
	}

	private void finishLifecycleAction(List<LifecycleOperation> operations, boolean batchAction) {
		for (LifecycleOperation operation : operations) {
			operation.config().finishLifecycleOperation(operation.revision());
		}
		if (batchAction) {
			batchActionActive = false;
			topToolbar.finishBatchAction();
		}
		serviceListPanel.refreshStatus();
	}

	private void markOperationsFailed(List<LifecycleOperation> operations, Throwable failure) {
		for (LifecycleOperation operation : operations) {
			operation.config().setStatus(ServiceStatus.ERROR);
		}
		unifiedLogBuffer.appendSystem("Lifecycle action failed: " + failure.getMessage());
	}

	private record LifecycleOperation(ServiceConfig config, long revision) {
	}

	@Override
	public void onHealthCheckCompleted() {
		serviceListPanel.refreshStatus();
	}

	private void shutdownSandbox() {
		if (shutdownActive) {
			return;
		}
		shutdownActive = true;
		unifiedLogBuffer.appendSystem("Shutting down Dev Sandbox...");
		setTitle("Stopping applications — Dev Sandbox · TNQ");
		setEnabled(false);

		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() {
				return processRunner.shutdownAll(catalog.getServices());
			}

			@Override
			protected void done() {
				try {
					if (!get()) {
						handleIncompleteShutdown();
						return;
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					handleIncompleteShutdown();
					return;
				} catch (ExecutionException exception) {
					unifiedLogBuffer.appendSystem("Sandbox shutdown failed: " + exception.getCause().getMessage());
					handleIncompleteShutdown();
					return;
				}
				healthMonitor.stop();
				processRunner.shutdown();
				dispose();
				System.exit(0);
			}
		}.execute();
	}

	private void switchProjectRoot(File projectRoot) {
		shutdownActive = true;
		unifiedLogBuffer.appendSystem("Changing project root to: " + projectRoot.getAbsolutePath());
		setTitle("Changing project root — Dev Sandbox · TNQ");
		setEnabled(false);

		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() {
				return processRunner.shutdownAll(catalog.getServices());
			}

			@Override
			protected void done() {
				try {
					if (!get()) {
						handleIncompleteProjectSwitch();
						return;
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					handleIncompleteProjectSwitch();
					return;
				} catch (ExecutionException exception) {
					unifiedLogBuffer.appendSystem("Project root change failed: " + exception.getCause().getMessage());
					handleIncompleteProjectSwitch();
					return;
				}

				healthMonitor.stop();
				processRunner.shutdown();
				DevSandboxApp.rememberProjectRoot(projectRoot);
				dispose();
				MainFrame replacementFrame = new MainFrame(new ServiceCatalog(projectRoot));
				replacementFrame.setVisible(true);
			}
		}.execute();
	}

	private void handleIncompleteProjectSwitch() {
		processRunner.resumeAfterFailedShutdown();
		shutdownActive = false;
		setTitle(catalog.getRootDir().getName() + " — Dev Sandbox · TNQ");
		setEnabled(true);
		JOptionPane.showMessageDialog(this,
				"One or more applications are still running. The project root was not changed.",
				"Applications still running", JOptionPane.ERROR_MESSAGE);
	}

	private void handleIncompleteShutdown() {
		processRunner.resumeAfterFailedShutdown();
		shutdownActive = false;
		setTitle(catalog.getRootDir().getName() + " — Dev Sandbox · TNQ");
		setEnabled(true);
		JOptionPane.showMessageDialog(this,
				"One or more applications are still running. The sandbox will remain open; close the window to retry.",
				"Applications still running", JOptionPane.ERROR_MESSAGE);
	}
}
