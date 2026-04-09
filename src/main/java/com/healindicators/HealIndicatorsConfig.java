package com.healindicators;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("healindicators")
public interface HealIndicatorsConfig extends Config
{
	enum SplatAnchor
	{
		ABOVE_HEAD("Head"),
		BODY("Body");

		private final String displayName;

		SplatAnchor(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	@ConfigSection(
		name = "Heal Splats",
		description = "Settings for hitpoint heal splats",
		position = 4,
		closedByDefault = false
	)
	String healSection = "healSection";

	@ConfigSection(
		name = "Prayer Splats",
		description = "Settings for prayer restore splats",
		position = 5,
		closedByDefault = true
	)
	String prayerSection = "prayerSection";

	@ConfigItem(
		keyName = "riseEffectEnabled",
		name = "Rise effect",
		description = "Make both heal and prayer splats rise upward over time",
		position = 0
	)
	default boolean riseEffectEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "splatAnchor",
		name = "Position",
		description = "Where all splats render around the player",
		position = 1
	)
	default SplatAnchor splatAnchor()
	{
		return SplatAnchor.BODY;
	}

	@Range(min = 10, max = 120)
	@ConfigItem(
		keyName = "splatDurationCycles",
		name = "Splat duration (cycles)",
		description = "How long all splats stay visible",
		position = 2
	)
	default int splatDurationCycles()
	{
		return 45;
	}

	@ConfigItem(
		keyName = "textOutlineEnabled",
		name = "Text outline",
		description = "Draw a black outline around splat numbers",
		position = 3
	)
	default boolean textOutlineEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableHealSplats",
		name = "Enable heal splats",
		description = "Show splats when your visible hitpoints increase",
		position = 0,
		section = healSection
	)
	default boolean enableHealSplats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHealRegenerationSplats",
		name = "Show regeneration",
		description = "Show heal splats for passive healing",
		position = 1,
		section = healSection
	)
	default boolean showHealRegenerationSplats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "healSplatColor",
		name = "Splat color",
		description = "Tint color applied to the heal splat icon",
		position = 2,
		section = healSection
	)
	default Color healSplatColor()
	{
		return new Color(250, 0, 255);
	}

	@Range(min = 50, max = 200)
	@ConfigItem(
		keyName = "healIconSizePercent",
		name = "Splat size (%)",
		description = "Scale of the heal icon",
		position = 3,
		section = healSection
	)
	default int healIconSizePercent()
	{
		return 100;
	}

	@Range(min = 8, max = 24)
	@ConfigItem(
		keyName = "healTextFontSize",
		name = "Font size",
		description = "Number size for heal splats",
		position = 4,
		section = healSection
	)
	default int healTextFontSize()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "testHealSplatTrigger",
		name = "Test heal splat",
		description = "Toggle to spawn a random heal test splat",
		position = 5,
		section = healSection
	)
	default boolean testHealSplatTrigger()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enablePrayerSplats",
		name = "Enable prayer splats",
		description = "Show splats when your visible prayer points increase",
		position = 0,
		section = prayerSection
	)
	default boolean enablePrayerSplats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerRegenerationSplats",
		name = "Show regeneration",
		description = "Show prayer splats for prayer regeneration",
		position = 1,
		section = prayerSection
	)
	default boolean showPrayerRegenerationSplats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerSplatColor",
		name = "Splat color",
		description = "Tint color applied to the prayer splat icon",
		position = 2,
		section = prayerSection
	)
	default Color prayerSplatColor()
	{
		return new Color(255, 255, 255);
	}

	@Range(min = 50, max = 200)
	@ConfigItem(
		keyName = "prayerIconSizePercent",
		name = "Splat size (%)",
		description = "Scale of the prayer icon",
		position = 3,
		section = prayerSection
	)
	default int prayerIconSizePercent()
	{
		return 100;
	}

	@Range(min = 8, max = 24)
	@ConfigItem(
		keyName = "prayerTextFontSize",
		name = "Font size",
		description = "Number size for prayer splats",
		position = 4,
		section = prayerSection
	)
	default int prayerTextFontSize()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "testPrayerSplatTrigger",
		name = "Test prayer splat",
		description = "Toggle to spawn a random prayer test splat",
		position = 5,
		section = prayerSection
	)
	default boolean testPrayerSplatTrigger()
	{
		return false;
	}
}
