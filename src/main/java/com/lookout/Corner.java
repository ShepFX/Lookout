package com.lookout;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Corner
{
	TOP_LEFT("Top left", true, true),
	TOP_RIGHT("Top right", false, true),
	BOTTOM_LEFT("Bottom left", true, false),
	BOTTOM_RIGHT("Bottom right", false, false);

	private final String label;
	private final boolean left;
	private final boolean top;

	@Override
	public String toString()
	{
		return label;
	}
}
