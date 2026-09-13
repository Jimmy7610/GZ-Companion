package se.jimmyeliasson.gzcompanion.bounty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure, deterministic formatting tests for {@link BountyFormatter}. */
class BountyFormatterTest {

    @Test
    @DisplayName("Reward formatting groups thousands with a plain space")
    void rewardFormattingGroupsThousands() {
        assertEquals("7 500", BountyFormatter.formatReward(7500));
        assertEquals("50 000", BountyFormatter.formatReward(50_000));
        assertEquals("1 234 567", BountyFormatter.formatReward(1_234_567));
        assertEquals("0", BountyFormatter.formatReward(0));
        assertEquals("999", BountyFormatter.formatReward(999));
        assertEquals("1 000", BountyFormatter.formatReward(1000));
    }

    @Test
    @DisplayName("Reward formatting handles values beyond int range")
    void rewardFormattingHandlesLargeValues() {
        assertEquals("9 999 999 999", BountyFormatter.formatReward(9_999_999_999L));
    }

    @Test
    @DisplayName("Remaining time: days and hours")
    void remainingTimeDaysAndHours() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = now.plusSeconds(52 * 3600); // 2 days 4 hours
        assertEquals("2 d 4 h", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(expiresAt), now));
    }

    @Test
    @DisplayName("Remaining time: hours and minutes")
    void remainingTimeHoursAndMinutes() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = now.plusSeconds(5 * 3600 + 18 * 60);
        assertEquals("5 h 18 min", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(expiresAt), now));
    }

    @Test
    @DisplayName("Remaining time: minutes only")
    void remainingTimeMinutesOnly() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = now.plusSeconds(42 * 60);
        assertEquals("42 min", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(expiresAt), now));
    }

    @Test
    @DisplayName("Remaining time: under a minute")
    void remainingTimeUnderAMinute() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = now.plusSeconds(30);
        assertEquals("< 1 min", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(expiresAt), now));
    }

    @Test
    @DisplayName("Remaining time: source explicitly says no time limit (NoLimit)")
    void remainingTimeNoLimit() {
        assertEquals("Ingen tidsgräns", BountyFormatter.formatRemainingTime(BountyExpiry.NO_LIMIT, Instant.now()));
    }

    @Test
    @DisplayName("Remaining time: source did not say (Unknown) is presented distinctly from NoLimit - never guessed as unlimited")
    void remainingTimeUnknownIsDistinctFromNoLimit() {
        assertEquals("Tidsgräns okänd", BountyFormatter.formatRemainingTime(BountyExpiry.UNKNOWN, Instant.now()));
        assertEquals("Tidsgräns okänd", BountyFormatter.formatRemainingTime(null, Instant.now()));
    }

    @Test
    @DisplayName("Remaining time: crossed expiry is presented as 'may have expired', never as still definitely active")
    void remainingTimeExpiredPresentation() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        Instant expiresAt = now.minusSeconds(10);
        assertEquals("Kan ha löpt ut - uppdatera", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(expiresAt), now));
    }

    @Test
    @DisplayName("Remaining time: exactly at expiry is treated as expired, not '< 1 min' still remaining")
    void remainingTimeExactlyAtExpiry() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        assertEquals("Kan ha löpt ut - uppdatera", BountyFormatter.formatRemainingTime(new BountyExpiry.ExpiresAt(now), now));
    }

    @Test
    @DisplayName("Command-copy: a simple single-token name produces the documented command")
    void commandCopySimpleName() {
        assertEquals("/bounty info Gorgash", BountyFormatter.bountyInfoCommand("Gorgash"));
        assertTrue(BountyFormatter.isNameSafeForCommand("Gorgash"));
    }

    @Test
    @DisplayName("Command-copy: a name containing whitespace never produces a command")
    void commandCopyRefusesNameWithWhitespace() {
        assertFalse(BountyFormatter.isNameSafeForCommand("Big Bad Wolf"));
        assertNull(BountyFormatter.bountyInfoCommand("Big Bad Wolf"));
    }

    @Test
    @DisplayName("Command-copy: a name containing a quote or backslash never produces a command")
    void commandCopyRefusesNameWithQuoteOrBackslash() {
        assertFalse(BountyFormatter.isNameSafeForCommand("Weird\"Name"));
        assertFalse(BountyFormatter.isNameSafeForCommand("Weird\\Name"));
        assertNull(BountyFormatter.bountyInfoCommand("Weird\"Name"));
    }

    @Test
    @DisplayName("Command-copy: a blank or null name never produces a command")
    void commandCopyRefusesBlankOrNullName() {
        assertNull(BountyFormatter.bountyInfoCommand(null));
        assertNull(BountyFormatter.bountyInfoCommand(""));
        assertNull(BountyFormatter.bountyInfoCommand("   "));
    }

    @Test
    @DisplayName("Entity type formatting only replaces underscores - never a guessed translation")
    void entityTypeFormattingOnlyReplacesUnderscores() {
        assertEquals("WITHER SKELETON", BountyFormatter.formatEntityType("WITHER_SKELETON"));
        assertEquals("ZOMBIE", BountyFormatter.formatEntityType("ZOMBIE"));
        assertNull(BountyFormatter.formatEntityType(null));
    }

    @Test
    @DisplayName("Freshness label: LOADED is LIVE, STALE is CACHAD (never LIVE)")
    void freshnessLabelLoadedAndStale() {
        BountySnapshot loaded = new BountySnapshot(BountyStatus.LOADED, List.of(), Instant.now(), null);
        BountySnapshot stale = new BountySnapshot(BountyStatus.STALE, List.of(), Instant.now(), "reason");
        assertEquals("LIVE", BountyFormatter.freshnessLabel(loaded));
        assertEquals("CACHAD", BountyFormatter.freshnessLabel(stale));
    }

    @Test
    @DisplayName("Freshness label: IDLE has no label")
    void freshnessLabelIdleIsBlank() {
        assertEquals("", BountyFormatter.freshnessLabel(BountySnapshot.idle()));
    }

    @Test
    @DisplayName("Freshness detail: 'just nu' immediately after fetch, then seconds/minutes/hours grow correctly")
    void freshnessDetailGrowsOverTime() {
        Instant fetchedAt = Instant.parse("2026-09-13T12:00:00Z");
        BountySnapshot loaded = new BountySnapshot(BountyStatus.LOADED, List.of(), fetchedAt, null);

        assertEquals("Uppdaterad just nu", BountyFormatter.freshnessDetail(loaded, fetchedAt.plusSeconds(2)));
        assertEquals("Uppdaterad 34 sek sedan", BountyFormatter.freshnessDetail(loaded, fetchedAt.plusSeconds(34)));

        BountySnapshot stale = new BountySnapshot(BountyStatus.STALE, List.of(), fetchedAt, "reason");
        assertEquals("Cachad data - 5 min sedan", BountyFormatter.freshnessDetail(stale, fetchedAt.plusSeconds(300)));
    }

    @Test
    @DisplayName("Freshness detail: never-fetched states give an honest status message, not a fabricated timestamp")
    void freshnessDetailNeverFetchedStates() {
        assertEquals("Hämtar...", BountyFormatter.freshnessDetail(
                new BountySnapshot(BountyStatus.LOADING, List.of(), null, null), Instant.now()));
        assertEquals("Kunde inte hämta just nu.", BountyFormatter.freshnessDetail(
                new BountySnapshot(BountyStatus.UNAVAILABLE, List.of(), null, "offline"), Instant.now()));
    }
}
