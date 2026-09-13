package com.lookout;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum LookoutMode
{
	WHEN_UNFOCUSED("When RuneLite is not the active window"),
	HOTKEY_ONLY("Only with the hotkey");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
