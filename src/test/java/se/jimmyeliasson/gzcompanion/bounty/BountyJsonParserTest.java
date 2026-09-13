package se.jimmyeliasson.gzcompanion.bounty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BountyJsonParser} against the REAL discovered public contract from
 * {@code https://www.gamezonemc.se/api/bounties} (captured 2026-09-13 - see docs/BOUNTY-BOARD.md
 * for the discovery process). Fixtures below are shaped exactly like the real captured response,
 * never an invented schema.
 */
class BountyJsonParserTest {

    /** The exact real response captured live during Phase 0 discovery (one active bounty). */
    private static final String REAL_SAMPLE_ONE_ACTIVE =
            "{\"status\":\"success\",\"data\":{\"active\":[{\"name\":\"HostileBoss\",\"entityType\":\"WITHER_SKELETON\","
            + "\"reward\":7500,\"hint\":\"En armé av fientliga mobs har invaderat byn utanför västra bron!\","
            + "\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-29T20:42:37Z\",\"expiresAt\":null}],\"count\":1}}";

    @Test
    @DisplayName("Parses the real captured single-active-bounty sample exactly")
    void parsesRealCapturedSample() throws Exception {
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(REAL_SAMPLE_ONE_ACTIVE);

        assertEquals(1, entries.size());
        BountyEntry entry = entries.get(0);
        assertEquals("HostileBoss", entry.name());
        assertEquals("WITHER_SKELETON", entry.entityType());
        assertEquals(7500L, entry.rewardCoins());
        assertEquals("En armé av fientliga mobs har invaderat byn utanför västra bron!", entry.hint());
        assertEquals("ACTIVE", entry.status());
        assertEquals(Instant.parse("2026-08-29T20:42:37Z"), entry.createdAt());
        assertNull(entry.expiresAt());
        assertFalse(entry.hasExpiry());
        assertTrue(entry.hasHint());
        assertTrue(entry.hasEntityType());
    }

    @Test
    @DisplayName("An empty active array parses to an empty list - not an error")
    void emptyActiveListParsesToEmptyList() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":[],\"count\":0}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertTrue(entries.isEmpty());
    }

    @Test
    @DisplayName("Multiple active bounties all parse correctly, preserving order")
    void multipleActiveBountiesParseCorrectly() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Gorgash\",\"entityType\":\"WARDEN\",\"reward\":1000,\"hint\":null,\"status\":\"ACTIVE\",\"createdAt\":null,\"expiresAt\":null},"
                + "{\"name\":\"Bloodmaw\",\"entityType\":\"ZOMBIE\",\"reward\":2500,\"hint\":\"Nära gruvorna\",\"status\":\"ACTIVE\",\"createdAt\":null,\"expiresAt\":null}"
                + "],\"count\":2}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);

        assertEquals(2, entries.size());
        assertEquals("Gorgash", entries.get(0).name());
        assertEquals("Bloodmaw", entries.get(1).name());
    }

    @Test
    @DisplayName("Large Coin reward values (beyond int range) parse correctly as a long")
    void largeCoinValuesParseAsLong() throws Exception {
        long huge = 9_999_999_999L; // beyond Integer.MAX_VALUE
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Titan\",\"reward\":" + huge + ",\"status\":\"ACTIVE\"}"
                + "],\"count\":1}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals(huge, entries.get(0).rewardCoins());
    }

    @Test
    @DisplayName("Unicode names (Swedish/emoji-adjacent characters) round-trip exactly")
    void unicodeNamesRoundTripExactly() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Örnklo Åskblixt\",\"reward\":100,\"status\":\"ACTIVE\"}"
                + "],\"count\":1}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals("Örnklo Åskblixt", entries.get(0).name());
    }

    @Test
    @DisplayName("A bounty with no clue reports hasHint() false and a null hint")
    void bountyWithNoClueReportsAbsent() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Silent\",\"reward\":100,\"status\":\"ACTIVE\"}"
                + "],\"count\":1}}";
        BountyEntry entry = BountyJsonParser.parseActiveBounties(json).get(0);
        assertNull(entry.hint());
        assertFalse(entry.hasHint());
    }

    @Test
    @DisplayName("A bounty with an explicit null clue is treated identically to an absent clue")
    void explicitNullClueTreatedAsAbsent() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Silent\",\"reward\":100,\"hint\":null,\"status\":\"ACTIVE\"}"
                + "],\"count\":1}}";
        BountyEntry entry = BountyJsonParser.parseActiveBounties(json).get(0);
        assertFalse(entry.hasHint());
    }

    @Test
    @DisplayName("A bounty with a real expiry timestamp parses it and reports hasExpiry() true")
    void bountyWithExpiryParsesTimestamp() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Timed\",\"reward\":100,\"status\":\"ACTIVE\",\"expiresAt\":\"2026-09-14T12:00:00Z\"}"
                + "],\"count\":1}}";
        BountyEntry entry = BountyJsonParser.parseActiveBounties(json).get(0);
        assertTrue(entry.hasExpiry());
        assertEquals(Instant.parse("2026-09-14T12:00:00Z"), entry.expiresAt());
    }

    @Test
    @DisplayName("A bounty with expiresAt: null (no time limit) reports hasExpiry() false - never an invented expiry")
    void bountyWithNullExpiryHasNoExpiry() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Forever\",\"reward\":100,\"status\":\"ACTIVE\",\"expiresAt\":null}"
                + "],\"count\":1}}";
        BountyEntry entry = BountyJsonParser.parseActiveBounties(json).get(0);
        assertFalse(entry.hasExpiry());
    }

    @Test
    @DisplayName("A malformed individual entry (missing name) is skipped, not fatal to the rest")
    void malformedEntryMissingNameIsSkipped() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"reward\":100,\"status\":\"ACTIVE\"},"
                + "{\"name\":\"Valid\",\"reward\":200,\"status\":\"ACTIVE\"}"
                + "],\"count\":2}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals(1, entries.size());
        assertEquals("Valid", entries.get(0).name());
    }

    @Test
    @DisplayName("A malformed individual entry (missing reward) is skipped - no guessed fallback reward")
    void malformedEntryMissingRewardIsSkipped() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"NoReward\",\"status\":\"ACTIVE\"},"
                + "{\"name\":\"Valid\",\"reward\":200,\"status\":\"ACTIVE\"}"
                + "],\"count\":2}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals(1, entries.size());
        assertEquals("Valid", entries.get(0).name());
    }

    @Test
    @DisplayName("A non-object element inside the active array is skipped safely")
    void nonObjectElementInActiveArrayIsSkipped() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "\"not-an-object\","
                + "{\"name\":\"Valid\",\"reward\":200,\"status\":\"ACTIVE\"}"
                + "],\"count\":2}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals(1, entries.size());
    }

    @Test
    @DisplayName("A malformed TOP-LEVEL response (not JSON at all) is reported as INCOMPATIBLE, never crashes")
    void malformedTopLevelResponseThrowsIncompatible() {
        assertThrows(BountyIncompatibleException.class, () -> BountyJsonParser.parseActiveBounties("not json at all {{{"));
    }

    @Test
    @DisplayName("A response missing the \"data\" object entirely is INCOMPATIBLE")
    void missingDataObjectIsIncompatible() {
        String json = "{\"status\":\"success\"}";
        assertThrows(BountyIncompatibleException.class, () -> BountyJsonParser.parseActiveBounties(json));
    }

    @Test
    @DisplayName("A response missing \"data.active\" entirely is INCOMPATIBLE")
    void missingActiveArrayIsIncompatible() {
        String json = "{\"status\":\"success\",\"data\":{\"count\":0}}";
        assertThrows(BountyIncompatibleException.class, () -> BountyJsonParser.parseActiveBounties(json));
    }

    @Test
    @DisplayName("An unexpected top-level status value is INCOMPATIBLE, not silently treated as success")
    void unexpectedTopLevelStatusIsIncompatible() {
        String json = "{\"status\":\"pending\",\"data\":{\"active\":[],\"count\":0}}";
        assertThrows(BountyIncompatibleException.class, () -> BountyJsonParser.parseActiveBounties(json));
    }

    @Test
    @DisplayName("\"data.active\" being a non-array value is INCOMPATIBLE, not silently treated as empty")
    void activeAsNonArrayIsIncompatible() {
        String json = "{\"status\":\"success\",\"data\":{\"active\":\"unexpected\",\"count\":0}}";
        assertThrows(BountyIncompatibleException.class, () -> BountyJsonParser.parseActiveBounties(json));
    }

    @Test
    @DisplayName("An unparseable individual timestamp degrades that field to absent, not fatal to the entry")
    void unparseableTimestampDegradesGracefully() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"BadDate\",\"reward\":100,\"status\":\"ACTIVE\",\"createdAt\":\"not-a-date\",\"expiresAt\":\"also-bad\"}"
                + "],\"count\":1}}";
        BountyEntry entry = BountyJsonParser.parseActiveBounties(json).get(0);
        assertEquals("BadDate", entry.name());
        assertNull(entry.createdAt());
        assertNull(entry.expiresAt());
        assertFalse(entry.hasExpiry());
    }

    @Test
    @DisplayName("A negative reward value is treated as malformed and skipped - never clamped/guessed")
    void negativeRewardIsSkipped() throws Exception {
        String json = "{\"status\":\"success\",\"data\":{\"active\":["
                + "{\"name\":\"Negative\",\"reward\":-500,\"status\":\"ACTIVE\"},"
                + "{\"name\":\"Valid\",\"reward\":200,\"status\":\"ACTIVE\"}"
                + "],\"count\":2}}";
        List<BountyEntry> entries = BountyJsonParser.parseActiveBounties(json);
        assertEquals(1, entries.size());
        assertEquals("Valid", entries.get(0).name());
    }
}
