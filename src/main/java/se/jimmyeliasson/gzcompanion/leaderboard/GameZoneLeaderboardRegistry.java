package se.jimmyeliasson.gzcompanion.leaderboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ONE place GameZone-specific board identifiers, titles, descriptions, and value-column labels
 * live - never scattered across UI code (per this feature's explicit architecture requirement). All
 * 27 entries below were discovered by hand from the real public site on 2026-09-13; see
 * docs/LEADERBOARDS.md for the discovery method and exact source pages.
 *
 * <p>For the 22 ranked boards (SPELARE/SETTLEMENTS/FORETAG), {@link LeaderboardDefinition#id()} is
 * the EXACT slug GameZone's own site uses at {@code https://www.gamezonemc.se/leaderboards/<id>} -
 * kept identical on purpose. The 5 SERVERN boards have no such per-board page (they are single
 * aggregate values shown only on the group overview), so their ids are Companion's own invention -
 * chosen to match the internal key names GameZone's own page happens to use for the same concept,
 * but not sourced from any public URL.
 *
 * <p>GameZone may add, remove, or rename boards at any time - this list is a snapshot, not eternal
 * truth (see this class's own tests for the exact count assertions this pass validated against).
 * Adding a future board requires exactly one new entry here; nothing in the UI hardcodes a board id.
 */
public final class GameZoneLeaderboardRegistry {
    private static final List<LeaderboardDefinition> ALL = List.of(
            // --- SPELARE (10) ---
            new LeaderboardDefinition("player_coins", LeaderboardGroup.SPELARE,
                    "Rikaste spelare", "Högst aktuellt personligt coin-saldo.", "Coins", false),
            new LeaderboardDefinition("player_character_level", LeaderboardGroup.SPELARE,
                    "Högsta level", "Högst nuvarande level i Liv och Levelsystemet. Leveln återställs vid död.", "Level", false),
            new LeaderboardDefinition("player_production", LeaderboardGroup.SPELARE,
                    "Mest producerat", "Flest registrerade producerade items totalt.", "Items", false),
            new LeaderboardDefinition("player_play_time", LeaderboardGroup.SPELARE,
                    "Mest spelad tid", "Flest aktiva timmar registrerade på GameZone.", "Speltid", false),
            new LeaderboardDefinition("player_kills", LeaderboardGroup.SPELARE,
                    "Flest kills", "Flest registrerade spelarkills.", "Kills", false),
            new LeaderboardDefinition("player_duel_wins", LeaderboardGroup.SPELARE,
                    "Flest vunna dueller", "Flest registrerade vinster i officiella dueller.", "Duellvinster", false),
            new LeaderboardDefinition("player_referrals", LeaderboardGroup.SPELARE,
                    "Flest värvningar", "Flest kvalificerade spelare värvade till GameZone.", "Värvningar", false),
            new LeaderboardDefinition("player_bounty_hunter", LeaderboardGroup.SPELARE,
                    "Monsterjägare", "Flest inkasserade bounties. Totalt intjänad bounty-belöning visas som extra statistik.", "Bounties", false),
            new LeaderboardDefinition("player_deaths", LeaderboardGroup.SPELARE,
                    "Flest deaths", "Flest registrerade dödsfall.", "Deaths", false),
            new LeaderboardDefinition("player_kd", LeaderboardGroup.SPELARE,
                    "Högst K/D", "Bäst förhållande mellan kills och deaths.", "K/D", false),

            // --- SETTLEMENTS (7) ---
            new LeaderboardDefinition("settlement_treasury", LeaderboardGroup.SETTLEMENTS,
                    "Rikaste settlement", "Högst aktuellt saldo i stadskassan.", "Coins", false),
            new LeaderboardDefinition("settlement_members", LeaderboardGroup.SETTLEMENTS,
                    "Flest invånare", "Störst registrerad befolkning.", "Invånare", false),
            new LeaderboardDefinition("settlement_level", LeaderboardGroup.SETTLEMENTS,
                    "Högst nivå", "Settlements som nått längst i utvecklingen.", "Nivå", false),
            new LeaderboardDefinition("settlement_tax_collected", LeaderboardGroup.SETTLEMENTS,
                    "Mest skatt insamlad", "Störst totalt skatteinflöde till stadskassan.", "Coins", false),
            new LeaderboardDefinition("settlement_war_wins", LeaderboardGroup.SETTLEMENTS,
                    "Flest krigsvinster", "Flest krig där settlementet deltagit på vinnarsidan.", "Vinster", false),
            new LeaderboardDefinition("settlement_war_losses", LeaderboardGroup.SETTLEMENTS,
                    "Flest krigsförluster", "Flest krig där settlementet deltagit på förlorarsidan.", "Förluster", false),
            new LeaderboardDefinition("settlement_war_ticket_differential", LeaderboardGroup.SETTLEMENTS,
                    "Bäst ticket-differens", "Skillnaden mellan den egna sidans och motståndarsidans återstående tickets över avslutade krig. Högre är bättre.", "Tickets", false),

            // --- FÖRETAG (5) ---
            new LeaderboardDefinition("company_wealth", LeaderboardGroup.FORETAG,
                    "Rikaste företag", "Högst aktuellt företagskapital.", "Coins", false),
            new LeaderboardDefinition("company_sales", LeaderboardGroup.FORETAG,
                    "Mest försäljning", "Högst total försäljning genom handelssystemet.", "Coins", false),
            new LeaderboardDefinition("company_transactions", LeaderboardGroup.FORETAG,
                    "Flest transaktioner", "Flest slutförda köp och försäljningar.", "Transaktioner", false),
            new LeaderboardDefinition("company_license", LeaderboardGroup.FORETAG,
                    "Högsta licensnivå", "Företagen med mest utvecklad licens.", "Licens", false),
            new LeaderboardDefinition("company_members", LeaderboardGroup.FORETAG,
                    "Största företag", "Flest registrerade företagsmedlemmar.", "Medlemmar", false),

            // --- SERVERN (5) - single aggregate values, no per-board page (see class doc comment) ---
            new LeaderboardDefinition("server_coin_economy", LeaderboardGroup.SERVERN,
                    "Coin-ekonomi", "Totalt antal coins i den aktiva ekonomin.", "Coins", true),
            new LeaderboardDefinition("server_active_settlements", LeaderboardGroup.SERVERN,
                    "Aktiva settlements", "Antal registrerade settlements.", "Settlements", true),
            new LeaderboardDefinition("server_active_companies", LeaderboardGroup.SERVERN,
                    "Aktiva företag", "Antal registrerade företag.", "Företag", true),
            new LeaderboardDefinition("server_active_today", LeaderboardGroup.SERVERN,
                    "Aktiva idag", "Unika spelare som varit online idag.", "Spelare", true),
            new LeaderboardDefinition("server_active_week", LeaderboardGroup.SERVERN,
                    "Aktiva denna vecka", "Unika spelare som varit online under veckan.", "Spelare", true)
    );

    private static final Map<String, LeaderboardDefinition> BY_ID;
    private static final Map<LeaderboardGroup, List<LeaderboardDefinition>> BY_GROUP;

    static {
        Map<String, LeaderboardDefinition> byId = new LinkedHashMap<>();
        Map<LeaderboardGroup, List<LeaderboardDefinition>> byGroup = new LinkedHashMap<>();
        for (LeaderboardGroup group : LeaderboardGroup.values()) {
            byGroup.put(group, new java.util.ArrayList<>());
        }
        for (LeaderboardDefinition def : ALL) {
            byId.put(def.id(), def);
            byGroup.get(def.group()).add(def);
        }
        byGroup.replaceAll((group, list) -> List.copyOf(list));
        BY_ID = Map.copyOf(byId);
        BY_GROUP = Map.copyOf(byGroup);
    }

    private GameZoneLeaderboardRegistry() {}

    public static List<LeaderboardDefinition> all() {
        return ALL;
    }

    public static List<LeaderboardDefinition> byGroup(LeaderboardGroup group) {
        return BY_GROUP.getOrDefault(group, List.of());
    }

    public static Optional<LeaderboardDefinition> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** The default board shown when a group is first selected - simply the first one defined for it. */
    public static Optional<LeaderboardDefinition> firstInGroup(LeaderboardGroup group) {
        List<LeaderboardDefinition> defs = byGroup(group);
        return defs.isEmpty() ? Optional.empty() : Optional.of(defs.get(0));
    }
}
