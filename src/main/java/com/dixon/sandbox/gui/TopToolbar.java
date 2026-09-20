/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public class TopToolbar extends JPanel {

	public interface ToolbarActionListener {

		void onStartAll();

		void onStopAll();

		void onRestartAll();

		void onClearAllLogs();

		void onChangeProjectRoot();
	}

	private final JButton startAllBtn;
	private final JButton stopAllBtn;
	private final JButton restartAllBtn;
	private final JButton clearLogsBtn;
	private final JButton changeRootBtn;
	private ToolbarActionListener actionListener;

	public TopToolbar(String projectName, int moduleCount) {
		setLayout(new BorderLayout(15, 0));
		setBackground(DarkTheme.BG_HEADER);
		setBorder(
				BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_COLOR),
						BorderFactory.createEmptyBorder(10, 16, 10, 16)));

		// Left: App Logo & Title with TNQ branding
		JPanel titlePanel = new JPanel(new BorderLayout(5, 2));
		titlePanel.setOpaque(false);

		JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		headerRow.setOpaque(false);

		JLabel titleLabel = new JLabel("⚡ DEV SANDBOX");
		titleLabel.setFont(DarkTheme.FONT_TITLE);
		titleLabel.setForeground(DarkTheme.TEXT_PRIMARY);
		headerRow.add(titleLabel);

		JLabel brandBadge = new JLabel("TNQ");
		brandBadge.setFont(DarkTheme.FONT_BOLD);
		brandBadge.setForeground(DarkTheme.TEXT_PRIMARY);
		brandBadge.setBackground(DarkTheme.BG_INPUT);
		brandBadge.setOpaque(true);
		brandBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		brandBadge.setToolTipText("TNQ MEDIA");
		headerRow.add(brandBadge);

		JLabel subtitleLabel = new JLabel("Java & Web Application Control Center · TNQ MEDIA");
		subtitleLabel.setFont(DarkTheme.FONT_MAIN);
		subtitleLabel.setForeground(DarkTheme.TEXT_MUTED);

		titlePanel.add(headerRow, BorderLayout.NORTH);
		titlePanel.add(subtitleLabel, BorderLayout.SOUTH);

		add(titlePanel, BorderLayout.WEST);

		// Center: selected project summary
		JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		centerPanel.setOpaque(false);

		JLabel projectLabel = new JLabel(projectName + " · " + moduleCount + " modules");
		projectLabel.setFont(DarkTheme.FONT_BOLD);
		projectLabel.setOpaque(true);
		projectLabel.setBackground(DarkTheme.BG_INPUT);
		projectLabel.setForeground(DarkTheme.TEXT_SECONDARY);
		projectLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

		centerPanel.add(projectLabel);

		changeRootBtn = DarkTheme.createStyledButton("📁 Change Root", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
		changeRootBtn.setToolTipText("Choose a different Gradle or Maven project root");
		changeRootBtn.addActionListener(e -> {
			if (actionListener != null) {
				actionListener.onChangeProjectRoot();
			}
		});
		centerPanel.add(changeRootBtn);
		add(centerPanel, BorderLayout.CENTER);

		// Right: Batch Action Buttons
		JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actionsPanel.setOpaque(false);

		startAllBtn = DarkTheme.createStyledButton("▶ Start All", DarkTheme.ACCENT_GREEN, DarkTheme.BG_HEADER);
		DarkTheme.reserveButtonBusyWidth(startAllBtn, "Starting all");
		startAllBtn.setToolTipText("Start all services and frontend applications");
		startAllBtn.addActionListener(e -> {
			if (actionListener != null) {
				beginBatchAction(startAllBtn, "Starting all");
				actionListener.onStartAll();
			}
		});

		stopAllBtn = DarkTheme.createStyledButton("⏹ Stop All", DarkTheme.ACCENT_RED, DarkTheme.BG_HEADER);
		DarkTheme.reserveButtonBusyWidth(stopAllBtn, "Stopping all");
		stopAllBtn.setToolTipText("Gracefully stop all running processes");
		stopAllBtn.addActionListener(e -> {
			if (actionListener != null) {
				beginBatchAction(stopAllBtn, "Stopping all");
				actionListener.onStopAll();
			}
		});

		restartAllBtn = DarkTheme.createStyledButton("🔄 Restart All", DarkTheme.ACCENT_PURPLE, DarkTheme.BG_HEADER);
		DarkTheme.reserveButtonBusyWidth(restartAllBtn, "Restarting all");
		restartAllBtn.setToolTipText("Restart all services and frontend applications");
		restartAllBtn.addActionListener(e -> {
			if (actionListener != null) {
				beginBatchAction(restartAllBtn, "Restarting all");
				actionListener.onRestartAll();
			}
		});

		clearLogsBtn = DarkTheme.createStyledButton("🧹 Clear Logs", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
		DarkTheme.reserveButtonBusyWidth(clearLogsBtn, "Clearing");
		clearLogsBtn.setToolTipText("Clear all service console logs");
		clearLogsBtn.addActionListener(e -> {
			if (actionListener != null) {
				DarkTheme.setButtonBusy(clearLogsBtn, true, "Clearing");
				actionListener.onClearAllLogs();
				Timer restoreTimer = new Timer(450, event -> DarkTheme.setButtonBusy(clearLogsBtn, false, ""));
				restoreTimer.setRepeats(false);
				restoreTimer.start();
			}
		});

		actionsPanel.add(startAllBtn);
		actionsPanel.add(stopAllBtn);
		actionsPanel.add(restartAllBtn);
		actionsPanel.add(clearLogsBtn);

		add(actionsPanel, BorderLayout.EAST);

		setPreferredSize(new Dimension(1000, 60));
	}

	public void setActionListener(ToolbarActionListener actionListener) {
		this.actionListener = actionListener;
	}

	public void finishBatchAction() {
		DarkTheme.setButtonBusy(startAllBtn, false, "");
		DarkTheme.setButtonBusy(stopAllBtn, false, "");
		DarkTheme.setButtonBusy(restartAllBtn, false, "");
		startAllBtn.setEnabled(true);
		stopAllBtn.setEnabled(true);
		restartAllBtn.setEnabled(true);
		clearLogsBtn.setEnabled(true);
		changeRootBtn.setEnabled(true);
	}

	private void beginBatchAction(JButton activeButton, String busyText) {
		startAllBtn.setEnabled(false);
		stopAllBtn.setEnabled(false);
		restartAllBtn.setEnabled(false);
		clearLogsBtn.setEnabled(false);
		changeRootBtn.setEnabled(false);
		DarkTheme.setButtonBusy(activeButton, true, busyText);
	}

}
