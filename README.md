# Lookout

A [RuneLite](https://runelite.net) plugin that keeps a small, always-on-top view of the game in the
corner of your screen while you are in another window, and gets your attention when something in
the game needs it.

## What it does

- **Shows a live copy of the game** in a small window whenever RuneLite is not the active window,
  after a short delay so a quick alt-tab does not trigger it. Or only when you press the hotkey.
- **Flashes** - a thick coloured border, a colour wash over the picture, a banner saying why, and
  the RuneLite taskbar button - or brings RuneLite back to the front, your choice - when:
  - your hitpoints drop to a percentage you set (and stays red until they recover),
  - something hits you or starts attacking you,
  - a Grand Exchange offer finishes,
  - you receive a private message,
  - RuneLite or any plugin sends a notification, such as the idle notifier.
- **Click it** to bring RuneLite to the front, or the **–** in its corner to hide it until you are
  next back in RuneLite. **Drag it** to move it; it remembers where you put it,
  on whichever screen that was. **Drag the bottom-right corner** (or scroll over it) to resize.
- Sits in the corner you choose with a margin, at the width you choose, at the opacity you choose.
  If the RuneLite window is still on screen and there is a corner beside it, it uses that one. If
  there is not, it waits until something covers RuneLite where your mouse is - the one clue there
  is that you can no longer see the game - rather than sitting on top of a game you can see.
- Never takes keyboard focus, and stays off the taskbar and out of alt-tab.

It renders at a frame rate you set, 15 a second by default, and only while it is on screen, so it
costs nothing when RuneLite is focused.

## Settings

- **Show** - when RuneLite is not the active window, or only with the hotkey.
- **Show after** - how long RuneLite must be unfocused before the window appears.
- **Show / hide hotkey** - shows the window or hides it; a window shown this way stays up until you
  press it again, even with RuneLite focused. Ctrl+Shift+L by default. It only works while
  RuneLite is the active window: the Lookout window never takes keyboard focus, so shortcuts pressed
  in another program go to that program.
- **On click** - bring RuneLite to the front, hide the window, or nothing.
- **Corner**, **Keep off RuneLite**, **Margin**, **Width**, **Opacity**, **Frame rate**.
- **On alert** - flash, bring RuneLite to the front, or both.
- **Low hitpoints**, **Under attack**, **Offer completes**, **Private message**, **Any notification**.

Choosing a corner forgets a dragged position.

## Notes

Everything happens inside the client: the window shows frames RuneLite has already drawn and never
sends input to the game. Nothing leaves your computer.

## Building

Requires JDK 11 or newer.

```
./gradlew build
```

The plugin can be run in a development client with `./gradlew run`.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
