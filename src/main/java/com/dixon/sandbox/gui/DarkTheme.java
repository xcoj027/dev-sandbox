/**
 * Copyright (c) 2026 TNQ MEDIA
 *
 * Author: TNQ MEDIA
 * Git: github.com/xcoj027
 */
package com.dixon.sandbox.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.UIManager;

public class DarkTheme {

	// Accessible light palette. The legacy class name is retained to avoid a broad
	// mechanical rename across the Swing components.
	public static final Color BG_DARK = new Color(245, 247, 251); // #f5f7fb
	public static final Color BG_CARD = new Color(255, 255, 255); // #ffffff
	public static final Color BG_CARD_HOVER = new Color(238, 244, 255); // #eef4ff
	public static final Color BG_HEADER = new Color(255, 255, 255); // #ffffff
	public static final Color BG_INPUT = new Color(226, 232, 240); // #e2e8f0

	public static final Color TEXT_PRIMARY = new Color(23, 32, 51); // #172033
	public static final Color TEXT_SECONDARY = new Color(71, 85, 105); // #475569
	public static final Color TEXT_MUTED = new Color(100, 116, 139); // #64748b

	public static final Color ACCENT_PRIMARY = new Color(37, 99, 235); // #2563eb Blue
	public static final Color ACCENT_GREEN = new Color(21, 128, 61); // #15803d Green
	public static final Color ACCENT_RED = new Color(185, 28, 28); // #b91c1c Red
	public static final Color ACCENT_YELLOW = new Color(161, 98, 7); // #a16207 Amber
	public static final Color ACCENT_PURPLE = new Color(126, 34, 206); // #7e22ce Purple

	public static final Color BORDER_COLOR = new Color(203, 213, 225); // #cbd5e1

	// Fonts
	public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 16);
	public static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 13);
	public static final Font FONT_MAIN = new Font("SansSerif", Font.PLAIN, 12);
	public static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 12);
	public static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 12);

	public static void applyThemeToComponent(JComponent comp) {
		comp.setBackground(BG_CARD);
		comp.setForeground(TEXT_PRIMARY);
	}

	public static JButton createStyledButton(String text, Color bg, Color fg) {
		JButton btn = new InteractiveButton(text, bg, fg);
		btn.setFont(FONT_BOLD);
		btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
		return btn;
	}

	public static void setButtonBusy(JButton button, boolean busy, String busyText) {
		if (button instanceof InteractiveButton interactiveButton) {
			interactiveButton.setBusy(busy, busyText);
		} else {
			button.setEnabled(!busy);
		}
	}

	public static void reserveButtonBusyWidth(JButton button, String busyText) {
		String idleText = button.getText();
		Dimension idleSize = button.getPreferredSize();
		button.setText("◐ " + busyText);
		Dimension busySize = button.getPreferredSize();
		button.setText(idleText);
		Dimension reservedSize = new Dimension(Math.max(idleSize.width, busySize.width),
				Math.max(idleSize.height, busySize.height));
		button.setPreferredSize(reservedSize);
		button.setMinimumSize(reservedSize);
	}

	private static class InteractiveButton extends JButton {

		private static final String[] SPINNER_FRAMES = {"◐", "◓", "◑", "◒"};
		private static final boolean REDUCE_MOTION = Boolean.getBoolean("dixon.sandbox.reduceMotion")
				|| UIManager.getBoolean("Component.reduceMotion");
		private final Color baseColor;
		private final Color hoverColor;
		private final Color pressedColor;
		private final Timer transitionTimer;
		private final Timer spinnerTimer;
		private Color paintedColor;
		private Color targetColor;
		private boolean hovered;
		private boolean pressed;
		private boolean busy;
		private int spinnerFrame;
		private String idleText;
		private String busyText;

		InteractiveButton(String text, Color bg, Color fg) {
			super(text);
			idleText = text;
			baseColor = bg;
			hoverColor = blend(bg, Color.WHITE, 0.14f);
			pressedColor = blend(bg, Color.BLACK, 0.18f);
			paintedColor = baseColor;
			targetColor = baseColor;
			setForeground(fg);
			setFocusPainted(false);
			setContentAreaFilled(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			transitionTimer = new Timer(16, event -> animateColor());
			spinnerTimer = new Timer(110, event -> {
				spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length;
				setText(SPINNER_FRAMES[spinnerFrame] + " " + busyText);
			});
			getModel().addChangeListener(event -> {
				boolean modelPressed = getModel().isPressed() && getModel().isArmed();
				if (pressed != modelPressed) {
					pressed = modelPressed;
					updateTargetColor();
					repaint();
				}
			});

			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent event) {
					hovered = true;
					updateTargetColor();
				}

				@Override
				public void mouseExited(MouseEvent event) {
					hovered = false;
					pressed = false;
					updateTargetColor();
				}

			});
			addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent event) {
					repaint();
				}

				@Override
				public void focusLost(FocusEvent event) {
					repaint();
				}
			});
		}

		void setBusy(boolean busy, String busyText) {
			if (this.busy == busy) {
				return;
			}
			this.busy = busy;
			if (busy) {
				idleText = getText();
				this.busyText = busyText;
				spinnerFrame = 0;
				setText(SPINNER_FRAMES[spinnerFrame] + " " + busyText);
				setEnabled(false);
				if (!REDUCE_MOTION) {
					spinnerTimer.start();
				}
			} else {
				spinnerTimer.stop();
				setText(idleText);
				setEnabled(true);
			}
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled && !busy);
			setCursor(isEnabled() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			updateTargetColor();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D graphics2d = (Graphics2D) graphics.create();
			graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			float scale = pressed && isEnabled() && !REDUCE_MOTION ? 0.96f : 1.0f;
			int width = Math.round(getWidth() * scale);
			int height = Math.round(getHeight() * scale);
			int x = (getWidth() - width) / 2;
			int y = (getHeight() - height) / 2;
			Color fill = isEnabled() ? paintedColor : blend(baseColor, BG_DARK, 0.58f);
			graphics2d.setColor(fill);
			graphics2d.fillRoundRect(x, y, width, height, 8, 8);
			if (isFocusOwner()) {
				graphics2d.setColor(focusRingColor(fill));
				graphics2d.setStroke(new BasicStroke(2.0f));
				graphics2d.drawRoundRect(x + 2, y + 2, width - 5, height - 5, 6, 6);
			}
			graphics2d.dispose();

			Graphics2D contentGraphics = (Graphics2D) graphics.create();
			if (scale < 1.0f) {
				contentGraphics.translate(getWidth() / 2.0, getHeight() / 2.0);
				contentGraphics.scale(scale, scale);
				contentGraphics.translate(-getWidth() / 2.0, -getHeight() / 2.0);
			}
			super.paintComponent(contentGraphics);
			contentGraphics.dispose();
		}

		private void updateTargetColor() {
			targetColor = !isEnabled() ? baseColor : pressed ? pressedColor : hovered ? hoverColor : baseColor;
			if (REDUCE_MOTION) {
				paintedColor = targetColor;
				repaint();
			} else if (!transitionTimer.isRunning()) {
				transitionTimer.start();
			}
		}

		private void animateColor() {
			paintedColor = blend(paintedColor, targetColor, 0.28f);
			if (colorDistance(paintedColor, targetColor) < 4) {
				paintedColor = targetColor;
				transitionTimer.stop();
			}
			repaint();
		}

		private static Color blend(Color from, Color to, float amount) {
			int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount);
			int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount);
			int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount);
			return new Color(red, green, blue);
		}

		private static int colorDistance(Color first, Color second) {
			return Math.abs(first.getRed() - second.getRed()) + Math.abs(first.getGreen() - second.getGreen())
					+ Math.abs(first.getBlue() - second.getBlue());
		}

		private static Color focusRingColor(Color fill) {
			double luminance = (0.2126 * fill.getRed() + 0.7152 * fill.getGreen() + 0.0722 * fill.getBlue()) / 255.0;
			return luminance > 0.52 ? ACCENT_PRIMARY : Color.WHITE;
		}
	}

	public static class RoundedBorder extends javax.swing.border.AbstractBorder {

		private final Color color;
		private final int radius;

		public RoundedBorder(Color color, int radius) {
			this.color = color;
			this.radius = radius;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(color);
			g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
			g2d.dispose();
		}

		@Override
		public Insets getBorderInsets(Component c) {
			return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			insets.left = insets.right = insets.top = insets.bottom = radius / 2;
			return insets;
		}
	}
}
