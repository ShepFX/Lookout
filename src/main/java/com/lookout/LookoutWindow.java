package com.lookout;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.FontManager;

/**
 * The small always-on-top window. It shows whatever frame it was last given, letterboxed, with a
 * border that flashes when the plugin wants attention. It never takes keyboard focus, so it can
 * sit over other applications without interrupting them.
 */
class LookoutWindow extends JFrame
{
	static final int BORDER = 3;
	private static final Color IDLE = new Color(58, 58, 58);
	private static final Color BACKGROUND = new Color(20, 20, 20);
	private static final int FLASH_PERIOD_MS = 300;
	private static final int ALERT_BORDER = 8;
	private static final int DRAG_THRESHOLD = 4;
	/** The square in the bottom-right corner that resizes instead of moves. */
	private static final int GRIP = 18;
	/** The hide button in the top-right corner. */
	private static final int BUTTON = 16;

	private final Runnable onClick;
	private final Runnable onHide;
	private final IntConsumer onScroll;
	private final IntConsumer onResized;
	private final Consumer<Point> onMoved;
	private final Runnable onSourceChanged;
	private final Timer pulse;
	private volatile Image frame;
	private volatile Dimension source;
	private Color flashColor;
	private String flashLabel;
	private long flashUntil;
	private Color stickyColor;
	private Point dragStart;
	private Point windowStart;
	private Dimension sizeStart;
	private boolean dragged;
	private boolean resizing;
	private boolean hoveringHide;

	LookoutWindow(Runnable onClick, Runnable onHide, IntConsumer onScroll, IntConsumer onResized, Consumer<Point> onMoved, Runnable onSourceChanged)
	{
		super("Lookout");
		this.onClick = onClick;
		this.onHide = onHide;
		this.onScroll = onScroll;
		this.onResized = onResized;
		this.onMoved = onMoved;
		this.onSourceChanged = onSourceChanged;
		setUndecorated(true);
		// A utility window stays off the taskbar and out of alt-tab.
		setType(Type.UTILITY);
		setAlwaysOnTop(true);
		setFocusableWindowState(false);
		setAutoRequestFocus(false);
		setIconImage(ClientUI.ICON_128);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		Panel panel = new Panel();
		panel.setBackground(BACKGROUND);
		Mouse mouse = new Mouse();
		panel.addMouseListener(mouse);
		panel.addMouseMotionListener(mouse);
		panel.addMouseWheelListener(mouse);
		setContentPane(panel);

		pulse = new Timer(FLASH_PERIOD_MS / 2, event -> tick());
		pulse.setRepeats(true);
	}

	/** Hands the window a new frame. Safe from any thread; the paint happens on the Swing thread. */
	void showFrame(Image image)
	{
		if (image == null) return;
		Dimension previous = source;
		Dimension now = new Dimension(image.getWidth(null), image.getHeight(null));
		frame = image;
		if (!now.equals(previous))
		{
			source = now;
			onSourceChanged.run();
		}
		repaint();
	}

	Dimension getSource()
	{
		return source;
	}

	/** Flashes the whole window in the given colour for a while, naming the reason. Swing thread only. */
	void flash(Color color, String label, int millis)
	{
		flashColor = color;
		flashLabel = label;
		flashUntil = System.currentTimeMillis() + millis;
		if (!pulse.isRunning()) pulse.start();
		repaint();
	}

	/** A colour the border keeps until cleared, for conditions rather than moments. Swing thread only. */
	void setSticky(Color color)
	{
		stickyColor = color;
		repaint();
	}

	void setOpacityPercent(int percent)
	{
		float opacity = Math.max(0.2f, Math.min(1f, percent / 100f));
		try
		{
			GraphicsDevice device = getGraphicsConfiguration().getDevice();
			if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))
			{
				setOpacity(opacity);
			}
		}
		catch (RuntimeException ignored)
		{
			// Some window managers refuse; a solid window is fine.
		}
	}

	private void tick()
	{
		if (System.currentTimeMillis() >= flashUntil)
		{
			flashColor = null;
			flashLabel = null;
			pulse.stop();
		}
		repaint();
	}

	private boolean flashOn()
	{
		return flashColor != null && (System.currentTimeMillis() / FLASH_PERIOD_MS) % 2 == 0;
	}

	private Color borderColor()
	{
		if (flashOn()) return flashColor;
		if (stickyColor != null) return stickyColor;
		return IDLE;
	}

	private final class Panel extends JPanel
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics;
			Image image = frame;
			Dimension panel = getSize();
			if (image == null)
			{
				g.setColor(Color.GRAY);
				g.setFont(FontManager.getRunescapeSmallFont());
				String text = "Waiting for the game...";
				int width = g.getFontMetrics().stringWidth(text);
				g.drawString(text, (panel.width - width) / 2, panel.height / 2);
			}
			else
			{
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				Rectangle box = LookoutGeometry.letterbox(new Dimension(panel.width - 2 * BORDER, panel.height - 2 * BORDER), source);
				g.drawImage(image, box.x + BORDER, box.y + BORDER, box.width, box.height, null);
			}
			boolean loud = flashOn();
			if (loud)
			{
				// Wash the whole picture so the flash is impossible to miss from the corner of an eye.
				g.setColor(new Color(flashColor.getRed(), flashColor.getGreen(), flashColor.getBlue(), 90));
				g.fillRect(0, 0, panel.width, panel.height);
			}
			int border = loud ? ALERT_BORDER : BORDER;
			g.setColor(borderColor());
			g.setStroke(new BasicStroke(border));
			g.drawRect(border / 2, border / 2, panel.width - border, panel.height - border);
			if (flashLabel != null)
			{
				g.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
				int width = g.getFontMetrics().stringWidth(flashLabel);
				int x = (panel.width - width) / 2;
				int y = ALERT_BORDER + 26;
				g.setColor(new Color(0, 0, 0, 170));
				g.fillRoundRect(x - 10, y - 20, width + 20, 28, 8, 8);
				g.setColor(flashColor == null ? Color.WHITE : flashColor);
				g.drawString(flashLabel, x, y);
			}
			// A small hide button top-right, and a grip bottom-right so the resize handle can be found.
			Rectangle button = hideButton();
			g.setColor(hoveringHide ? new Color(255, 255, 255, 200) : new Color(255, 255, 255, 110));
			g.setStroke(new BasicStroke(2));
			g.drawLine(button.x + 4, button.y + button.height / 2, button.x + button.width - 4, button.y + button.height / 2);
			g.setStroke(new BasicStroke(1));
			g.setColor(new Color(255, 255, 255, 110));
			for (int i = 1; i <= 3; i++)
			{
				int offset = BORDER + i * 4;
				g.drawLine(panel.width - offset, panel.height - BORDER - 1, panel.width - BORDER - 1, panel.height - offset);
			}
		}
	}

	private boolean inGrip(Point point)
	{
		return point.x >= getWidth() - GRIP && point.y >= getHeight() - GRIP;
	}

	private Rectangle hideButton()
	{
		return new Rectangle(getWidth() - BORDER - BUTTON - 2, BORDER + 2, BUTTON, BUTTON);
	}

	private final class Mouse extends MouseAdapter
	{
		@Override
		public void mouseMoved(MouseEvent event)
		{
			boolean overHide = hideButton().contains(event.getPoint());
			if (overHide != hoveringHide)
			{
				hoveringHide = overHide;
				repaint();
			}
			int cursor = overHide ? Cursor.HAND_CURSOR : inGrip(event.getPoint()) ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR;
			setCursor(Cursor.getPredefinedCursor(cursor));
		}

		@Override
		public void mouseExited(MouseEvent event)
		{
			if (hoveringHide)
			{
				hoveringHide = false;
				repaint();
			}
		}

		@Override
		public void mousePressed(MouseEvent event)
		{
			if (event.getButton() == MouseEvent.BUTTON1 && hideButton().contains(event.getPoint()))
			{
				dragStart = null;
				onHide.run();
				return;
			}
			dragStart = event.getLocationOnScreen();
			windowStart = getLocation();
			sizeStart = getSize();
			dragged = false;
			resizing = inGrip(event.getPoint());
		}

		@Override
		public void mouseDragged(MouseEvent event)
		{
			if (dragStart == null) return;
			Point now = event.getLocationOnScreen();
			int dx = now.x - dragStart.x;
			int dy = now.y - dragStart.y;
			if (!dragged && Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) return;
			dragged = true;
			if (resizing)
			{
				// Follow whichever way the mouse went further; the height always keeps the game's shape.
				int width = LookoutGeometry.widthForDrag(sizeStart, dx, dy, source);
				setSize(LookoutGeometry.size(width, source));
			}
			else
			{
				setLocation(windowStart.x + dx, windowStart.y + dy);
			}
		}

		@Override
		public void mouseReleased(MouseEvent event)
		{
			if (dragStart == null) return;
			dragStart = null;
			if (dragged && resizing) onResized.accept(getWidth());
			else if (dragged) onMoved.accept(getLocation());
			else if (event.getButton() == MouseEvent.BUTTON1) onClick.run();
			resizing = false;
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent event)
		{
			onScroll.accept(event.getWheelRotation());
		}
	}
}
