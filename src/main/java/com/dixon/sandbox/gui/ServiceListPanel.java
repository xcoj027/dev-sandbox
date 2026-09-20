/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import com.dixon.sandbox.model.ServiceCategory;
import com.dixon.sandbox.model.ServiceConfig;
import com.dixon.sandbox.model.ServiceStatus;
import com.dixon.sandbox.service.ServiceCatalog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ServiceListPanel extends JPanel {

	public interface ServiceItemActionListener {

		void onStart(ServiceConfig config);

		void onStop(ServiceConfig config);

		void onForceKill(ServiceConfig config);

		void onSelect(ServiceConfig config);

		void onEditConfig(ServiceConfig config);
	}

	private final ServiceCatalog catalog;
	private final Map<String, ServiceItemView> itemViews = new HashMap<>();
	private final List<CategorySection> categorySections = new ArrayList<>();
	private final JPanel contentPanel;
	private ServiceItemActionListener listener;
	private String selectedServiceId;
	private final JTextField filterField;

	public ServiceListPanel(ServiceCatalog catalog) {
		this.catalog = catalog;
		setLayout(new BorderLayout());
		setBackground(DarkTheme.BG_DARK);
		setPreferredSize(new Dimension(500, 700));
		setMinimumSize(new Dimension(320, 200));

		// Filter field container at top
		JPanel filterPanel = new JPanel(new BorderLayout());
		filterPanel.setBackground(DarkTheme.BG_DARK);
		filterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

		filterField = new FilterTextField("Filter services by name…");
		filterPanel.add(filterField, BorderLayout.CENTER);
		add(filterPanel, BorderLayout.NORTH);

		// Listener to apply filter on text change
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				applyFilter();
			}
		});

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(DarkTheme.BG_DARK);
		contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

		// Build categorized service sections
		for (ServiceCategory category : List.of(ServiceCategory.WEB_APP, ServiceCategory.JAVA_APP)) {
			List<ServiceConfig> categoryServices = catalog.getServices().stream()
					.filter(s -> s.getCategory() == category).toList();

			if (!categoryServices.isEmpty()) {
				CategorySection section = new CategorySection(category, categoryServices);
				categorySections.add(section);
				contentPanel.add(section);
			}
		}

		// Vertical glue at bottom so cards never stretch vertically when total height
		// is small
		contentPanel.add(Box.createVerticalGlue());

		JScrollPane scrollPane = new JScrollPane(contentPanel);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(12);
		add(scrollPane, BorderLayout.CENTER);
	}

	public void setListener(ServiceItemActionListener listener) {
		this.listener = listener;
	}

	public void refreshStatus() {
		for (ServiceItemView view : itemViews.values()) {
			view.updateState();
		}
	}

	public void setSelectedServiceId(String serviceId) {
		this.selectedServiceId = serviceId;
		for (Map.Entry<String, ServiceItemView> entry : itemViews.entrySet()) {
			entry.getValue().setSelected(entry.getKey().equals(serviceId));
		}
	}

	private void applyFilter() {
		String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
		for (CategorySection section : categorySections) {
			int visibleCount = 0;
			for (ServiceItemView view : section.getCategoryItemViews()) {
				boolean matches = filter.isEmpty()
						|| view.getConfig().getName().toLowerCase(Locale.ROOT).contains(filter);
				if (view.isVisible() != matches) {
					view.setVisible(matches);
					view.revalidate();
				}
				if (matches) {
					visibleCount++;
				}
			}
			if (section.isVisible() != (visibleCount > 0)) {
				section.setVisible(visibleCount > 0);
				section.revalidate();
			}
		}
		contentPanel.revalidate();
		contentPanel.repaint();
		revalidate();
		repaint();
	}

	private class CategorySection extends JPanel {

		private final List<ServiceItemView> categoryItemViews = new ArrayList<>();

		public CategorySection(ServiceCategory category, List<ServiceConfig> categoryServices) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel categoryLabel = new JLabel(category.getDisplayName().toUpperCase(Locale.ROOT));
			categoryLabel.setFont(DarkTheme.FONT_BOLD);
			categoryLabel.setForeground(DarkTheme.ACCENT_PRIMARY);
			categoryLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
			categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(categoryLabel);

			for (ServiceConfig config : categoryServices) {
				ServiceItemView itemView = new ServiceItemView(config);
				itemViews.put(config.getId(), itemView);
				itemView.setAlignmentX(Component.LEFT_ALIGNMENT);
				add(itemView);
				categoryItemViews.add(itemView);
			}
		}

		public List<ServiceItemView> getCategoryItemViews() {
			return categoryItemViews;
		}

		@Override
		public Dimension getMaximumSize() {
			Dimension preferred = getPreferredSize();
			return new Dimension(Integer.MAX_VALUE, preferred.height);
		}
	}

	private static class FilterTextField extends JTextField {

		private final String placeholder;

		public FilterTextField(String placeholder) {
			this.placeholder = placeholder;
			setBackground(DarkTheme.BG_INPUT);
			setForeground(DarkTheme.TEXT_PRIMARY);
			setCaretColor(DarkTheme.TEXT_PRIMARY);
			setFont(DarkTheme.FONT_MAIN);
			setBorder(
					BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_COLOR, 1, true),
							BorderFactory.createEmptyBorder(6, 10, 6, 10)));
			addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent e) {
					repaint();
				}

				@Override
				public void focusLost(FocusEvent e) {
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty() && placeholder != null) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(DarkTheme.TEXT_MUTED);
				g2.setFont(getFont());
				Insets insets = getInsets();
				FontMetrics fm = g2.getFontMetrics();
				int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
				g2.drawString(placeholder, insets.left, y);
				g2.dispose();
			}
		}
	}

	private class ServiceItemView extends JPanel {

		private final ServiceConfig config;
		private final JLabel statusLabel;
		private final JLabel nameLabel;
		private final JLabel portLabel;
		private final JButton startBtn;
		private final JButton stopBtn;
		private final JButton configBtn;
		private final JButton killBtn;
		private final JButton openBtn;

		public ServiceItemView(ServiceConfig config) {
			this.config = config;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(DarkTheme.BG_CARD);
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0),
					BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_COLOR, 1, true),
							BorderFactory.createEmptyBorder(8, 10, 8, 10))));

			// Row 1: Service name
			nameLabel = new JLabel(config.getName());
			nameLabel.setFont(DarkTheme.FONT_BOLD);
			nameLabel.setForeground(DarkTheme.TEXT_PRIMARY);
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

			// Row 2: Port + Status
			portLabel = new JLabel(config.getDefaultPort() > 0 ? "Port " + config.getDefaultPort() : "Worker");
			portLabel.setFont(DarkTheme.FONT_MAIN);
			portLabel.setForeground(DarkTheme.TEXT_MUTED);

			statusLabel = new JLabel(config.getStatus().getLabel());
			statusLabel.setFont(DarkTheme.FONT_BOLD);
			statusLabel.setOpaque(true);
			statusLabel.setBackground(DarkTheme.BG_INPUT);
			statusLabel.setForeground(config.getStatus().getColor());
			statusLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));

			JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			infoRow.setOpaque(false);
			infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			infoRow.add(portLabel);
			infoRow.add(statusLabel);
			infoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, infoRow.getPreferredSize().height));

			// Row 3: Description
			JLabel descLabel = new JLabel(config.getDescription());
			descLabel.setFont(DarkTheme.FONT_MAIN);
			descLabel.setForeground(DarkTheme.TEXT_MUTED);
			descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

			// Bottom line: Action Buttons in fixed FlowLayout row
			JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			btnPanel.setOpaque(false);
			btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

			startBtn = DarkTheme.createStyledButton("Start", DarkTheme.ACCENT_GREEN, DarkTheme.BG_HEADER);
			startBtn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
			startBtn.setFont(DarkTheme.FONT_MAIN);
			DarkTheme.reserveButtonBusyWidth(startBtn, "Starting");
			startBtn.addActionListener(e -> {
				if (listener != null) {
					listener.onSelect(config);
					listener.onStart(config);
				}
			});

			stopBtn = DarkTheme.createStyledButton("Stop", DarkTheme.ACCENT_RED, DarkTheme.BG_HEADER);
			stopBtn.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
			stopBtn.setFont(DarkTheme.FONT_MAIN);
			DarkTheme.reserveButtonBusyWidth(stopBtn, "Stopping");
			stopBtn.addActionListener(e -> {
				if (listener != null) {
					listener.onSelect(config);
					listener.onStop(config);
				}
			});

			configBtn = DarkTheme.createStyledButton("⚙", DarkTheme.BG_INPUT, DarkTheme.TEXT_PRIMARY);
			configBtn.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			configBtn.setToolTipText("Edit service environment & configuration");
			configBtn.addActionListener(e -> {
				if (listener != null) {
					listener.onSelect(config);
					listener.onEditConfig(config);
				}
			});

			killBtn = DarkTheme.createStyledButton("⚡ Kill", DarkTheme.BG_INPUT, DarkTheme.ACCENT_RED);
			killBtn.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			killBtn.setFont(DarkTheme.FONT_MAIN);
			killBtn.setToolTipText("Find process ID and force kill this service");
			killBtn.addActionListener(e -> {
				if (listener != null) {
					listener.onSelect(config);
					listener.onForceKill(config);
				}
			});

			openBtn = DarkTheme.createStyledButton("🌐 Open", DarkTheme.BG_INPUT, DarkTheme.ACCENT_PRIMARY);
			openBtn.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			openBtn.setFont(DarkTheme.FONT_MAIN);
			openBtn.setToolTipText("Open host & port in default browser");
			openBtn.setEnabled(config.getDefaultPort() > 0);
			openBtn.addActionListener(e -> {
				if (listener != null) {
					listener.onSelect(config);
				}
				String url = com.dixon.sandbox.service.BrowserLauncher.getBrowserUrl(config);
				if (url != null) {
					com.dixon.sandbox.service.BrowserLauncher.openInBrowser(url);
				}
			});

			btnPanel.add(startBtn);
			btnPanel.add(stopBtn);
			btnPanel.add(openBtn);
			btnPanel.add(killBtn);
			btnPanel.add(configBtn);
			btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnPanel.getPreferredSize().height));

			add(nameLabel);
			add(Box.createRigidArea(new Dimension(0, 2)));
			add(infoRow);
			add(Box.createRigidArea(new Dimension(0, 2)));
			add(descLabel);
			add(Box.createRigidArea(new Dimension(0, 6)));
			add(btnPanel);

			// Click to select service console
			MouseAdapter clickAdapter = new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					if (listener != null) {
						listener.onSelect(config);
					}
				}
			};
			addMouseListener(clickAdapter);

			updateState();
		}

		public ServiceConfig getConfig() {
			return config;
		}

		@Override
		public Dimension getMaximumSize() {
			Dimension preferred = getPreferredSize();
			return new Dimension(Integer.MAX_VALUE, preferred.height);
		}

		public void updateState() {
			ServiceStatus status = config.getDisplayStatus();
			statusLabel.setText(status.getLabel());
			statusLabel.setForeground(status.getColor());

			boolean pending = status == ServiceStatus.STARTING || status == ServiceStatus.STOPPING;
			boolean isRunning = config.isRunning();
			DarkTheme.setButtonBusy(startBtn, status == ServiceStatus.STARTING, "Starting");
			DarkTheme.setButtonBusy(stopBtn, status == ServiceStatus.STOPPING, "Stopping");
			startBtn.setEnabled(!pending && !isRunning);
			stopBtn.setEnabled(!pending && isRunning);
			configBtn.setEnabled(!pending);
			killBtn.setEnabled(!pending);
			openBtn.setEnabled(!pending && config.getDefaultPort() > 0);

			if (config.getPid() > 0) {
				portLabel.setText((config.getDefaultPort() > 0 ? ":" + config.getDefaultPort() : "Worker") + " (PID "
						+ config.getPid() + ")");
			} else {
				portLabel.setText(config.getDefaultPort() > 0 ? "Port " + config.getDefaultPort() : "Worker");
			}
		}

		public void setSelected(boolean selected) {
			if (selected) {
				setBackground(DarkTheme.BG_CARD_HOVER);
				setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0),
						BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(DarkTheme.ACCENT_PRIMARY, 2, true),
								BorderFactory.createEmptyBorder(7, 9, 7, 9))));
			} else {
				setBackground(DarkTheme.BG_CARD);
				setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0),
						BorderFactory.createCompoundBorder(
								BorderFactory.createLineBorder(DarkTheme.BORDER_COLOR, 1, true),
								BorderFactory.createEmptyBorder(8, 10, 8, 10))));
			}
		}
	}
}
