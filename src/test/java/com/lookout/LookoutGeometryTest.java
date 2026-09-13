package com.lookout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;

public class LookoutGeometryTest
{
	private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1040);
	private static final Dimension SIZE = new Dimension(400, 263);

	@Test
	public void sizeFollowsTheGamesShape()
	{
		assertEquals(new Dimension(400, 263), LookoutGeometry.size(400, null));
		assertEquals(new Dimension(400, 225), LookoutGeometry.size(400, new Dimension(1920, 1080)));
		assertEquals(new Dimension(LookoutGeometry.MIN_WIDTH, 105), LookoutGeometry.size(10, new Dimension(765, 503)));
		assertEquals(LookoutGeometry.MAX_WIDTH, LookoutGeometry.size(99999, null).width);
	}

	@Test
	public void letterboxCentresTheGame()
	{
		Rectangle box = LookoutGeometry.letterbox(new Dimension(400, 300), new Dimension(1600, 900));
		assertEquals(new Rectangle(0, 37, 400, 225), box);
		Rectangle tall = LookoutGeometry.letterbox(new Dimension(400, 300), new Dimension(500, 1000));
		assertEquals(new Rectangle(125, 0, 150, 300), tall);
		assertEquals(new Rectangle(0, 0, 400, 300), LookoutGeometry.letterbox(new Dimension(400, 300), null));
	}

	@Test
	public void cornersRespectTheMargin()
	{
		assertEquals(new Point(16, 16), LookoutGeometry.place(Corner.TOP_LEFT, 16, SCREEN, SIZE));
		assertEquals(new Point(1504, 16), LookoutGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE));
		assertEquals(new Point(16, 761), LookoutGeometry.place(Corner.BOTTOM_LEFT, 16, SCREEN, SIZE));
		assertEquals(new Point(1504, 761), LookoutGeometry.place(Corner.BOTTOM_RIGHT, 16, SCREEN, SIZE));
	}

	@Test
	public void secondMonitorOffsetsAreKept()
	{
		Rectangle right = new Rectangle(1920, 0, 2560, 1400);
		assertEquals(new Point(4064, 1121), LookoutGeometry.place(Corner.BOTTOM_RIGHT, 16, right, SIZE));
	}

	@Test
	public void clampKeepsTheWindowOnScreen()
	{
		assertEquals(new Point(0, 0), LookoutGeometry.clamp(new Point(-50, -50), SIZE, SCREEN));
		assertEquals(new Point(1520, 777), LookoutGeometry.clamp(new Point(5000, 5000), SIZE, SCREEN));
		assertEquals(new Point(300, 200), LookoutGeometry.clamp(new Point(300, 200), SIZE, SCREEN));
		// A window bigger than the screen sits at the top-left rather than off the left edge.
		assertEquals(new Point(0, 0), LookoutGeometry.clamp(new Point(100, 100), new Dimension(3000, 2000), SCREEN));
	}

	@Test
	public void positionsRoundTrip()
	{
		assertEquals(new Point(123, -4), LookoutGeometry.parsePosition(LookoutGeometry.formatPosition(new Point(123, -4))));
		assertNull(LookoutGeometry.parsePosition(""));
		assertNull(LookoutGeometry.parsePosition(null));
		assertNull(LookoutGeometry.parsePosition("12"));
		assertNull(LookoutGeometry.parsePosition("a,b"));
	}

	@Test
	public void scrollingResizesByTenPercent()
	{
		assertEquals(440, LookoutGeometry.scrolledWidth(400, -1));
		assertEquals(364, LookoutGeometry.scrolledWidth(400, 1));
		assertEquals(LookoutGeometry.MIN_WIDTH, LookoutGeometry.scrolledWidth(170, 5));
		assertEquals(LookoutGeometry.MAX_WIDTH, LookoutGeometry.scrolledWidth(1590, -5));
	}

	@Test
	public void cornerDragFollowsTheLargerMovement()
	{
		Dimension start = new Dimension(400, 263);
		Dimension source = new Dimension(765, 503);
		assertEquals(500, LookoutGeometry.widthForDrag(start, 100, 10, source));
		// Mostly downward: the new height, converted to a width.
		assertEquals(552, LookoutGeometry.widthForDrag(start, 10, 100, source));
		assertEquals(LookoutGeometry.MIN_WIDTH, LookoutGeometry.widthForDrag(start, -1000, -1000, source));
	}

	@Test
	public void avoidsTheClientWindowWhenACornerIsClear()
	{
		// RuneLite fills the right two thirds of the screen: both right corners are covered.
		Rectangle client = new Rectangle(640, 0, 1280, 1040);
		assertEquals(new Point(16, 761), LookoutGeometry.place(Corner.BOTTOM_RIGHT, 16, SCREEN, SIZE, client));
		assertEquals(new Point(16, 16), LookoutGeometry.place(Corner.TOP_RIGHT, 16, SCREEN, SIZE, client));
		// A clear preferred corner is left alone.
		assertEquals(new Point(16, 761), LookoutGeometry.place(Corner.BOTTOM_LEFT, 16, SCREEN, SIZE, client));
		// A maximised client covers every corner, so the preferred one is used regardless.
		assertEquals(new Point(1504, 761), LookoutGeometry.place(Corner.BOTTOM_RIGHT, 16, SCREEN, SIZE, SCREEN));
		assertEquals(new Point(1504, 761), LookoutGeometry.place(Corner.BOTTOM_RIGHT, 16, SCREEN, SIZE, null));
	}

	@Test
	public void knowsWhenNoCornerIsClear()
	{
		assertEquals(true, LookoutGeometry.hasClearCorner(16, SCREEN, SIZE, new Rectangle(640, 0, 1280, 1040)));
		assertEquals(false, LookoutGeometry.hasClearCorner(16, SCREEN, SIZE, new Rectangle(50, 50, 1800, 950)));
		assertEquals(true, LookoutGeometry.hasClearCorner(16, SCREEN, SIZE, null));
	}
}
