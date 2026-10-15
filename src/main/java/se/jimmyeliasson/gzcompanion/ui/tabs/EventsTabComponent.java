package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.events.EventSchedule;
import se.jimmyeliasson.gzcompanion.events.EventScheduleEntry;
import se.jimmyeliasson.gzcompanion.events.EventScheduleManager;
import se.jimmyeliasson.gzcompanion.events.EventScheduleSnapshot;
import se.jimmyeliasson.gzcompanion.events.EventScheduleStatus;
import se.jimmyeliasson.gzcompanion.events.EventScheduleView;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.EventsLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Events tab: GameZone's public event schedule (the same source as the in-game
 * hologram). Holds no fetch or JSON logic - it reads {@link EventScheduleManager#getSnapshot()},
 * asks {@link EventScheduleManager#ensureFresh} (TTL-guarded, so calling it every frame never
 * fetches every frame) and turns the cached schedule into an {@link EventScheduleView} for the
 * current instant, so passed events drop out and countdowns tick without refetching.
 */
public class EventsTabComponent {
    static final int DAY_HEADING_H = 12;
    static final int EVENT_ROW_H = 11;
    static final int GROUP_GAP = 3;
    private static final int LIST_PAD = 3;
    private static final int TIME_COL_W = 26;

    private int listScroll = 0;
    private final List<Hit> hitTargets = new ArrayList<>();
    private UiRect lastListRect;
    private int lastContentHeight;

    private record Hit(UiRect rect, Runnable action) {}

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        hitTargets.clear();
        EventScheduleManager manager = CompanionSession.getInstance().getEventScheduleManager();
        manager.ensureFresh();
        EventScheduleSnapshot snapshot = manager.getSnapshot();
        Instant now = Instant.now();
        EventsLayout layout = EventsLayout.calculate(bounds);

        renderHeader(extractor, font, layout.headerRect(), snapshot);

        EventSchedule schedule = snapshot.schedule();
        EventScheduleView view = EventScheduleView.of(schedule, now);
        if (schedule == null || view.next().isEmpty()) {
            lastListRect = null;
            renderState(extractor, font, layout.bodyRect(), snapshot, manager, mouseX, mouseY);
        } else {
            renderNextCard(extractor, font, layout.nextCardRect(), view.next().get(), schedule.zone(), now);
            renderList(extractor, font, layout.listRect(), view);
        }
        renderFooter(extractor, font, layout, snapshot, manager, now, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Pure text rules (tested without a Font)
    // ------------------------------------------------------------------

    /** Header freshness badge - same vocabulary as Bounties/Leaderboards. */
    static String badgeLabel(EventScheduleSnapshot snapshot) {
        return switch (snapshot.status()) {
            case LOADING -> snapshot.hasSchedule() ? "UPPDATERAR" : "LADDAR";
            case LOADED -> "LIVE";
            case STALE -> "CACHAD";
            case UNAVAILABLE, INCOMPATIBLE -> "OTILLGÄNGLIG";
            case IDLE -> "";
        };
    }

    /** Footer status line; the zone note is appended when the player's zone differs from the schedule's. */
    static String footerText(EventScheduleSnapshot snapshot, Instant now, ZoneId viewerZone) {
        String text;
        if (snapshot.status() == EventScheduleStatus.STALE) {
            text = "Visar senast hämtade schema";
        } else if (snapshot.fetchedAt() == null) {
            text = switch (snapshot.status()) {
                case LOADING, IDLE -> "Hämtar schema...";
                case UNAVAILABLE, INCOMPATIBLE -> "Kunde inte hämta eventschemat";
                default -> "";
            };
        } else {
            long minutes = Math.max(0, Duration.between(snapshot.fetchedAt(), now).toMinutes());
            text = minutes < 1 ? "Uppdaterad nyss" : "Uppdaterad för " + minutes + " min sedan";
        }
        if (snapshot.schedule() != null) {
            String note = EventScheduleView.zoneNote(snapshot.schedule().zone(), viewerZone, now);
            if (note != null) text = text.isEmpty() ? note : text + " • " + note;
        }
        return text;
    }

    /** Headline of the whole-body state shown when there is no next event to highlight. */
    static String stateHeadline(EventScheduleSnapshot snapshot) {
        if (snapshot.hasSchedule()) return "INGA KOMMANDE EVENTS";
        return switch (snapshot.status()) {
            case UNAVAILABLE -> "KUNDE INTE HÄMTA EVENTSCHEMAT";
            case INCOMPATIBLE -> "EVENTSCHEMAT KUNDE INTE LÄSAS";
            default -> "HÄMTAR EVENTSCHEMA...";
        };
    }

    static String stateBody(EventScheduleSnapshot snapshot) {
        if (snapshot.hasSchedule()) {
            return snapshot.status() == EventScheduleStatus.STALE
                    ? "Det senast hämtade schemat har inga fler events. Uppdatera för att se nya."
                    : "Det finns inga schemalagda events de närmaste dagarna.";
        }
        return switch (snapshot.status()) {
            case UNAVAILABLE -> "Servern svarade inte. Försök igen om en stund.";
            case INCOMPATIBLE -> "Svaret från GameZone kunde inte tolkas av den här versionen.";
            default -> "";
        };
    }

    /** Whether the state panel offers "Försök igen" (only when nothing could be fetched). */
    static boolean offersRetry(EventScheduleSnapshot snapshot) {
        return !snapshot.hasSchedule()
                && (snapshot.status() == EventScheduleStatus.UNAVAILABLE || snapshot.status() == EventScheduleStatus.INCOMPATIBLE);
    }

    /** Total height of the scrollable day-grouped list. */
    static int listContentHeight(EventScheduleView view) {
        int h = LIST_PAD;
        for (int i = 0; i < view.days().size(); i++) {
            if (i > 0) h += GROUP_GAP;
            h += DAY_HEADING_H + view.days().get(i).entries().size() * EVENT_ROW_H;
        }
        return h + LIST_PAD;
    }

    static int clampScroll(int offset, int contentH, int viewportH) {
        return Math.max(0, Math.min(offset, Math.max(0, contentH - viewportH)));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect bounds, EventScheduleSnapshot snapshot) {
        int y = bounds.y();
        GZTheme.drawIcon(extractor, IconId.EVENTS, bounds.x(), y + 1, 11, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Events", bounds.x() + 14, y + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String badge = badgeLabel(snapshot);
        if (!badge.isBlank()) {
            int dotColor = switch (snapshot.status()) {
                case LOADED -> GZTheme.COLOR_STATUS_GREEN;
                case STALE, LOADING -> GZTheme.COLOR_STATUS_YELLOW;
                default -> GZTheme.COLOR_STATUS_GREY;
            };
            int textW = TextUtil.scaledWidth(font, badge, TypographyScale.META.getScale());
            int badgeX = bounds.right() - textW - 10;
            GZTheme.drawStatusDot(extractor, badgeX, y + 4, dotColor);
            TextUtil.drawScaledText(extractor, font, badge, badgeX + 7, y + 1, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, "Kommande events på GameZone.",
                bounds.x(), y + 12, bounds.width(), TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderNextCard(GuiGraphicsExtractor extractor, Font font, UiRect card, EventScheduleEntry next, ZoneId zone, Instant now) {
        GZTheme.drawCard(extractor, card, 0x3310B981, GZTheme.COLOR_BORDER_EMERALD);
        int x = card.x() + 5;
        int maxW = card.width() - 10;

        String relative = EventScheduleView.relative(next, now, zone);
        int relW = Math.min(maxW / 2, TextUtil.scaledWidth(font, relative, TypographyScale.SMALL.getScale()));
        TextUtil.drawScaledEllipsizedText(extractor, font, relative, card.right() - 5 - relW, card.y() + 4, relW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
        TextUtil.drawScaledEllipsizedText(extractor, font, "NÄSTA EVENT", x, card.y() + 4, Math.max(10, maxW - relW - 6),
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        TextUtil.drawScaledEllipsizedText(extractor, font, next.eventName(), x, card.y() + 14, maxW,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String when = EventScheduleView.dayHeading(next.localDate(), now.atZone(zone).toLocalDate()) + " • " + EventScheduleView.time(next);
        TextUtil.drawScaledEllipsizedText(extractor, font, when, x, card.y() + 27, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect list, EventScheduleView view) {
        GZTheme.drawCard(extractor, list, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int contentH = listContentHeight(view);
        int viewportH = list.height() - 2;
        listScroll = clampScroll(listScroll, contentH, viewportH);
        lastListRect = list;
        lastContentHeight = contentH;

        EventScheduleEntry next = view.next().orElse(null);
        int x = list.x() + 4;
        int maxW = list.width() - 8;
        extractor.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);
        int y = list.y() + LIST_PAD - listScroll;
        for (int i = 0; i < view.days().size(); i++) {
            EventScheduleView.DayGroup day = view.days().get(i);
            if (i > 0) y += GROUP_GAP;
            TextUtil.drawScaledEllipsizedText(extractor, font, day.heading(), x, y + 2, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
            y += DAY_HEADING_H;
            for (EventScheduleEntry entry : day.entries()) {
                boolean isNext = entry == next;
                if (isNext) {
                    extractor.fill(list.x() + 1, y, list.right() - 1, y + EVENT_ROW_H, 0x2210B981);
                }
                TextUtil.drawScaledText(extractor, font, EventScheduleView.time(entry), x + 2, y + 2,
                        TypographyScale.SMALL.getScale(), isNext ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_SECONDARY, false);
                TextUtil.drawScaledEllipsizedText(extractor, font, entry.eventName(), x + 2 + TIME_COL_W, y + 2, Math.max(10, maxW - 2 - TIME_COL_W),
                        TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
                y += EVENT_ROW_H;
            }
        }
        extractor.disableScissor();
    }

    private void renderState(GuiGraphicsExtractor extractor, Font font, UiRect body, EventScheduleSnapshot snapshot,
                             EventScheduleManager manager, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, body, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int cx = body.x() + body.width() / 2;
        int maxW = body.width() - 12;
        int y = body.y() + Math.max(6, body.height() / 2 - 20);
        TextUtil.drawScaledCenteredText(extractor, font, stateHeadline(snapshot), cx, y, maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        y += 12;
        String body1 = stateBody(snapshot);
        if (!body1.isEmpty()) {
            y += TextUtil.drawScaledWrappedText(extractor, font, body1, body.x() + 6, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 4;
        }
        if (offersRetry(snapshot)) {
            int btnW = Math.min(maxW, 70);
            UiRect retry = new UiRect(cx - btnW / 2, Math.min(y + 2, body.bottom() - 14), btnW, 11);
            boolean can = manager.canManualRefresh();
            GZTheme.drawButton(extractor, font, retry, "Försök igen", true, can && retry.contains(mouseX, mouseY), TypographyScale.META.getScale());
            if (can) hitTargets.add(new Hit(retry, manager::manualRefresh));
        }
    }

    private void renderFooter(GuiGraphicsExtractor extractor, Font font, EventsLayout layout, EventScheduleSnapshot snapshot,
                              EventScheduleManager manager, Instant now, int mouseX, int mouseY) {
        UiRect text = layout.footerTextRect();
        int color = snapshot.status() == EventScheduleStatus.STALE ? GZTheme.COLOR_STATUS_YELLOW : GZTheme.COLOR_TEXT_MUTED;
        TextUtil.drawScaledEllipsizedText(extractor, font, footerText(snapshot, now, ZoneId.systemDefault()), text.x(), text.y() + 3, text.width(),
                TypographyScale.META.getScale(), color, false);

        UiRect refresh = layout.refreshBtnRect();
        boolean can = manager.canManualRefresh();
        GZTheme.drawButton(extractor, font, refresh, "Uppdatera", false, can && refresh.contains(mouseX, mouseY), TypographyScale.META.getScale());
        if (can) hitTargets.add(new Hit(refresh, manager::manualRefresh));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        for (Hit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (lastListRect == null || !lastListRect.contains(mouseX, mouseY)) return false;
        listScroll = clampScroll(listScroll - (int) (scrollY * 14), lastContentHeight, lastListRect.height() - 2);
        return true;
    }
}
