/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import com.dixon.sandbox.model.ServiceConfig;
import com.dixon.sandbox.service.LogBuffer;
import com.dixon.sandbox.service.ProcessRunner;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

public class ConsolePanel extends JPanel {
	private static final System.Logger LOGGER = System.getLogger(ConsolePanel.class.getName());

	private final JTabbedPane tabbedPane;
	private final LogBuffer unifiedLogBuffer;
	private final ProcessRunner processRunner;
	private final Map<String, ConsoleTab> tabs = new HashMap<>();

	public ConsolePanel(LogBuffer unifiedLogBuffer, ProcessRunner processRunner, List<ServiceConfig> services) {
		this.unifiedLogBuffer = unifiedLogBuffer;
		this.processRunner = processRunner;

		setLayout(new BorderLayout());
		setBackground(DarkTheme.BG_DARK);
		setPreferredSize(new Dimension(800, 700));
		setMinimumSize(new Dimension(350, 200));

		// Apply tab colour overrides so the selected tab is clearly visible
		javax.swing.UIManager.put("TabbedPane.selected", DarkTheme.BG_CARD);
		javax.swing.UIManager.put("TabbedPane.background", DarkTheme.BG_HEADER);
		javax.swing.UIManager.put("TabbedPane.foreground", DarkTheme.TEXT_MUTED);
		javax.swing.UIManager.put("TabbedPane.selectedForeground", DarkTheme.ACCENT_PRIMARY);
		javax.swing.UIManager.put("TabbedPane.contentAreaColor", DarkTheme.BG_DARK);
		javax.swing.UIManager.put("TabbedPane.light", DarkTheme.BORDER_COLOR);
		javax.swing.UIManager.put("TabbedPane.shadow", DarkTheme.BG_DARK);
		javax.swing.UIManager.put("TabbedPane.darkShadow", DarkTheme.BG_DARK);
		javax.swing.UIManager.put("TabbedPane.focus", DarkTheme.ACCENT_PRIMARY);

		tabbedPane = new JTabbedPane();
		tabbedPane.setBackground(DarkTheme.BG_HEADER);
		tabbedPane.setForeground(DarkTheme.TEXT_MUTED);
		tabbedPane.setFont(DarkTheme.FONT_BOLD);

		// 1. Unified ALL LOGS tab
		ConsoleTab unifiedTab = new ConsoleTab("all", "GLOBAL CONSOLE", unifiedLogBuffer, this::clearAllLogs);
		tabs.put("all", unifiedTab);
		tabbedPane.addTab("🌐 ALL LOGS", unifiedTab);

		// 2. Tabs per service
		for (ServiceConfig service : services) {
			LogBuffer buffer = processRunner.getOrCreateLogBuffer(service);
			ConsoleTab tab = new ConsoleTab(service.getId(), service.getName(), buffer, () -> {
				buffer.clear();
				unifiedLogBuffer.removeEntriesForService(service.getId());
			});
			tabs.put(service.getId(), tab);
			tabbedPane.addTab(service.getName(), tab);
		}

		add(tabbedPane, BorderLayout.CENTER);
	}

	public void selectTab(String serviceId) {
		ConsoleTab tab = tabs.get(serviceId);
		if (tab != null) {
			tabbedPane.setSelectedComponent(tab);
		}
	}

	public void clearAllLogs() {
		unifiedLogBuffer.clear();
		for (Map.Entry<String, ConsoleTab> entry : tabs.entrySet()) {
			if (!"all".equals(entry.getKey())) {
				entry.getValue().clearBuffer();
			}
		}
	}

	private static class ConsoleTab extends JPanel implements LogBuffer.LogListener {

		private final String id;
		private final String name;
		private final LogBuffer logBuffer;
		private final Runnable clearAction;

		private final JTextPane textPane;
		private final JScrollPane scrollPane;
		private final JTextField searchField;
		private final JLabel matchLabel;
		private final JCheckBox autoScrollCheckBox;
		private final JCheckBox filterCheckBox;

		private final List<Integer> searchMatches = new ArrayList<>();
		private int currentMatchIndex = -1;
		private boolean selectingText;

		private final SimpleAttributeSet styleInfo;
		private final SimpleAttributeSet styleWarn;
		private final SimpleAttributeSet styleError;
		private final SimpleAttributeSet styleDebug;
		private final SimpleAttributeSet styleSystem;

		public ConsoleTab(String id, String name, LogBuffer logBuffer, Runnable clearAction) {
			this.id = id;
			this.name = name;
			this.logBuffer = logBuffer;
			this.clearAction = clearAction;

			setLayout(new BorderLayout());
			setBackground(DarkTheme.BG_DARK);

			// Init text styles
			StyleContext sc = StyleContext.getDefaultStyleContext();
			styleInfo = new SimpleAttributeSet(
					sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, DarkTheme.TEXT_PRIMARY));
			styleWarn = new SimpleAttributeSet(
					sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, DarkTheme.ACCENT_YELLOW));
			styleError = new SimpleAttributeSet(
					sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, DarkTheme.ACCENT_RED));
			styleDebug = new SimpleAttributeSet(
					sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, DarkTheme.TEXT_MUTED));
			styleSystem = new SimpleAttributeSet(
					sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, DarkTheme.ACCENT_PURPLE));

			// Top toolbar
			JPanel toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
			toolbar.setBackground(DarkTheme.BG_HEADER);
			toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_COLOR));

			JLabel searchLabel = new JLabel("🔍 Search:");
			searchLabel.setFont(DarkTheme.FONT_BOLD);
			searchLabel.setForeground(DarkTheme.TEXT_PRIMARY);

			searchField = new JTextField(16);
			searchField.setBackground(DarkTheme.BG_INPUT);
			searchField.setForeground(DarkTheme.TEXT_PRIMARY);
			searchField.setCaretColor(DarkTheme.TEXT_PRIMARY);
			searchField.setFont(DarkTheme.FONT_MAIN);
			searchField.addActionListener(e -> performSearch());

			JButton prevMatchBtn = DarkTheme.createStyledButton("Prev", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			prevMatchBtn.setToolTipText("Previous search match");
			prevMatchBtn.getAccessibleContext().setAccessibleName("Previous search match");
			prevMatchBtn.addActionListener(e -> navigateMatch(-1));

			JButton nextMatchBtn = DarkTheme.createStyledButton("Next", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			nextMatchBtn.setToolTipText("Next search match");
			nextMatchBtn.getAccessibleContext().setAccessibleName("Next search match");
			nextMatchBtn.addActionListener(e -> navigateMatch(1));

			matchLabel = new JLabel("");
			matchLabel.setFont(DarkTheme.FONT_MAIN);
			matchLabel.setForeground(DarkTheme.ACCENT_PRIMARY);

			filterCheckBox = new JCheckBox("Filter lines");
			filterCheckBox.setOpaque(false);
			filterCheckBox.setFont(DarkTheme.FONT_MAIN);
			filterCheckBox.setForeground(DarkTheme.TEXT_PRIMARY);
			filterCheckBox.addActionListener(e -> rebuildLogDisplay());

			autoScrollCheckBox = new JCheckBox("Auto-scroll", true);
			autoScrollCheckBox.setOpaque(false);
			autoScrollCheckBox.setFont(DarkTheme.FONT_MAIN);
			autoScrollCheckBox.setForeground(DarkTheme.TEXT_PRIMARY);

			JButton clearBtn = DarkTheme.createStyledButton("Clear", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			clearBtn.setFont(DarkTheme.FONT_MAIN);
			DarkTheme.reserveButtonBusyWidth(clearBtn, "Clearing");
			clearBtn.addActionListener(e -> acknowledgeClear(clearBtn));

			JButton copyBtn = DarkTheme.createStyledButton("Copy", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			copyBtn.setFont(DarkTheme.FONT_MAIN);
			copyBtn.setToolTipText("Copy selected text, or all displayed logs when nothing is selected");
			copyBtn.addActionListener(e -> copyToClipboard());

			JButton exportBtn = DarkTheme.createStyledButton("Export...", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			exportBtn.setFont(DarkTheme.FONT_MAIN);
			exportBtn.addActionListener(e -> exportLogFile());

			toolbar.add(searchLabel);
			toolbar.add(searchField);
			toolbar.add(prevMatchBtn);
			toolbar.add(nextMatchBtn);
			toolbar.add(matchLabel);
			toolbar.add(filterCheckBox);
			toolbar.add(autoScrollCheckBox);
			toolbar.add(clearBtn);
			toolbar.add(copyBtn);
			toolbar.add(exportBtn);

			add(toolbar, BorderLayout.NORTH);

			// Console Text Area
			textPane = new JTextPane();
			textPane.setEditable(false);
			textPane.getInputMap(JComponent.WHEN_FOCUSED).put(
					KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK), DefaultEditorKit.copyAction);
			textPane.getInputMap(JComponent.WHEN_FOCUSED).put(
					KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), DefaultEditorKit.copyAction);
			((DefaultCaret) textPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
			textPane.setSelectionColor(DarkTheme.ACCENT_PRIMARY);
			textPane.setSelectedTextColor(Color.WHITE);
			textPane.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent event) {
					selectingText = true;
				}

				@Override
				public void mouseReleased(MouseEvent event) {
					selectingText = false;
				}
			});
			textPane.setBackground(DarkTheme.BG_DARK);
			textPane.setForeground(DarkTheme.TEXT_PRIMARY);
			textPane.setFont(DarkTheme.FONT_MONO);
			textPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

			scrollPane = new JScrollPane(textPane);
			scrollPane.setBorder(null);
			scrollPane.getVerticalScrollBar().setUnitIncrement(16);

			add(scrollPane, BorderLayout.CENTER);

			logBuffer.addListener(this);
			rebuildLogDisplay();
		}

		@Override
		public void onLogAdded(LogBuffer.LogEntry entry) {
			if (filterCheckBox.isSelected()) {
				String query = searchField.getText().trim().toLowerCase();
				if (!query.isEmpty() && !entry.getText().toLowerCase().contains(query)) {
					return;
				}
			}

			appendEntryToDoc(entry);

			if (autoScrollCheckBox.isSelected()) {
				SwingUtilities.invokeLater(() -> {
					if (autoScrollCheckBox.isSelected() && !selectingText
							&& textPane.getSelectionStart() == textPane.getSelectionEnd()) {
						textPane.setCaretPosition(textPane.getDocument().getLength());
					}
				});
			}
		}

		@Override
		public void onLogCleared() {
			textPane.setText("");
			matchLabel.setText("");
			searchMatches.clear();
			currentMatchIndex = -1;
		}

		public void clearBuffer() {
			logBuffer.clear();
		}

		private void acknowledgeClear(JButton clearButton) {
			DarkTheme.setButtonBusy(clearButton, true, "Clearing");
			clearAction.run();
			Timer restoreTimer = new Timer(450, event -> DarkTheme.setButtonBusy(clearButton, false, ""));
			restoreTimer.setRepeats(false);
			restoreTimer.start();
		}

		private void appendEntryToDoc(LogBuffer.LogEntry entry) {
			Document doc = textPane.getDocument();
			AttributeSet style = switch (entry.getLevel()) {
				case WARN -> styleWarn;
				case ERROR -> styleError;
				case DEBUG -> styleDebug;
				case SYSTEM -> styleSystem;
				default -> styleInfo;
			};

			try {
				doc.insertString(doc.getLength(), entry.getFormattedLine() + "\n", style);
			} catch (BadLocationException exception) {
				LOGGER.log(System.Logger.Level.WARNING, "Unable to append a sandbox log entry", exception);
			}
		}

		private void rebuildLogDisplay() {
			textPane.setText("");
			String query = searchField.getText().trim().toLowerCase();
			boolean isFiltering = filterCheckBox.isSelected() && !query.isEmpty();

			List<LogBuffer.LogEntry> entries = logBuffer.getEntries();
			for (LogBuffer.LogEntry entry : entries) {
				if (isFiltering && !entry.getText().toLowerCase().contains(query)) {
					continue;
				}
				appendEntryToDoc(entry);
			}

			performSearch();
		}

		private void performSearch() {
			searchMatches.clear();
			currentMatchIndex = -1;
			String query = searchField.getText().trim();
			if (query.isEmpty()) {
				matchLabel.setText("");
				return;
			}

			String content = textPane.getText();
			String lowerContent = content.toLowerCase();
			String lowerQuery = query.toLowerCase();

			int index = lowerContent.indexOf(lowerQuery);
			while (index >= 0) {
				searchMatches.add(index);
				index = lowerContent.indexOf(lowerQuery, index + lowerQuery.length());
			}

			if (!searchMatches.isEmpty()) {
				currentMatchIndex = 0;
				highlightMatch();
				matchLabel.setText(searchMatches.size() + " matches");
			} else {
				matchLabel.setText("0 matches");
			}
		}

		private void navigateMatch(int direction) {
			if (searchMatches.isEmpty()) {
				return;
			}
			currentMatchIndex += direction;
			if (currentMatchIndex < 0) {
				currentMatchIndex = searchMatches.size() - 1;
			} else if (currentMatchIndex >= searchMatches.size()) {
				currentMatchIndex = 0;
			}
			highlightMatch();
		}

		private void highlightMatch() {
			if (currentMatchIndex >= 0 && currentMatchIndex < searchMatches.size()) {
				int pos = searchMatches.get(currentMatchIndex);
				String query = searchField.getText().trim();
				textPane.setCaretPosition(pos);
				textPane.moveCaretPosition(pos + query.length());
				textPane.getCaret().setSelectionVisible(true);
				matchLabel.setText((currentMatchIndex + 1) + "/" + searchMatches.size() + " matches");
			}
		}

		private void copyToClipboard() {
			String text = textPane.getSelectedText();
			if (text == null || text.isEmpty()) {
				text = textPane.getText();
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			JOptionPane.showMessageDialog(this, "Log content copied to clipboard!", "Copied",
					JOptionPane.INFORMATION_MESSAGE);
		}

		private void exportLogFile() {
			JFileChooser chooser = new JFileChooser();
			chooser.setSelectedFile(new File(name.replaceAll("\\s+", "_").toLowerCase() + ".log"));
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				try {
					logBuffer.exportToFile(chooser.getSelectedFile());
					JOptionPane.showMessageDialog(this, "Log exported successfully!", "Exported",
							JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(this, "Failed to export log: " + e.getMessage(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
}
