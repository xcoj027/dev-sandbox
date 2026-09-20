/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import com.dixon.sandbox.model.ServiceConfig;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class ServiceConfigDialog extends JDialog {

	private final ServiceConfig config;
	private final JTextField commandField;
	private final JTextField portField;
	private final JTextField healthUrlField;
	private final DefaultTableModel envTableModel;
	private boolean saved = false;

	public ServiceConfigDialog(Frame owner, ServiceConfig config) {
		super(owner, "Configure Service: " + config.getName(), true);
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(DarkTheme.BG_DARK);
		setSize(860, 480);
		setLocationRelativeTo(owner);

		JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setBackground(DarkTheme.BG_CARD);
		formPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Service ID
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		JLabel idLabel = new JLabel("Service ID:");
		idLabel.setFont(DarkTheme.FONT_BOLD);
		idLabel.setForeground(DarkTheme.TEXT_PRIMARY);
		formPanel.add(idLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		JLabel idValLabel = new JLabel(config.getId() + " (" + config.getCategory().getDisplayName() + ")");
		idValLabel.setFont(DarkTheme.FONT_MAIN);
		idValLabel.setForeground(DarkTheme.TEXT_MUTED);
		formPanel.add(idValLabel, gbc);

		// Execution Command
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0;
		JLabel cmdLabel = new JLabel("Start Command:");
		cmdLabel.setFont(DarkTheme.FONT_BOLD);
		cmdLabel.setForeground(DarkTheme.TEXT_PRIMARY);
		formPanel.add(cmdLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		commandField = new JTextField(config.getCommand());
		commandField.setBackground(DarkTheme.BG_INPUT);
		commandField.setForeground(DarkTheme.TEXT_PRIMARY);
		commandField.setCaretColor(DarkTheme.TEXT_PRIMARY);
		commandField.setFont(DarkTheme.FONT_MONO);
		formPanel.add(commandField, gbc);

		// Port
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 0;
		JLabel portLabel = new JLabel("Default Port:");
		portLabel.setFont(DarkTheme.FONT_BOLD);
		portLabel.setForeground(DarkTheme.TEXT_PRIMARY);
		formPanel.add(portLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		portField = new JTextField(String.valueOf(config.getDefaultPort()));
		portField.setBackground(DarkTheme.BG_INPUT);
		portField.setForeground(DarkTheme.TEXT_PRIMARY);
		portField.setFont(DarkTheme.FONT_MAIN);
		formPanel.add(portField, gbc);

		// Health URL
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.weightx = 0;
		JLabel healthLabel = new JLabel("Health Check URL:");
		healthLabel.setFont(DarkTheme.FONT_BOLD);
		healthLabel.setForeground(DarkTheme.TEXT_PRIMARY);
		formPanel.add(healthLabel, gbc);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		healthUrlField = new JTextField(
				config.getHealthCheckUrl() != null ? config.getHealthCheckUrl() : "http://127.0.0.1:");
		healthUrlField.setBackground(DarkTheme.BG_INPUT);
		healthUrlField.setForeground(DarkTheme.TEXT_PRIMARY);
		healthUrlField.setFont(DarkTheme.FONT_MAIN);
		formPanel.add(healthUrlField, gbc);

		// Environment Variables Table
		gbc.gridx = 0;
		gbc.gridy = 4;
		gbc.gridwidth = 2;
		JLabel envTitleLabel = new JLabel("Environment Variables:");
		envTitleLabel.setFont(DarkTheme.FONT_BOLD);
		envTitleLabel.setForeground(DarkTheme.ACCENT_PRIMARY);
		formPanel.add(envTitleLabel, gbc);

		envTableModel = new DefaultTableModel(new Object[]{"KEY", "VALUE"}, 0);
		for (Map.Entry<String, String> entry : config.getEnvironmentVars().entrySet()) {
			envTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
		}

		JTable envTable = new JTable(envTableModel);
		envTable.setBackground(DarkTheme.BG_DARK);
		envTable.setForeground(DarkTheme.TEXT_PRIMARY);
		envTable.setGridColor(DarkTheme.BORDER_COLOR);
		envTable.setFont(DarkTheme.FONT_MONO);

		JScrollPane tableScroll = new JScrollPane(envTable);
		gbc.gridy = 5;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		formPanel.add(tableScroll, gbc);

		// Add/Remove Env Var buttons
		JPanel envBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		envBtnPanel.setOpaque(false);

		JButton addRowBtn = DarkTheme.createStyledButton("+ Add Variable", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
		addRowBtn.addActionListener(e -> envTableModel.addRow(new Object[]{"NEW_KEY", "VALUE"}));

		JButton removeRowBtn = DarkTheme.createStyledButton("- Remove Variable", DarkTheme.BG_INPUT,
				DarkTheme.TEXT_PRIMARY);
		removeRowBtn.addActionListener(e -> {
			int row = envTable.getSelectedRow();
			if (row >= 0) {
				envTableModel.removeRow(row);
			}
		});

		envBtnPanel.add(addRowBtn);
		envBtnPanel.add(removeRowBtn);
		gbc.gridy = 6;
		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		formPanel.add(envBtnPanel, gbc);

		add(formPanel, BorderLayout.CENTER);

		// Bottom Dialog Actions
		JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
		actionPanel.setBackground(DarkTheme.BG_HEADER);

		JButton saveBtn = DarkTheme.createStyledButton("Save Configuration", DarkTheme.ACCENT_GREEN,
				DarkTheme.BG_HEADER);
		saveBtn.addActionListener(e -> saveAndClose());

		JButton cancelBtn = DarkTheme.createStyledButton("Cancel", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
		cancelBtn.addActionListener(e -> dispose());

		actionPanel.add(saveBtn);
		actionPanel.add(cancelBtn);

		add(actionPanel, BorderLayout.SOUTH);
	}

	private void saveAndClose() {
		config.setCommand(commandField.getText().trim());
		try {
			config.setDefaultPort(Integer.parseInt(portField.getText().trim()));
		} catch (NumberFormatException exception) {
			javax.swing.JOptionPane.showMessageDialog(this, "Port must be a whole number.", "Invalid port",
					javax.swing.JOptionPane.ERROR_MESSAGE);
			portField.requestFocusInWindow();
			return;
		}
		config.setHealthCheckUrl(healthUrlField.getText().trim());

		Map<String, String> newEnv = new LinkedHashMap<>();
		for (int i = 0; i < envTableModel.getRowCount(); i++) {
			String key = (String) envTableModel.getValueAt(i, 0);
			String val = (String) envTableModel.getValueAt(i, 1);
			if (key != null && !key.isBlank()) {
				newEnv.put(key.trim(), val != null ? val.trim() : "");
			}
		}
		config.setEnvVars(newEnv);

		saved = true;
		dispose();
	}

	public boolean isSaved() {
		return saved;
	}
}
