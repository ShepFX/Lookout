package com.lookout;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.Notifier;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Lookout",
	description = "A small always-on-top view of the game while you are in another window, that flashes when something needs you",
	tags = {"picture", "pip", "mini", "window", "afk", "focus", "always on top", "alert"}
)
public class LookoutPlugin extends Plugin
{
	private static final Color ATTACK = new Color(255, 70, 70);
	private static final Color OFFER = new Color(255, 190, 45);
	private static final Color MESSAGE = new Color(80, 155, 255);
	private static final Color NOTIFICATION = Color.WHITE;
	private static final int FLASH_MS = 4000;
	private static final String NOTIFICATION_PREFIX = "Lookout: ";
	/** Being hit again a moment later is the same fight, not a new alert. */
	private static final long ATTACK_REPEAT_MS = 6000;
	/** How often to look again while RuneLite is unfocused but probably still in view. */
	private static final int RECHECK_MS = 1000;

	@Inject private Client client;
	@Inject private Notifier notifier;
	@Inject private LookoutConfig config;
	@Inject private ConfigManager configManager;
	@Inject private DrawManager drawManager;
	@Inject private KeyManager keyManager;

	private final Map<Integer, GrandExchangeOfferState> offerStates = new HashMap<>();
	private final Runnable frameListener = this::onFrame;
	private final HotkeyListener hotkey = new HotkeyListener(() -> config.hotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			SwingUtilities.invokeLater(LookoutPlugin.this::togglePinned);
		}
	};
	private final WindowFocusListener focusListener = new WindowFocusListener()
	{
		@Override
		public void windowGainedFocus(WindowEvent event)
		{
			clientFocused();
		}

		@Override
		public void windowLostFocus(WindowEvent event)
		{
			clientUnfocused();
		}
	};

	// Swing thread state.
	private LookoutWindow window;
	private Window clientWindow;
	private Timer showTimer;
	private boolean pinned;
	private boolean autoShown;
	/** Set when the user dismissed the window; auto-show waits for RuneLite to be focused again first. */
	private boolean dismissed;
	// Client thread state.
	private long lastCaptureAt;
	private volatile boolean capturePending;
	private boolean lowHitpoints;
	private long lastAttackAt;

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(hotkey);
		drawManager.registerEveryFrameListener(frameListener);
		SwingUtilities.invokeLater(() ->
		{
			window = new LookoutWindow(this::clicked, this::hide, this::scrolled, this::resized, this::moved, this::sourceChanged);
			showTimer = new Timer(RECHECK_MS, event -> autoShow());
			showTimer.setRepeats(true);
			applyWindowConfig();
			attachToClientWindow();
		});
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkey);
		drawManager.unregisterEveryFrameListener(frameListener);
		offerStates.clear();
		lowHitpoints = false;
		capturePending = false;
		SwingUtilities.invokeLater(() ->
		{
			if (showTimer != null) showTimer.stop();
			if (clientWindow != null) clientWindow.removeWindowFocusListener(focusListener);
			if (window != null) window.dispose();
			window = null;
			clientWindow = null;
			showTimer = null;
			pinned = false;
			autoShown = false;
			dismissed = false;
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// The canvas can be parented after the plugin starts; keep trying until the window is found.
		if (clientWindow == null && window != null) SwingUtilities.invokeLater(this::attachToClientWindow);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LookoutConfig.GROUP.equals(event.getGroup())) return;
		// Picking a corner is a request to go there, so a dragged position no longer applies.
		if (LookoutConfig.KEY_CORNER.equals(event.getKey())) configManager.unsetConfiguration(LookoutConfig.GROUP, LookoutConfig.KEY_POSITION);
		SwingUtilities.invokeLater(this::applyWindowConfig);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			offerStates.clear();
			lowHitpoints = false;
			SwingUtilities.invokeLater(() -> { if (window != null) window.setSticky(null); });
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int threshold = config.lowHitpoints();
		int max = client.getRealSkillLevel(Skill.HITPOINTS);
		boolean low = threshold > 0 && max > 0 && client.getBoostedSkillLevel(Skill.HITPOINTS) * 100 <= threshold * max;
		if (low == lowHitpoints) return;
		lowHitpoints = low;
		SwingUtilities.invokeLater(() -> { if (window != null) window.setSticky(low ? ATTACK : null); });
		if (low) alert(ATTACK, "Low hitpoints");
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (config.alertOnAttack() && event.getActor() == client.getLocalPlayer()) attacked();
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (config.alertOnAttack() && event.getTarget() != null && event.getTarget() == client.getLocalPlayer()
			&& event.getSource() != client.getLocalPlayer())
		{
			attacked();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOfferState state = event.getOffer().getState();
		// Every slot reports its state on login; only a change seen while watching is news.
		GrandExchangeOfferState previous = offerStates.put(event.getSlot(), state);
		if (previous == null || previous == state) return;
		if (config.alertOnOffer() && (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)) alert(OFFER, "Offer complete");
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (config.alertOnPrivateMessage() && (type == ChatMessageType.PRIVATECHAT || type == ChatMessageType.MODPRIVATECHAT)) alert(MESSAGE, "Private message");
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		// Our own notifications must not come back round as alerts.
		if (event.getMessage() != null && event.getMessage().startsWith(NOTIFICATION_PREFIX)) return;
		if (config.alertOnNotification()) alert(NOTIFICATION, "Notification");
	}

	private void attacked()
	{
		long now = System.currentTimeMillis();
		if (now - lastAttackAt < ATTACK_REPEAT_MS) return;
		lastAttackAt = now;
		alert(ATTACK, "Under attack");
	}

	/**
	 * An alert only matters while the Lookout window is what the user can see. A notification goes
	 * through RuneLite's own notifier, so it does whatever the user's notification settings say.
	 */
	private void alert(Color color, String label)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window == null || !window.isVisible()) return;
			AlertAction action = config.alertAction();
			if (action.flashes()) window.flash(color, label, FLASH_MS);
			if (action.notifies()) notifier.notify(NOTIFICATION_PREFIX + label);
		});
	}

	// Called every frame on the client thread; asks for a copy of the frame at the configured rate.
	private void onFrame()
	{
		LookoutWindow target = window;
		if (target == null || !target.isVisible() || capturePending) return;
		long now = System.currentTimeMillis();
		if (now - lastCaptureAt < 1000 / Math.max(1, config.frameRate())) return;
		lastCaptureAt = now;
		capturePending = true;
		drawManager.requestNextFrameListener(image ->
		{
			capturePending = false;
			LookoutWindow current = window;
			if (current != null) current.showFrame(image);
		});
	}

	private void attachToClientWindow()
	{
		if (clientWindow != null || client.getCanvas() == null) return;
		Window found = SwingUtilities.getWindowAncestor(client.getCanvas());
		if (found == null) return;
		clientWindow = found;
		clientWindow.addWindowFocusListener(focusListener);
		if (!clientWindow.isFocused()) clientUnfocused();
	}

	private void clientFocused()
	{
		if (showTimer != null) showTimer.stop();
		autoShown = false;
		dismissed = false;
		updateVisibility();
	}

	private void clientUnfocused()
	{
		if (config.mode() != LookoutMode.WHEN_UNFOCUSED || dismissed || showTimer == null) return;
		showTimer.setInitialDelay(config.delay());
		showTimer.restart();
	}

	private void autoShow()
	{
		// Focus may have gone to another RuneLite window - a config dialog, say - rather than away.
		if (clientWindow == null || clientWindow.isFocused() || anyOwnWindowFocused()) return;
		if (config.mode() != LookoutMode.WHEN_UNFOCUSED || dismissed) return;
		if (clientProbablyVisible())
		{
			// Leave the timer running: the next click may cover RuneLite.
			return;
		}
		showTimer.stop();
		autoShown = true;
		updateVisibility();
	}

	/** Whether any window of this client has focus - the game, a config dialog, a detached panel. */
	private static boolean anyOwnWindowFocused()
	{
		for (Window window : Window.getWindows())
		{
			if (window.isFocused()) return true;
		}
		return false;
	}

	private void togglePinned()
	{
		pinned = !pinned;
		if (!pinned)
		{
			autoShown = false;
			dismissed = true;
		}
		updateVisibility();
	}

	private void updateVisibility()
	{
		if (window == null) return;
		boolean visible = pinned || autoShown;
		if (visible && !window.isVisible())
		{
			position();
			window.setVisible(true);
		}
		else if (!visible && window.isVisible())
		{
			window.setVisible(false);
		}
	}

	private void applyWindowConfig()
	{
		if (window == null) return;
		window.setOpacityPercent(config.opacity());
		if (config.mode() == LookoutMode.HOTKEY_ONLY)
		{
			autoShown = false;
			updateVisibility();
		}
		if (window.isVisible()) position();
	}

	private void position()
	{
		Dimension size = LookoutGeometry.size(config.width(), window.getSource());
		Point custom = LookoutGeometry.parsePosition(config.position());
		Point location;
		if (custom != null)
		{
			// A dragged window stays on whichever screen it was dragged to.
			location = LookoutGeometry.clamp(custom, size, workArea(screenContaining(custom)));
		}
		else
		{
			location = LookoutGeometry.place(config.corner(), config.margin(), workArea(clientScreen()), size, clientBounds());
		}
		window.setSize(size);
		window.setLocation(location);
	}

	/**
	 * Java cannot ask whether another program covers RuneLite, so this reads the one clue there is:
	 * RuneLite just lost focus, and if the mouse is inside its rectangle then whatever took the focus
	 * is sitting on top of it. A pointer outside that rectangle means the user clicked beside
	 * RuneLite and can still see it, unless there is a spare corner to show the window in anyway.
	 */
	private boolean clientProbablyVisible()
	{
		Rectangle bounds = clientBounds();
		if (bounds == null) return false;
		Dimension size = LookoutGeometry.size(config.width(), window.getSource());
		if (LookoutGeometry.hasClearCorner(config.margin(), workArea(clientScreen()), size, bounds)) return false;
		PointerInfo pointer = MouseInfo.getPointerInfo();
		return pointer != null && !bounds.contains(pointer.getLocation());
	}

	/** Where the RuneLite window is, or null when it is minimised or not to be avoided. */
	private Rectangle clientBounds()
	{
		if (!config.avoidClient() || clientWindow == null || !clientWindow.isShowing()) return null;
		if (clientWindow instanceof Frame && (((Frame) clientWindow).getExtendedState() & Frame.ICONIFIED) != 0) return null;
		return clientWindow.getBounds();
	}

	private GraphicsConfiguration clientScreen()
	{
		return clientWindow != null ? clientWindow.getGraphicsConfiguration() : window.getGraphicsConfiguration();
	}

	private GraphicsConfiguration screenContaining(Point point)
	{
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			GraphicsConfiguration graphics = device.getDefaultConfiguration();
			if (graphics.getBounds().contains(point)) return graphics;
		}
		return clientScreen();
	}

	/** The usable area of a screen, taskbar excluded. */
	private static Rectangle workArea(GraphicsConfiguration graphics)
	{
		Rectangle bounds = graphics.getBounds();
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
		return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
			bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
	}

	private void sourceChanged()
	{
		// The game changed shape - a resize, or fixed to resizable - so the window follows.
		SwingUtilities.invokeLater(() -> { if (window != null && window.isVisible()) position(); });
	}

	private void clicked()
	{
		if (config.clickAction() == ClickAction.HIDE_LOOKOUT) hide();
	}

	/** Hides the window until RuneLite has been focused again, so it does not come straight back. */
	private void hide()
	{
		pinned = false;
		autoShown = false;
		dismissed = true;
		updateVisibility();
	}

	private void scrolled(int notches)
	{
		resized(LookoutGeometry.scrolledWidth(config.width(), notches));
	}

	private void resized(int width)
	{
		if (width != config.width()) configManager.setConfiguration(LookoutConfig.GROUP, LookoutConfig.KEY_WIDTH, width);
	}

	private void moved(Point location)
	{
		configManager.setConfiguration(LookoutConfig.GROUP, LookoutConfig.KEY_POSITION, LookoutGeometry.formatPosition(location));
	}

	@Provides
	LookoutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LookoutConfig.class);
	}
}
