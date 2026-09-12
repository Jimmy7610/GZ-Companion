package se.jimmyeliasson.gzcompanion.online;

import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure grouping/sorting/search logic for the Online tab - no live Minecraft dependency, so this is
 * fully unit-testable without a running client. Callers supply already-resolved plain data: the
 * current online snapshot list (empty when not connected to GameZone, or genuinely nobody known),
 * the list of locally-favorited player names (original casing, as stored), and the list of
 * locally-known settlement member names (original casing) - this class never talks to a knowledge
 * base, settings store, or Minecraft itself, and never invents a player's online status beyond
 * what the supplied snapshot list actually contains.
 *
 * <p>Order: FAVORITER (online favorites first, then not-online/unknown favorites, alphabetical
 * within each group) &gt; MIN SETTLEMENT (online locally-known members, excluding favorites,
 * alphabetical) &gt; ÖVRIGA ONLINE (everyone else currently online, alphabetical). No player ever
 * appears in more than one section - a favorite who is also a settlement member is shown ONLY in
 * FAVORITER (with {@link OnlinePlayerRow#settlementMember()} still true, so the UI can badge it).
 */
public final class OnlinePlayersGrouping {
    private OnlinePlayersGrouping() {}

    public static OnlinePlayersView build(
            List<OnlinePlayerSnapshot> onlinePlayers,
            boolean connectedToGameZone,
            List<String> favoriteNames,
            List<String> settlementMemberNames,
            String searchQuery
    ) {
        List<OnlinePlayerSnapshot> online = onlinePlayers != null ? onlinePlayers : List.of();
        List<String> favorites = favoriteNames != null ? favoriteNames : List.of();
        List<String> settlementMembers = settlementMemberNames != null ? settlementMemberNames : List.of();

        Map<String, OnlinePlayerSnapshot> onlineByLowerName = new HashMap<>();
        for (OnlinePlayerSnapshot snap : online) {
            if (snap != null && snap.username() != null && !snap.username().isBlank()) {
                onlineByLowerName.putIfAbsent(snap.username().toLowerCase(Locale.ROOT), snap);
            }
        }

        Set<String> favoriteLower = new HashSet<>();
        for (String f : favorites) {
            if (f != null && !f.isBlank()) favoriteLower.add(f.toLowerCase(Locale.ROOT));
        }
        Set<String> settlementLower = new HashSet<>();
        for (String m : settlementMembers) {
            if (m != null && !m.isBlank()) settlementLower.add(m.toLowerCase(Locale.ROOT));
        }

        // Dedupe favorites case-insensitively, keeping the first-seen original casing for display
        // when the player isn't currently online (an online match always wins with fresher casing).
        Map<String, String> favoriteDisplayByLower = new LinkedHashMap<>();
        for (String f : favorites) {
            if (f == null || f.isBlank()) continue;
            favoriteDisplayByLower.putIfAbsent(f.toLowerCase(Locale.ROOT), f);
        }

        List<OnlinePlayerRow> favoriteRows = new ArrayList<>();
        for (Map.Entry<String, String> entry : favoriteDisplayByLower.entrySet()) {
            String lower = entry.getKey();
            OnlinePlayerSnapshot snap = onlineByLowerName.get(lower);

            OnlinePresence presence;
            String displayName;
            Integer latency;
            boolean isLocal;
            if (snap != null) {
                presence = OnlinePresence.ONLINE;
                displayName = snap.username();
                latency = snap.latencyMs();
                isLocal = snap.localPlayer();
            } else {
                presence = connectedToGameZone ? OnlinePresence.NOT_ONLINE : OnlinePresence.UNKNOWN;
                displayName = entry.getValue();
                latency = null;
                isLocal = false;
            }
            favoriteRows.add(new OnlinePlayerRow(displayName, isLocal, true, settlementLower.contains(lower), presence, latency, OnlineSection.FAVORITES));
        }
        favoriteRows.sort(Comparator
                .comparingInt((OnlinePlayerRow r) -> r.presence() == OnlinePresence.ONLINE ? 0 : 1)
                .thenComparing(r -> r.displayName().toLowerCase(Locale.ROOT)));

        List<OnlinePlayerRow> settlementRows = new ArrayList<>();
        List<OnlinePlayerRow> otherRows = new ArrayList<>();
        for (OnlinePlayerSnapshot snap : online) {
            if (snap == null || snap.username() == null || snap.username().isBlank()) continue;
            String lower = snap.username().toLowerCase(Locale.ROOT);
            if (favoriteLower.contains(lower)) continue; // already shown in FAVORITER

            boolean isMember = settlementLower.contains(lower);
            OnlinePlayerRow row = new OnlinePlayerRow(snap.username(), snap.localPlayer(), false, isMember,
                    OnlinePresence.ONLINE, snap.latencyMs(), isMember ? OnlineSection.SETTLEMENT : OnlineSection.OTHERS);
            (isMember ? settlementRows : otherRows).add(row);
        }
        settlementRows.sort(Comparator.comparing(r -> r.displayName().toLowerCase(Locale.ROOT)));
        otherRows.sort(Comparator.comparing(r -> r.displayName().toLowerCase(Locale.ROOT)));

        int onlineCount = online.size();

        if (searchQuery != null && !searchQuery.isBlank()) {
            String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
            favoriteRows = filter(favoriteRows, needle);
            settlementRows = filter(settlementRows, needle);
            otherRows = filter(otherRows, needle);
        }

        return new OnlinePlayersView(favoriteRows, settlementRows, otherRows, onlineCount);
    }

    private static List<OnlinePlayerRow> filter(List<OnlinePlayerRow> rows, String lowercaseNeedle) {
        List<OnlinePlayerRow> result = new ArrayList<>();
        for (OnlinePlayerRow row : rows) {
            if (row.displayName().toLowerCase(Locale.ROOT).contains(lowercaseNeedle)) {
                result.add(row);
            }
        }
        return result;
    }
}
