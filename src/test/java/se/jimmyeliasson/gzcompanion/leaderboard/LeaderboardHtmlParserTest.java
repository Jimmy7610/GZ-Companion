package se.jimmyeliasson.gzcompanion.leaderboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure HTML-scanning tests for {@link LeaderboardHtmlParser} - every fixture below is a FICTIONAL,
 * deterministic HTML string modeled on the real markup shape discovered on 2026-09-13 (see
 * docs/LEADERBOARDS.md), using a DIFFERENT CSS-module hash than the real site's own to prove the
 * substring-based class matching is genuinely hash-independent. No real player names, settlement
 * names, or coin values from the live site are used anywhere in this file or in production code.
 */
class LeaderboardHtmlParserTest {

    /** One fictional {@code tableRow} fragment, matching the real per-board full-table page shape. */
    private static String row(int rank, String name, String value) {
        return """
                <div class="page-module__zZ9fake__tableRow"><span class="page-module__zZ9fake__tableRank page-module__zZ9fake__rank%d">#<!-- -->%d</span><span class="page-module__zZ9fake__tableIdentity"><img src="/minecraft/items/bell.png" alt=""/><a class="page-module__zZ9fake__entityLink" href="/x">%s</a></span><strong class="page-module__zZ9fake__tableValue">%s</strong></div>
                """.formatted(rank, rank, name, value);
    }

    private static String fullTablePage(String... rows) {
        StringBuilder sb = new StringBuilder("<div class=\"page-module__zZ9fake__fullTable\">");
        sb.append("<div class=\"page-module__zZ9fake__tableHead\"><span>Placering</span><span>Namn</span><span>Coins</span></div>");
        for (String r : rows) sb.append(r);
        sb.append("</div>");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Full-table (ranked board) parsing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("15 valid rows -> capped at exactly the top 10, in order")
    void parsesTenOrMoreRowsCappedAtTop10() throws Exception {
        String[] rows = new String[15];
        for (int i = 0; i < 15; i++) rows[i] = row(i + 1, "Spelare" + (i + 1), (1000 - i) + " coins");
        String html = fullTablePage(rows);

        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");

        assertEquals(10, entries.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, entries.get(i).rank());
            assertEquals("Spelare" + (i + 1), entries.get(i).displayName());
        }
    }

    @Test
    @DisplayName("Exactly 10 rows returns all 10")
    void exactlyTenRowsReturnsAllTen() throws Exception {
        String[] rows = new String[10];
        for (int i = 0; i < 10; i++) rows[i] = row(i + 1, "Namn" + i, "1 coins");
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(fullTablePage(rows), "Coins");
        assertEquals(10, entries.size());
    }

    @Test
    @DisplayName("Fewer than 10 legitimate records -> only the actual records, never fabricated")
    void fewerThanTenRowsReturnsOnlyActual() throws Exception {
        String html = fullTablePage(row(1, "Alfa", "3 coins"), row(2, "Beta", "2 coins"), row(3, "Gamma", "1 coins"));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");
        assertEquals(3, entries.size());
        assertEquals(3, entries.get(2).rank());
    }

    @Test
    @DisplayName("Rank order is preserved exactly as published, never re-sorted")
    void rankOrderPreserved() throws Exception {
        String html = fullTablePage(row(1, "Alfa", "9 coins"), row(2, "Beta", "8 coins"), row(3, "Gamma", "7 coins"));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");
        assertEquals(List.of(1, 2, 3), entries.stream().map(LeaderboardEntry::rank).toList());
    }

    @Test
    @DisplayName("Long names are preserved in full - truncation is a UI concern, not the parser's")
    void longNamesPreserved() throws Exception {
        String longName = "EttMycketMycketMycketMycketLångtAnvändarnamn123456789";
        String html = fullTablePage(row(1, longName, "1 coins"));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");
        assertEquals(longName, entries.get(0).displayName());
    }

    @Test
    @DisplayName("Unicode Swedish names (å/ä/ö) are preserved exactly")
    void unicodeSwedishNamesPreserved() throws Exception {
        String html = fullTablePage(row(1, "Åsa_Örnklärt", "1 coins"), row(2, "Björn-Ängla", "1 coins"));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");
        assertEquals("Åsa_Örnklärt", entries.get(0).displayName());
        assertEquals("Björn-Ängla", entries.get(1).displayName());
    }

    @Test
    @DisplayName("NBSP-grouped numbers (U+00A0) survive exactly, never mistaken for blank/whitespace")
    void nbspNumericGroupingPreserved() throws Exception {
        String nbspValue = "32 389 617 coins";
        String html = fullTablePage(row(1, "Alfa", nbspValue));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");
        assertEquals(nbspValue, entries.get(0).primaryValue());
    }

    @Test
    @DisplayName("A malformed row (missing the value cell) is skipped, not fatal to the whole board")
    void malformedRowSkippedNotFatal() throws Exception {
        String goodRow1 = row(1, "Alfa", "5 coins");
        String malformedRow = "<div class=\"page-module__zZ9fake__tableRow\"><span class=\"page-module__zZ9fake__tableRank\">#<!-- -->2</span><span class=\"page-module__zZ9fake__tableIdentity\"><a class=\"page-module__zZ9fake__entityLink\">Beta</a></span></div>"; // no tableValue at all
        String goodRow3 = row(3, "Gamma", "1 coins");
        String html = fullTablePage(goodRow1, malformedRow, goodRow3);

        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Coins");

        assertEquals(2, entries.size(), "the malformed middle row must be skipped, not crash the board");
        assertEquals("Alfa", entries.get(0).displayName());
        assertEquals("Gamma", entries.get(1).displayName());
    }

    @Test
    @DisplayName("A duplicate rank in the source is handled safely - no exception, data passed through as published")
    void duplicateRankHandledSafely() throws Exception {
        String html = fullTablePage(row(1, "Alfa", "5 coins"), row(1, "Beta", "5 coins"));
        List<LeaderboardEntry> entries = assertDoesNotThrow(() -> LeaderboardHtmlParser.parseFullTableTop10(html, "Coins"));
        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).rank());
        assertEquals(1, entries.get(1).rank());
    }

    @Test
    @DisplayName("A combined 'primary • secondary' value cell is split into primary and secondary values")
    void secondaryValueSplitOnBullet() throws Exception {
        String html = fullTablePage(row(1, "Delfiinn", "15 bounties • 5200664 Coins intjänat"));
        List<LeaderboardEntry> entries = LeaderboardHtmlParser.parseFullTableTop10(html, "Bounties");
        LeaderboardEntry entry = entries.get(0);
        assertEquals("15 bounties", entry.primaryValue());
        assertTrue(entry.hasSecondaryValue());
        assertEquals("5200664 Coins intjänat", entry.secondaryValue());
    }

    @Test
    @DisplayName("A board with no bullet separator has no secondary value")
    void noSecondaryValueWhenNoBulletPresent() throws Exception {
        String html = fullTablePage(row(1, "Alfa", "42"));
        LeaderboardEntry entry = LeaderboardHtmlParser.parseFullTableTop10(html, "Level").get(0);
        assertFalse(entry.hasSecondaryValue());
        assertNull(entry.secondaryValue());
    }

    @Test
    @DisplayName("A completely unrecognized page structure (no tableRow marker at all) throws Incompatible, not a crash")
    void unsupportedShapeThrowsIncompatible() {
        String html = "<html><body><p>GameZone redesigned this page entirely.</p></body></html>";
        assertThrows(LeaderboardIncompatibleException.class, () -> LeaderboardHtmlParser.parseFullTableTop10(html, "Coins"));
    }

    @Test
    @DisplayName("Null HTML throws Incompatible rather than NullPointerException")
    void nullHtmlThrowsIncompatible() {
        assertThrows(LeaderboardIncompatibleException.class, () -> LeaderboardHtmlParser.parseFullTableTop10(null, "Coins"));
    }

    // ------------------------------------------------------------------
    // Server-stat single-value cards
    // ------------------------------------------------------------------

    private static String serverCard(String title, String entityName, String value) {
        return """
                <article class="page-module__zZ9fake__boardCard page-module__zZ9fake__serverBoardCard"><header class="page-module__zZ9fake__boardHeader"><div class="page-module__zZ9fake__boardIcon">*</div><div class="page-module__zZ9fake__boardHeading"><div class="page-module__zZ9fake__boardTitleLine"><h3>%s</h3><span class="page-module__zZ9fake__liveBadge">LIVE</span></div><p>En fiktiv beskrivning.</p></div></header><ol class="page-module__zZ9fake__rankingList"><li class="page-module__zZ9fake__rankingRow "><div class="page-module__zZ9fake__identity"><span class="page-module__zZ9fake__entityIcon">*</span><div><strong>%s</strong><small>Enhet</small></div></div><div class="page-module__zZ9fake__value"><strong>%s</strong><small>Enhet</small></div></li></ol></article>
                """.formatted(title, entityName, value);
    }

    @Test
    @DisplayName("Server-stat overview page parses every card, keyed by its own title")
    void parsesAllServerCards() throws Exception {
        String html = "<section>" + serverCard("Fiktiv Coin-ekonomi", "GameZone", "1 234 567 coins")
                + serverCard("Fiktiva aktiva settlements", "GameZone", "42") + "</section>";

        Map<String, LeaderboardHtmlParser.RawServerStat> byTitle = LeaderboardHtmlParser.parseServerStats(html);

        assertEquals(2, byTitle.size());
        assertEquals("1 234 567 coins", byTitle.get("Fiktiv Coin-ekonomi").value());
        assertEquals("GameZone", byTitle.get("Fiktiv Coin-ekonomi").entityName());
        assertEquals("42", byTitle.get("Fiktiva aktiva settlements").value());
    }

    @Test
    @DisplayName("Server-stat page with no boardCard marker at all throws Incompatible")
    void serverStatsUnsupportedShapeThrowsIncompatible() {
        String html = "<html><body>GameZone removed the whole stats section.</body></html>";
        assertThrows(LeaderboardIncompatibleException.class, () -> LeaderboardHtmlParser.parseServerStats(html));
    }
}
