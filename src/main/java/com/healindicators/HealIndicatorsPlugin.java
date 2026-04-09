package com.healindicators;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Heal Indicators+",
	description = "Displays heal splats whenever your visible HP increases",
	tags = {"healing", "hitsplat", "food", "blood", "hitpoints"}
)
public class HealIndicatorsPlugin extends Plugin
{
	static final int SPLAT_MAX_VALUE = 99;
	private static final int MAX_SPLATS_PER_TICK = 4;
	private static final int STARTUP_WARMUP_TICKS = 2;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HealIndicatorsOverlay overlay;

	@Inject
	private HealIndicatorsConfig config;

	@Inject
	private ConfigManager configManager;

	private final List<HealSplatEntry> activeSplats = new ArrayList<>();
	private Integer lastVisibleHp;
	private Integer lastVisiblePrayer;
	private int lastProcessedTick = -1;
	private int warmupTicksRemaining = 0;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		resetState(true);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		resetState(true);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"healindicators".equals(event.getGroup()))
		{
			return;
		}

		if ("testHealSplatTrigger".equals(event.getKey()) && "true".equals(event.getNewValue()))
		{
			addTestSplat(SplatType.HEAL);
			configManager.setConfiguration("healindicators", "testHealSplatTrigger", false);
			return;
		}

		if ("testPrayerSplatTrigger".equals(event.getKey()) && "true".equals(event.getNewValue()))
		{
			addTestSplat(SplatType.PRAYER);
			configManager.setConfiguration("healindicators", "testPrayerSplatTrigger", false);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			resetState(false);
			return;
		}

		resetState(true);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		final int currentTick = client.getTickCount();
		if (currentTick == lastProcessedTick)
		{
			return;
		}
		lastProcessedTick = currentTick;

		final int currentVisibleHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		final int currentVisiblePrayer = client.getBoostedSkillLevel(Skill.PRAYER);
		if (lastVisibleHp == null)
		{
			lastVisibleHp = currentVisibleHp;
			lastVisiblePrayer = currentVisiblePrayer;
			return;
		}

		if (warmupTicksRemaining > 0)
		{
			warmupTicksRemaining--;
			lastVisibleHp = currentVisibleHp;
			lastVisiblePrayer = currentVisiblePrayer;
			pruneExpiredSplats();
			return;
		}

		final int healDelta = currentVisibleHp - lastVisibleHp;
		if (healDelta > 0 && config.enableHealSplats())
		{
			final boolean isRegenHeal = isNaturalHpRegeneration(healDelta);
			if (!(isRegenHeal && !config.showHealRegenerationSplats()))
			{
				addSplats(healDelta, SplatType.HEAL, config.splatDurationCycles());
			}
		}

		final int prayerDelta = currentVisiblePrayer - lastVisiblePrayer;
		if (prayerDelta > 0 && config.enablePrayerSplats())
		{
			final boolean isPrayerRegen = isPrayerRegenerationPotionTick(prayerDelta);
			if (!(isPrayerRegen && !config.showPrayerRegenerationSplats()))
			{
				addSplats(prayerDelta, SplatType.PRAYER, config.splatDurationCycles());
			}
		}

		lastVisibleHp = currentVisibleHp;
		lastVisiblePrayer = currentVisiblePrayer;
		pruneExpiredSplats();
	}

	List<HealSplatEntry> getActiveSplats()
	{
		return Collections.unmodifiableList(activeSplats);
	}

	private void addTestSplat(SplatType type)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}

		final int amount = ThreadLocalRandom.current().nextInt(1, 100);
		addSplats(amount, type, config.splatDurationCycles());
	}

	private boolean isNaturalHpRegeneration(int healDelta)
	{
		return healDelta == 1;
	}

	private boolean isPrayerRegenerationPotionTick(int prayerDelta)
	{
		return prayerDelta == 1;
	}

	private void addSplats(int totalAmount, SplatType type, int lifetimeCycles)
	{
		int remaining = totalAmount;
		int emitted = 0;
		final int cap = MAX_SPLATS_PER_TICK;
		final int startCycle = client.getGameCycle();
		final int effectiveLifetime = Math.max(1, lifetimeCycles);

		while (remaining > 0 && emitted < cap)
		{
			final int splatAmount = Math.min(remaining, SPLAT_MAX_VALUE);
			activeSplats.add(new HealSplatEntry(splatAmount, startCycle, startCycle + effectiveLifetime, type));
			remaining -= splatAmount;
			emitted++;
		}

		// Preserve total heal information if safety cap is reached.
		if (remaining > 0 && !activeSplats.isEmpty())
		{
			HealSplatEntry last = activeSplats.get(activeSplats.size() - 1);
			last.setAmount(Math.min(999, last.getAmount() + remaining));
		}
	}

	private void pruneExpiredSplats()
	{
		final int gameCycle = client.getGameCycle();
		activeSplats.removeIf(splat -> splat.getExpiresOnGameCycle() <= gameCycle);
	}

	private void resetState(boolean clearSplats)
	{
		lastVisibleHp = null;
		lastVisiblePrayer = null;
		lastProcessedTick = -1;
		warmupTicksRemaining = STARTUP_WARMUP_TICKS;

		if (clearSplats)
		{
			activeSplats.clear();
		}
	}

	@Provides
	HealIndicatorsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HealIndicatorsConfig.class);
	}

	@Getter
	static class HealSplatEntry
	{
		@Setter
		private int amount;
		private final int spawnedOnGameCycle;
		private final int expiresOnGameCycle;
		private final SplatType type;

		HealSplatEntry(int amount, int spawnedOnGameCycle, int expiresOnGameCycle, SplatType type)
		{
			this.amount = amount;
			this.spawnedOnGameCycle = spawnedOnGameCycle;
			this.expiresOnGameCycle = expiresOnGameCycle;
			this.type = type;
		}
	}

	enum SplatType
	{
		HEAL,
		PRAYER
	}
}
