package com.healindicators;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HealIndicatorsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HealIndicatorsPlugin.class);
		RuneLite.main(args);
	}
}
