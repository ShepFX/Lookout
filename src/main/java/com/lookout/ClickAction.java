package com.lookout;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ClickAction
{
	HIDE_LOOKOUT("Hide the Lookout window"),
	NOTHING("Nothing");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
