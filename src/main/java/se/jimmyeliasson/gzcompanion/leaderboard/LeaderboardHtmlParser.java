package se.jimmyeliasson.gzcompanion.leaderboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure HTML text-scanning for GameZone's public leaderboard pages - no network, no Minecraft state,
 * fully unit-testable with fixture strings. Deliberately NOT a general DOM parser: this project adds
 * no new external dependency for a narrow, purpose-built adapter over one specific, small, regular
 * HTML shape (see docs/LEADERBOARDS.md's "discovered public data contract" section for the exact
 * markup this was written against and why a full parser dependency wasn't used).
 *
 * <p>Value-column LABELS (e.g. "Coins", "Level") are never scraped from the page - they come from
 * {@link GameZoneLeaderboardRegistry}'s own {@link LeaderboardDefinition#valueLabel()}, passed in by
 * the caller. Only rank, name, and the raw value text are ever extracted here.
 *
 * <p>Resilience strategy: every class-name match is a SUBSTRING check (e.g. {@code "tableRow"}),
 * never an exact match - GameZone's site uses CSS Modules, whose class names carry a build-specific
 * hash prefix (e.g. {@code page-module__k8x8IG__tableRow}) that can change on every GameZone
 * deployment even when the semantic structure doesn't. A single malformed row (unparseable rank,
 * blank name, or blank value) is skipped, never thrown - only a COMPLETELY absent container marker
 * throws {@link LeaderboardIncompatibleException}, since that means the page's structure changed in
 * a way this parser cannot navigate at all, not just one bad row.
 */
public final class LeaderboardHtmlParser {
    private static final int MAX_ENTRIES = 10;

    private static final Pattern TAG_OR_COMMENT = Pattern.compile("<!--.*?-->|<[^>]*>", Pattern.DOTALL);

    // Matches one <div class="...tableRow...">'s opening tag, used only to locate row boundaries -
    // field extraction below re-scans within each row's own slice.
    private static final Pattern TABLE_ROW_START = Pattern.compile("<div class=\"[^\"]*tableRow[^\"]*\"[^>]*>");
    private static final Pattern TABLE_RANK = Pattern.compile("<span class=\"[^\"]*tableRank[^\"]*\"[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern ENTITY_LINK = Pattern.compile("<a class=\"[^\"]*entityLink[^\"]*\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TABLE_VALUE = Pattern.compile("<strong class=\"[^\"]*tableValue[^\"]*\"[^>]*>(.*?)</strong>", Pattern.DOTALL);

    // Server-stat single-value cards (the /leaderboards?tab=server overview page) - a different,
    // simpler markup shape from the per-board full-table pages above: one <article> per stat, an
    // <h3> title, an identity name, and exactly one value.
    private static final Pattern SERVER_CARD = Pattern.compile("<article class=\"[^\"]*boardCard[^\"]*\"[^>]*>(.*?)</article>", Pattern.DOTALL);
    private static final Pattern SERVER_TITLE = Pattern.compile("<h3>(.*?)</h3>", Pattern.DOTALL);
    private static final Pattern SERVER_IDENTITY_NAME = Pattern.compile("<div class=\"[^\"]*identity[^\"]*\"[^>]*>.*?<strong>(.*?)</strong>", Pattern.DOTALL);
    private static final Pattern SERVER_VALUE = Pattern.compile("<div class=\"[^\"]*value[^\"]*\"[^>]*><strong>(.*?)</strong>", Pattern.DOTALL);

    private LeaderboardHtmlParser() {}

    /**
     * Parses a per-board full-table page (e.g. {@code /leaderboards/settlement_treasury}) into up
     * to {@value #MAX_ENTRIES} ranked entries, ranks 1..N in document order - page 1 of that page's
     * own pagination always contains at least the first 25 ranks, so this never needs to fetch a
     * second page to reach the required top 10. Returns fewer than 10 (down to zero) when the
     * board genuinely has fewer legitimate records - never fabricates a missing rank.
     *
     * @param primaryLabel the value-column label to attach to every entry (from the registry, e.g. "Coins").
     * @throws LeaderboardIncompatibleException if the {@code tableRow} container marker is entirely
     *         absent - GameZone most likely restructured the page.
     */
    public static List<LeaderboardEntry> parseFullTableTop10(String html, String primaryLabel) throws LeaderboardIncompatibleException {
        if (html == null || !html.contains("tableRow")) {
            throw new LeaderboardIncompatibleException("Expected 'tableRow' markup was not found in the fetched page.");
        }

        List<String> rowSlices = sliceRows(html);
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (String rowHtml : rowSlices) {
            if (entries.size() >= MAX_ENTRIES) break;
            parseOneFullTableRow(rowHtml, primaryLabel).ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    /** Splits the document into one string per {@code tableRow} div, from each row's start to the next row's start (or end of document). */
    private static List<String> sliceRows(String html) {
        List<Integer> starts = new ArrayList<>();
        Matcher m = TABLE_ROW_START.matcher(html);
        while (m.find()) {
            starts.add(m.end());
        }
        List<String> slices = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : html.length();
            slices.add(html.substring(from, Math.min(to, html.length())));
        }
        return slices;
    }

    private static Optional<LeaderboardEntry> parseOneFullTableRow(String rowHtml, String primaryLabel) {
        Matcher rankMatcher = TABLE_RANK.matcher(rowHtml);
        Matcher nameMatcher = ENTITY_LINK.matcher(rowHtml);
        Matcher valueMatcher = TABLE_VALUE.matcher(rowHtml);

        if (!rankMatcher.find() || !nameMatcher.find() || !valueMatcher.find()) {
            return Optional.empty(); // malformed row - skip, never crash the whole board
        }

        String rankText = stripHtml(rankMatcher.group(1)).replace("#", "").trim();
        String name = stripHtml(nameMatcher.group(1));
        String valueText = stripHtml(valueMatcher.group(1));

        int rank;
        try {
            rank = Integer.parseInt(rankText.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (rank < 1 || name.isBlank() || valueText.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(splitPrimaryAndSecondary(rank, name, valueText, primaryLabel));
    }

    /**
     * Some boards (e.g. Monsterjägare) publish a combined {@code "primary • secondary"} string in
     * the single value cell rather than two separate cells - split on the bullet separator whenever
     * present so {@link LeaderboardEntry#secondaryValue()} is populated, generically, for any board
     * that uses this convention rather than hardcoding a specific board id. The secondary value's
     * own unit (e.g. "Coins") is already embedded in its text (GameZone publishes it that way), so
     * no separate secondary label is invented.
     */
    private static LeaderboardEntry splitPrimaryAndSecondary(int rank, String name, String rawValue, String primaryLabel) {
        int bulletIdx = rawValue.indexOf(" • ");
        if (bulletIdx < 0) {
            return LeaderboardEntry.of(rank, name, rawValue, primaryLabel);
        }
        String primary = rawValue.substring(0, bulletIdx).trim();
        String secondary = rawValue.substring(bulletIdx + 3).trim();
        if (primary.isBlank()) {
            return LeaderboardEntry.of(rank, name, rawValue, primaryLabel);
        }
        if (secondary.isBlank()) {
            return LeaderboardEntry.of(rank, name, primary, primaryLabel);
        }
        return new LeaderboardEntry(rank, name, primary, primaryLabel, secondary, "");
    }

    /**
     * Parses the SERVERN group overview page ({@code /leaderboards?tab=server}) into one
     * single-row {@link LeaderboardEntry} per stat card found, keyed by the card's own {@code <h3>}
     * title text (matched against {@link LeaderboardDefinition#title()} by the caller - see
     * {@link GameZoneLeaderboardSource}). Each returned entry has {@code rank=1}, since a server
     * stat is a single aggregate value, not a ranked list.
     *
     * @throws LeaderboardIncompatibleException if no {@code boardCard} markup is found at all.
     */
    public static Map<String, RawServerStat> parseServerStats(String html) throws LeaderboardIncompatibleException {
        if (html == null || !html.contains("boardCard")) {
            throw new LeaderboardIncompatibleException("Expected 'boardCard' markup was not found in the fetched page.");
        }

        Map<String, RawServerStat> byTitle = new LinkedHashMap<>();
        Matcher cardMatcher = SERVER_CARD.matcher(html);
        while (cardMatcher.find()) {
            String cardHtml = cardMatcher.group(1);
            parseOneServerCard(cardHtml).ifPresent(stat -> byTitle.put(stat.title(), stat));
        }
        return Map.copyOf(byTitle);
    }

    /** One server stat card's raw extracted text, keyed by title by {@link #parseServerStats}. The
     * label is attached by the caller from the registry, exactly like {@link #parseFullTableTop10}. */
    public record RawServerStat(String title, String entityName, String value) {}

    private static Optional<RawServerStat> parseOneServerCard(String cardHtml) {
        Matcher titleMatcher = SERVER_TITLE.matcher(cardHtml);
        Matcher valueMatcher = SERVER_VALUE.matcher(cardHtml);
        if (!titleMatcher.find() || !valueMatcher.find()) {
            return Optional.empty();
        }

        String title = stripHtml(titleMatcher.group(1));
        String value = stripHtml(valueMatcher.group(1));

        Matcher nameMatcher = SERVER_IDENTITY_NAME.matcher(cardHtml);
        String name = nameMatcher.find() ? stripHtml(nameMatcher.group(1)) : "GameZone";

        if (title.isBlank() || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RawServerStat(title, name.isBlank() ? "GameZone" : name, value));
    }

    /**
     * Strips HTML tags and comments and unescapes the handful of entities GameZone's own text
     * realistically contains. Deliberately does NOT strip non-breaking spaces (U+00A0) - GameZone
     * groups large numbers with them (e.g. {@code "32 389 617"}), and
     * {@link String#trim()}/{@link String#isBlank()} both already treat U+00A0 as non-whitespace, so
     * it survives here exactly as published.
     */
    private static String stripHtml(String fragment) {
        if (fragment == null) return "";
        String noTags = TAG_OR_COMMENT.matcher(fragment).replaceAll("");
        return unescapeHtmlEntities(noTags).trim();
    }

    private static String unescapeHtmlEntities(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }
}
