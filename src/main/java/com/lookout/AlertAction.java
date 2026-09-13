package com.lookout;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AlertAction
{
	FLASH("Flash the window"),
	NOTIFY("Send a RuneLite notification"),
	BOTH("Flash and send a notification");

	private final String label;

	boolean flashes()
	{
		return this != NOTIFY;
	}

	boolean notifies()
	{
		return this != FLASH;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
