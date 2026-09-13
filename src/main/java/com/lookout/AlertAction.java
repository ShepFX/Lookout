package com.lookout;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AlertAction
{
	FLASH("Flash the window"),
	FOCUS_CLIENT("Bring RuneLite to the front"),
	BOTH("Flash and bring RuneLite to the front");

	private final String label;

	boolean flashes()
	{
		return this != FOCUS_CLIENT;
	}

	boolean focuses()
	{
		return this != FLASH;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
