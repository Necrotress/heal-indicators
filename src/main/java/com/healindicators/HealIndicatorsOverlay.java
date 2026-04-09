package com.healindicators;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.ImageUtil;

public class HealIndicatorsOverlay extends Overlay
{
	private static final Class<?> PLUGIN_CLASS = HealIndicatorsOverlay.class;
	private static final int FADE_OUT_CYCLES = 8;
	private static final int RISE_PIXELS = 20;
	private static final int SPLAT_BASELINE_Y_OFFSET = 40;
	private static final BufferedImage HEAL_SPLAT_ICON = ImageUtil.loadImageResource(PLUGIN_CLASS, "/heal_splat.png");
	private static final BufferedImage PRAYER_SPLAT_ICON = ImageUtil.loadImageResource(PLUGIN_CLASS, "/prayer_splat.png");
	private static final String ANCHOR_TEXT = "99";
	private static final float LUMA_R = 0.2126f;
	private static final float LUMA_G = 0.7152f;
	private static final float LUMA_B = 0.0722f;

	private Color cachedHealColor;
	private BufferedImage cachedHealTintedIcon;
	private int cachedHealIconSizePercent;
	private Color cachedPrayerColor;
	private BufferedImage cachedPrayerTintedIcon;
	private int cachedPrayerIconSizePercent;
	private final Map<HealIndicatorsPlugin.HealSplatEntry, ClusterAssignment> clusterAssignments = new IdentityHashMap<>();

	@Inject
	private HealIndicatorsPlugin plugin;

	@Inject
	private HealIndicatorsConfig config;

	@Inject
	private Client client;

	@Inject
	HealIndicatorsOverlay()
	{
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableHealSplats() && !config.enablePrayerSplats())
		{
			return null;
		}

		final Actor localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		final List<HealIndicatorsPlugin.HealSplatEntry> splats = plugin.getActiveSplats();
		if (splats.isEmpty())
		{
			return null;
		}

		final Font previousFont = graphics.getFont();
		Font baseFont = FontManager.getRunescapeSmallFont();

		final int currentCycle = client.getGameCycle();
		final Set<HealIndicatorsPlugin.HealSplatEntry> currentlyRendered = new HashSet<>();
		final Map<HealIndicatorsConfig.SplatAnchor, Set<Integer>> usedSlotsByAnchor =
			new EnumMap<>(HealIndicatorsConfig.SplatAnchor.class);

		for (int i = 0; i < splats.size(); i++)
		{
			HealIndicatorsPlugin.HealSplatEntry splat = splats.get(i);
			if (splat.getType() == HealIndicatorsPlugin.SplatType.HEAL && !config.enableHealSplats())
			{
				continue;
			}
			if (splat.getType() == HealIndicatorsPlugin.SplatType.PRAYER && !config.enablePrayerSplats())
			{
				continue;
			}

			final int remaining = splat.getExpiresOnGameCycle() - currentCycle;
			if (remaining <= 0)
			{
				continue;
			}

			final float fontSize = splat.getType() == HealIndicatorsPlugin.SplatType.PRAYER
				? config.prayerTextFontSize()
				: config.healTextFontSize();
			graphics.setFont(baseFont.deriveFont(fontSize));

			final String text = Integer.toString(splat.getAmount());
			final BufferedImage splatIcon = getTintedIcon(splat.getType());
			final Point anchor = resolveAnchor(graphics, localPlayer, splat.getType(), splatIcon);
			if (anchor == null)
			{
				continue;
			}

			final int totalLifetime = Math.max(1, splat.getExpiresOnGameCycle() - splat.getSpawnedOnGameCycle());
			final double progress = 1.0 - (remaining / (double) totalLifetime);
			final boolean riseEnabled = config.riseEffectEnabled();
			final int rise = riseEnabled ? (int) Math.round(progress * RISE_PIXELS) : 0;
			final float alpha = remaining < FADE_OUT_CYCLES ? (remaining / (float) FADE_OUT_CYCLES) : 1.0f;
			final HealIndicatorsConfig.SplatAnchor configuredAnchor = config.splatAnchor();
			final int slot = resolveClusterSlot(splat, configuredAnchor, usedSlotsByAnchor);
			currentlyRendered.add(splat);

			final int x = anchor.getX() + clusterXOffset(slot) - 6;
			final int y = anchor.getY() + SPLAT_BASELINE_Y_OFFSET + clusterYOffset(slot) - rise;
			drawSplat(graphics, x, y, text, alpha, splatIcon, config.textOutlineEnabled());
		}

		clusterAssignments.entrySet().removeIf(entry -> !currentlyRendered.contains(entry.getKey()));

		graphics.setFont(previousFont);
		return null;
	}

	private int resolveClusterSlot(
		HealIndicatorsPlugin.HealSplatEntry splat,
		HealIndicatorsConfig.SplatAnchor anchor,
		Map<HealIndicatorsConfig.SplatAnchor, Set<Integer>> usedSlotsByAnchor)
	{
		final Set<Integer> usedSlots = usedSlotsByAnchor.computeIfAbsent(anchor, key -> new HashSet<>());
		ClusterAssignment assignment = clusterAssignments.get(splat);
		if (assignment == null || assignment.anchor != anchor || usedSlots.contains(assignment.slot))
		{
			final int slot = firstAvailableSlot(usedSlots);
			assignment = new ClusterAssignment(anchor, slot);
			clusterAssignments.put(splat, assignment);
		}
		usedSlots.add(assignment.slot);
		return assignment.slot;
	}

	private static int firstAvailableSlot(Set<Integer> usedSlots)
	{
		int slot = 0;
		while (usedSlots.contains(slot))
		{
			slot++;
		}
		return slot;
	}

	private static int clusterXOffset(int index)
	{
		switch (index)
		{
			case 0:
			case 5:
				return 0;
			case 1:
			case 3:
			case 6:
				return -20;
			case 2:
			case 4:
			case 7:
				return 20;
			default:
				return 0;
		}
	}

	private static int clusterYOffset(int index)
	{
		switch (index)
		{
			case 1:
			case 2:
				return -15;
			case 0:
				return 0;
			case 3:
			case 4:
				return 15;
			case 5:
				return 30;
			case 6:
			case 7:
				return 45;
			default:
				return -30;
		}
	}

	private static final class ClusterAssignment
	{
		private final HealIndicatorsConfig.SplatAnchor anchor;
		private final int slot;

		private ClusterAssignment(HealIndicatorsConfig.SplatAnchor anchor, int slot)
		{
			this.anchor = anchor;
			this.slot = slot;
		}
	}

	private static void drawSplat(
		Graphics2D graphics,
		int iconX,
		int iconY,
		String text,
		float alpha,
		BufferedImage icon,
		boolean textOutlineEnabled)
	{
		if (icon == null)
		{
			return;
		}

		FontMetrics fm = graphics.getFontMetrics();

		final int textWidth = fm.stringWidth(text);
		final int textX = iconX + ((icon.getWidth() - textWidth) / 2);
		final int textY = iconY + ((icon.getHeight() + fm.getAscent() - fm.getDescent()) / 2);

		final Composite previousComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.drawImage(icon, iconX, iconY, null);
		if (textOutlineEnabled)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, textX - 1, textY);
			graphics.drawString(text, textX + 1, textY);
			graphics.drawString(text, textX, textY - 1);
			graphics.drawString(text, textX, textY + 1);
			graphics.drawString(text, textX - 1, textY - 1);
			graphics.drawString(text, textX + 1, textY - 1);
			graphics.drawString(text, textX - 1, textY + 1);
			graphics.drawString(text, textX + 1, textY + 1);
		}
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, textX, textY);
		graphics.setComposite(previousComposite);
	}

	private BufferedImage getTintedIcon(HealIndicatorsPlugin.SplatType type)
	{
		if (type == HealIndicatorsPlugin.SplatType.PRAYER)
		{
			final Color target = config.prayerSplatColor();
			final int sizePercent = config.prayerIconSizePercent();
			if (!target.equals(cachedPrayerColor) || cachedPrayerTintedIcon == null || cachedPrayerIconSizePercent != sizePercent)
			{
				cachedPrayerColor = target;
				cachedPrayerIconSizePercent = sizePercent;
				BufferedImage tinted = tintIconLuminance(PRAYER_SPLAT_ICON, target);
				cachedPrayerTintedIcon = scaleIcon(tinted, sizePercent);
			}
			return cachedPrayerTintedIcon;
		}

		final Color target = config.healSplatColor();
		final int sizePercent = config.healIconSizePercent();
		if (!target.equals(cachedHealColor) || cachedHealTintedIcon == null || cachedHealIconSizePercent != sizePercent)
		{
			cachedHealColor = target;
			cachedHealIconSizePercent = sizePercent;
			BufferedImage tinted = tintIconLuminance(HEAL_SPLAT_ICON, target);
			cachedHealTintedIcon = scaleIcon(tinted, sizePercent);
		}
		return cachedHealTintedIcon;
	}

	private Point resolveAnchor(Graphics2D graphics, Actor localPlayer, HealIndicatorsPlugin.SplatType type, BufferedImage icon)
	{
		HealIndicatorsConfig.SplatAnchor anchor = config.splatAnchor();
		int zOffset = anchor == HealIndicatorsConfig.SplatAnchor.ABOVE_HEAD
			? localPlayer.getLogicalHeight() + 30
			: Math.max(0, (localPlayer.getLogicalHeight() / 2) + 15);

		if (icon != null)
		{
			return localPlayer.getCanvasImageLocation(icon, zOffset);
		}

		return localPlayer.getCanvasTextLocation(graphics, ANCHOR_TEXT, zOffset);
	}

	private static BufferedImage tintIconLuminance(BufferedImage source, Color tint)
	{
		if (source == null)
		{
			return null;
		}

		BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		final float tintR = tint.getRed() / 255.0f;
		final float tintG = tint.getGreen() / 255.0f;
		final float tintB = tint.getBlue() / 255.0f;

		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < source.getWidth(); x++)
			{
				final int argb = source.getRGB(x, y);
				final int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0)
				{
					tinted.setRGB(x, y, 0);
					continue;
				}

				final int red = (argb >>> 16) & 0xFF;
				final int green = (argb >>> 8) & 0xFF;
				final int blue = argb & 0xFF;
				final float luminance = ((LUMA_R * red) + (LUMA_G * green) + (LUMA_B * blue)) / 255.0f;

				final int outR = clamp255(Math.round(255.0f * tintR * luminance));
				final int outG = clamp255(Math.round(255.0f * tintG * luminance));
				final int outB = clamp255(Math.round(255.0f * tintB * luminance));
				final int outArgb = (alpha << 24) | (outR << 16) | (outG << 8) | outB;
				tinted.setRGB(x, y, outArgb);
			}
		}

		return tinted;
	}

	private static int clamp255(int value)
	{
		return Math.max(0, Math.min(255, value));
	}

	private static BufferedImage scaleIcon(BufferedImage source, int sizePercent)
	{
		if (source == null)
		{
			return null;
		}
		if (sizePercent == 100)
		{
			return source;
		}

		int width = Math.max(1, Math.round(source.getWidth() * (sizePercent / 100.0f)));
		int height = Math.max(1, Math.round(source.getHeight() * (sizePercent / 100.0f)));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = scaled.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.drawImage(source, 0, 0, width, height, null);
		g2.dispose();
		return scaled;
	}
}
