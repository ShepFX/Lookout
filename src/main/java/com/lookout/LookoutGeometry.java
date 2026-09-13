package com.lookout;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Comparator;

/** Where the Lookout window goes and how the game fits inside it. Pure arithmetic, so it is tested. */
final class LookoutGeometry
{
	static final int MIN_WIDTH = 160;
	static final int MAX_WIDTH = 1600;

	private LookoutGeometry()
	{
	}

	/** The window size for a given width, keeping the game's aspect ratio. */
	static Dimension size(int width, Dimension source)
	{
		int clamped = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
		if (source == null || source.width <= 0 || source.height <= 0)
		{
			// The classic 765x503 client until a frame tells us otherwise.
			return new Dimension(clamped, Math.round(clamped * 503f / 765f));
		}
		return new Dimension(clamped, Math.max(1, Math.round(clamped * (float) source.height / source.width)));
	}

	/** The rectangle the game is drawn in, letterboxed inside the panel. */
	static Rectangle letterbox(Dimension panel, Dimension source)
	{
		if (source == null || source.width <= 0 || source.height <= 0) return new Rectangle(0, 0, panel.width, panel.height);
		float scale = Math.min(panel.width / (float) source.width, panel.height / (float) source.height);
		int width = Math.max(1, Math.round(source.width * scale));
		int height = Math.max(1, Math.round(source.height * scale));
		return new Rectangle((panel.width - width) / 2, (panel.height - height) / 2, width, height);
	}

	/** The top-left point that puts a window of {@code size} in the given corner of the work area. */
	static Point place(Corner corner, int margin, Rectangle workArea, Dimension size)
	{
		int x = corner.isLeft() ? workArea.x + margin : workArea.x + workArea.width - size.width - margin;
		int y = corner.isTop() ? workArea.y + margin : workArea.y + workArea.height - size.height - margin;
		return clamp(new Point(x, y), size, workArea);
	}

	/**
	 * Like {@link #place}, but prefers a corner where the window would not sit on top of
	 * {@code avoid} - the RuneLite window, when it is still on screen. The preferred corner wins
	 * if it is clear; otherwise the first clear corner going round; otherwise the preferred corner
	 * anyway, since something else must be covering RuneLite for it to have lost focus.
	 */
	static Point place(Corner preferred, int margin, Rectangle workArea, Dimension size, Rectangle avoid)
	{
		Point first = place(preferred, margin, workArea, size);
		if (avoid == null || !new Rectangle(first, size).intersects(avoid)) return first;
		// Slide along the same edge before jumping to the other one.
		Corner[] fallbacks = Arrays.copyOf(Corner.values(), Corner.values().length);
		Arrays.sort(fallbacks, Comparator.comparingInt((Corner corner) -> corner.isTop() == preferred.isTop() ? 0 : 1)
			.thenComparingInt(corner -> corner.isLeft() == preferred.isLeft() ? 0 : 1));
		for (Corner corner : fallbacks)
		{
			if (corner == preferred) continue;
			Point candidate = place(corner, margin, workArea, size);
			if (!new Rectangle(candidate, size).intersects(avoid)) return candidate;
		}
		return first;
	}

	/** Whether any corner of the work area can hold the window without covering {@code avoid}. */
	static boolean hasClearCorner(int margin, Rectangle workArea, Dimension size, Rectangle avoid)
	{
		if (avoid == null) return true;
		for (Corner corner : Corner.values())
		{
			if (!new Rectangle(place(corner, margin, workArea, size), size).intersects(avoid)) return true;
		}
		return false;
	}

	/** Keeps a window on screen: fully visible if it fits, pinned to the top-left if it does not. */
	static Point clamp(Point point, Dimension size, Rectangle workArea)
	{
		int maxX = workArea.x + workArea.width - size.width;
		int maxY = workArea.y + workArea.height - size.height;
		int x = Math.max(workArea.x, Math.min(point.x, maxX));
		int y = Math.max(workArea.y, Math.min(point.y, maxY));
		return new Point(x, y);
	}

	/**
	 * The width a corner drag asks for. Dragging along the diagonal is natural, so the larger of the
	 * two movements wins, with the vertical one converted through the game's aspect ratio.
	 */
	static int widthForDrag(Dimension start, int dx, int dy, Dimension source)
	{
		float aspect = source == null || source.height <= 0 ? 765f / 503f : source.width / (float) source.height;
		int fromHeight = Math.round((start.height + dy) * aspect);
		int fromWidth = start.width + dx;
		int width = Math.abs(dx) >= Math.abs(dy * aspect) ? fromWidth : fromHeight;
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
	}

	/** Reads an "x,y" position saved by {@link #formatPosition}; null for anything else. */
	static Point parsePosition(String text)
	{
		if (text == null) return null;
		String[] parts = text.trim().split(",");
		if (parts.length != 2) return null;
		try
		{
			return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	static String formatPosition(Point point)
	{
		return point.x + "," + point.y;
	}

	/** A new width after a scroll of {@code notches}: ten percent per notch, within the limits. */
	static int scrolledWidth(int width, int notches)
	{
		double factor = Math.pow(1.1, -notches);
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, (int) Math.round(width * factor)));
	}
}
