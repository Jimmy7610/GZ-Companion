package se.jimmyeliasson.gzcompanion.bounty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure JSON-in, entries-out mapping for GameZone's public {@code /api/bounties} contract - see
 * docs/BOUNTY-BOARD.md for the discovery process and a captured sample response. No I/O, no
 * Minecraft types, fully unit-testable against fixture strings.
 *
 * <p><b>The real discovered contract</b> (2026-09-13):
 * <pre>{@code
 * {
 *   "status": "success",
 *   "data": {
 *     "active": [
 *       {
 *         "name": "HostileBoss",
 *         "entityType": "WITHER_SKELETON",
 *         "reward": 7500,
 *         "hint": "En armé av fientliga mobs har invaderat byn utanför västra bron!",
 *         "status": "ACTIVE",
 *         "createdAt": "2026-08-29T20:42:37Z",
 *         "expiresAt": null
 *       }
 *     ],
 *     "count": 1
 *   }
 * }
 * }</pre>
 * This is a genuine, intentional, CORS-open ({@code Access-Control-Allow-Origin: *}), publicly
 * cacheable ({@code Cache-Control: public, s-maxage=10800}) JSON API - the exact same endpoint
 * GameZone's own public wiki page fetches to render its "LIVE FRÅN SERVERN" bounty widget - not an
 * internal Next.js Flight/RSC payload (deliberately rejected for Leaderboards for the same reason
 * this project rejects it here: not a stable, intentional, documented public contract).
 *
 * <p><b>Top-level validation vs. per-entry tolerance.</b> A response that isn't even a JSON object,
 * is missing {@code data}/{@code data.active} entirely, or declares an unrecognized top-level
 * {@code status} throws {@link BountyIncompatibleException} - GameZone most likely changed the
 * contract shape and this parser needs an update. Within an otherwise-valid {@code active} array, a
 * single entry missing its required {@code name} or {@code reward} is silently skipped (never a
 * guessed fallback name/reward) rather than failing the whole registry - see
 * {@link #parseActiveBounties(String)}.
 *
 * <p><b>A genuinely empty registry is not the same thing as a registry every entry of which failed
 * to parse.</b> If {@code data.active} is a NONEMPTY array but every single element fails per-entry
 * validation (e.g. GameZone renamed {@code reward} to something else), that is a contract change,
 * not "zero active bounties" - {@link #parseActiveBounties(String)} throws {@link
 * BountyIncompatibleException} in that case rather than silently returning an empty list, so the UI
 * never claims "no active hunts" when the truth is "this parser can no longer read any of them."
 *
 * <p><b>{@code data.count}</b> is present in the real contract but deliberately NOT read or
 * cross-checked here - GameZone's public contract does not document it as authoritative, so a
 * mismatch between {@code count} and the actual number of parsed/skipped entries must never by
 * itself cause a rejection.
 */
public final class BountyJsonParser {
    private BountyJsonParser() {}

    public static List<BountyEntry> parseActiveBounties(String json) throws BountyIncompatibleException {
        if (json == null || json.isBlank()) {
            throw new BountyIncompatibleException("Empty response body");
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new BountyIncompatibleException("Response root is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (BountyIncompatibleException e) {
            throw e;
        } catch (Exception e) {
            throw new BountyIncompatibleException("Response is not valid JSON: " + e.getMessage());
        }

        String topLevelStatus = optString(root, "status");
        if (topLevelStatus == null || !"success".equals(topLevelStatus)) {
            throw new BountyIncompatibleException("Unexpected top-level status: " + topLevelStatus);
        }

        if (!root.has("data") || !root.get("data").isJsonObject()) {
            throw new BountyIncompatibleException("Missing \"data\" object");
        }
        JsonObject data = root.getAsJsonObject("data");

        if (!data.has("active") || !data.get("active").isJsonArray()) {
            throw new BountyIncompatibleException("Missing \"data.active\" array");
        }
        JsonArray active = data.getAsJsonArray("active");

        List<BountyEntry> entries = new ArrayList<>();
        for (JsonElement el : active) {
            BountyEntry entry = parseOneEntry(el);
            if (entry != null) {
                entries.add(entry);
            }
            // A malformed/incomplete individual entry is silently skipped - never crashes the
            // whole registry fetch, never a guessed fallback name/reward/entityType.
        }

        // A NONEMPTY active array where NOTHING parsed means the per-entry contract itself broke
        // (e.g. a renamed required field) - that is a schema change, never a genuine "zero active
        // bounties" success. Only an active array that was ALREADY empty may legitimately produce
        // an empty result.
        if (!active.isEmpty() && entries.isEmpty()) {
            throw new BountyIncompatibleException(
                    "data.active contained " + active.size() + " entrie(s) but none matched the expected per-entry contract");
        }
        return entries;
    }

    private static BountyEntry parseOneEntry(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();

        String name = optString(obj, "name");
        if (name == null || name.isBlank()) return null; // no fallback guessed name

        Long reward = optLong(obj, "reward");
        if (reward == null || reward < 0) return null; // no guessed fallback reward

        String entityType = optString(obj, "entityType");
        String hint = optString(obj, "hint");
        String status = optString(obj, "status");
        Instant createdAt = optInstant(obj, "createdAt");
        BountyExpiry expiry = parseExpiry(obj, "expiresAt");

        try {
            return new BountyEntry(name, entityType, reward, hint, status, createdAt, expiry);
        } catch (IllegalArgumentException e) {
            return null; // defense in depth - the record's own compact constructor rejected it
        }
    }

    /**
     * Maps GameZone's {@code expiresAt} field to one of {@link BountyExpiry}'s three explicit
     * states - see that type's own doc comment for why these must never be collapsed together:
     *
     * <ul>
     *     <li>field ABSENT entirely &rarr; {@link BountyExpiry#UNKNOWN} (the source told us
     *         nothing - never guessed as "no limit")</li>
     *     <li>explicit JSON {@code null} &rarr; {@link BountyExpiry#NO_LIMIT} (the source itself
     *         says there is no time limit)</li>
     *     <li>a valid ISO-8601 string &rarr; {@code BountyExpiry.ExpiresAt}</li>
     *     <li>any other value - non-string, blank, or an unparseable non-null string &rarr;
     *         {@link BountyExpiry#UNKNOWN} (malformed, never silently treated as "no limit")</li>
     * </ul>
     */
    private static BountyExpiry parseExpiry(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return BountyExpiry.UNKNOWN;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return BountyExpiry.NO_LIMIT;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return BountyExpiry.UNKNOWN;
        }
        String raw = el.getAsString();
        if (raw.isBlank()) {
            return BountyExpiry.UNKNOWN;
        }
        try {
            return new BountyExpiry.ExpiresAt(Instant.parse(raw));
        } catch (DateTimeParseException e) {
            return BountyExpiry.UNKNOWN; // malformed non-null value - never becomes "no limit"
        }
    }

    private static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }

    private static Long optLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        try {
            return el.getAsLong();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Absent, null, or unparseable is treated identically - a missing/bad timestamp degrades that
     * one field to "not provided" rather than discarding the whole otherwise-valid entry. */
    private static Instant optInstant(JsonObject obj, String key) {
        String raw = optString(obj, key);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
