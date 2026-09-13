package com.lookout;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(LookoutConfig.GROUP)
public interface LookoutConfig extends Config
{
	String GROUP = "lookout";
	String KEY_WIDTH = "width";
	String KEY_POSITION = "position";
	String KEY_CORNER = "corner";

	@ConfigSection(name = "Showing", description = "When the Lookout window appears", position = 0)
	String showing = "showing";

	@ConfigSection(name = "Window", description = "Where it goes and what it looks like", position = 1)
	String window = "window";

	@ConfigSection(name = "Alerts", description = "What makes it flash or bring the client back", position = 2)
	String alerts = "alerts";

	@ConfigItem(keyName = "mode", name = "Show", description = "When the Lookout window is shown", section = showing, position = 0)
	default LookoutMode mode()
	{
		return LookoutMode.WHEN_UNFOCUSED;
	}

	@ConfigItem(keyName = "delay", name = "Show after", description = "How long RuneLite must be unfocused before the window appears", section = showing, position = 1)
	@Units(Units.MILLISECONDS)
	@Range(max = 10000)
	default int delay()
	{
		return 1500;
	}

	@ConfigItem(keyName = "hotkey", name = "Show / hide hotkey", description = "Shows the window, or hides it. A window shown this way stays up until you press it again, even with RuneLite focused. Only works while RuneLite is the active window - other programs keep their own shortcuts", section = showing, position = 2)
	default Keybind hotkey()
	{
		return new Keybind(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@ConfigItem(keyName = "clickAction", name = "On click", description = "What a click on the Lookout window does. Dragging always moves it", section = showing, position = 3)
	default ClickAction clickAction()
	{
		return ClickAction.HIDE_LOOKOUT;
	}

	@ConfigItem(keyName = KEY_CORNER, name = "Corner", description = "Which corner of the screen the window sits in, until you drag it somewhere else", section = window, position = 0)
	default Corner corner()
	{
		return Corner.BOTTOM_RIGHT;
	}

	@ConfigItem(keyName = "avoidClient", name = "Keep off RuneLite", description = "When the RuneLite window is still on screen, use a corner beside it if there is one. If there is not, wait until something covers RuneLite where the mouse is before showing", section = window, position = 1)
	default boolean avoidClient()
	{
		return true;
	}

	@ConfigItem(keyName = "margin", name = "Margin", description = "Gap between the window and the edge of the screen", section = window, position = 2)
	@Units(Units.PIXELS)
	@Range(max = 200)
	default int margin()
	{
		return 16;
	}

	@ConfigItem(keyName = KEY_WIDTH, name = "Width", description = "Width of the window; the height follows the game's shape. Dragging the bottom-right corner or scrolling over the window changes this too", section = window, position = 3)
	@Units(Units.PIXELS)
	@Range(min = LookoutGeometry.MIN_WIDTH, max = LookoutGeometry.MAX_WIDTH)
	default int width()
	{
		return 400;
	}

	@ConfigItem(keyName = "opacity", name = "Opacity", description = "How solid the window is", section = window, position = 4)
	@Units(Units.PERCENT)
	@Range(min = 20, max = 100)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(keyName = "frameRate", name = "Frame rate", description = "How many times a second the window is redrawn. Lower is cheaper", section = window, position = 5)
	@Range(min = 1, max = 30)
	default int frameRate()
	{
		return 15;
	}

	@ConfigItem(keyName = "alertAction", name = "On alert", description = "Flash the Lookout window, send a RuneLite notification (which follows your notification settings), or both", section = alerts, position = 0)
	default AlertAction alertAction()
	{
		return AlertAction.FLASH;
	}

	@ConfigItem(keyName = "lowHitpoints", name = "Low hitpoints", description = "Alert while hitpoints are at or below this percentage. 0 turns it off", section = alerts, position = 1)
	@Units(Units.PERCENT)
	@Range(max = 99)
	default int lowHitpoints()
	{
		return 25;
	}

	@ConfigItem(keyName = "alertOnAttack", name = "Under attack", description = "Alert when something hits you or starts attacking you", section = alerts, position = 2)
	default boolean alertOnAttack()
	{
		return true;
	}

	@ConfigItem(keyName = "alertOnOffer", name = "Offer completes", description = "Alert when a Grand Exchange offer finishes buying or selling", section = alerts, position = 3)
	default boolean alertOnOffer()
	{
		return true;
	}

	@ConfigItem(keyName = "alertOnPrivateMessage", name = "Private message", description = "Alert when you receive a private message", section = alerts, position = 4)
	default boolean alertOnPrivateMessage()
	{
		return true;
	}

	@ConfigItem(keyName = "alertOnNotification", name = "Any notification", description = "Alert whenever RuneLite or another plugin sends a notification, such as the idle notifier", section = alerts, position = 5)
	default boolean alertOnNotification()
	{
		return true;
	}

	/** "x,y" of where the window was last dragged to, or empty to use the corner. */
	@ConfigItem(keyName = KEY_POSITION, name = "", description = "", hidden = true)
	default String position()
	{
		return "";
	}
}
