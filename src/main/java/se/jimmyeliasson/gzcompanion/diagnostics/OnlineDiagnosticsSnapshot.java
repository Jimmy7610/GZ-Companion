package se.jimmyeliasson.gzcompanion.diagnostics;

import se.jimmyeliasson.gzcompanion.gamezone.settlement.GameZoneSettlementIdentity;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;

import java.util.List;

/**
 * A TEMPORARY, LOCAL-ONLY snapshot of exactly what the Online tab's automatic settlement detection
 * currently sees and parsed, for human QA on the real GameZoneMC server - see the Online tab's
 * "Diagnostik" panel. Never written to persistent logs by default, never sent anywhere; it exists
 * only to let a human compare the RAW values against what synthetic tests assumed, since real TAB
 * text can contain formatting-adjacent Unicode invisible in a screenshot (see
 * {@link DiagnosticTextEscaper}). Pure data - no Minecraft dependency beyond the already-existing
 * {@link OnlinePlayerSnapshot} record, so this is fully unit-testable.
 */
public record OnlineDiagnosticsSnapshot(
        boolean connected,
        String localUsername,
        boolean localSnapshotFound,
        String headerText,
        String localDisplayText,
        String settlementName,
        String role,
        String settlementPrefix,
        int sameSettlementCount,
        int totalOnlineCount,
        int playersWithTabDisplayCount
) {
    public static OnlineDiagnosticsSnapshot capture(
            boolean connected,
            String headerText,
            List<OnlinePlayerSnapshot> onlinePlayers,
            GameZoneSettlementIdentity identity,
            int sameSettlementCount
    ) {
        List<OnlinePlayerSnapshot> players = onlinePlayers != null ? onlinePlayers : List.of();
        OnlinePlayerSnapshot local = null;
        int withDisplay = 0;
        for (OnlinePlayerSnapshot player : players) {
            if (player == null) continue;
            if (player.localPlayer() && local == null) local = player;
            if (player.tabDisplayText() != null && !player.tabDisplayText().isBlank()) withDisplay++;
        }

        GameZoneSettlementIdentity id = identity != null ? identity : GameZoneSettlementIdentity.UNKNOWN;
        return new OnlineDiagnosticsSnapshot(
                connected,
                local != null ? local.username() : null,
                local != null,
                headerText,
                local != null ? local.tabDisplayText() : null,
                id.settlementName(),
                id.role(),
                id.settlementPrefix(),
                sameSettlementCount,
                players.size(),
                withDisplay
        );
    }

    public int headerLength() {
        return headerText != null ? headerText.length() : 0;
    }

    public int localDisplayLength() {
        return localDisplayText != null ? localDisplayText.length() : 0;
    }

    public String headerEscaped() {
        return DiagnosticTextEscaper.escape(headerText);
    }

    public String localDisplayEscaped() {
        return DiagnosticTextEscaper.escape(localDisplayText);
    }

    private static String orNull(String value) {
        return value != null ? value : "null";
    }

    /** Plain-text, machine-diffable block for the "Kopiera diagnostik" clipboard button. */
    public String toCopyText() {
        StringBuilder sb = new StringBuilder();
        sb.append("GZ Companion Online diagnostics\n");
        sb.append("connected=").append(connected).append('\n');
        sb.append("localUsername=").append(orNull(localUsername)).append('\n');
        sb.append("localSnapshotFound=").append(localSnapshotFound).append('\n');
        sb.append("header=").append(orNull(headerText)).append('\n');
        sb.append("headerLength=").append(headerLength()).append('\n');
        sb.append("headerEscaped=").append(headerEscaped()).append('\n');
        sb.append("localDisplay=").append(orNull(localDisplayText)).append('\n');
        sb.append("localDisplayLength=").append(localDisplayLength()).append('\n');
        sb.append("localDisplayEscaped=").append(localDisplayEscaped()).append('\n');
        sb.append("parsedSettlement=").append(orNull(settlementName)).append('\n');
        sb.append("parsedRole=").append(orNull(role)).append('\n');
        sb.append("parsedPrefix=").append(orNull(settlementPrefix)).append('\n');
        sb.append("sameSettlementCount=").append(sameSettlementCount).append('\n');
        sb.append("playersWithTabDisplay=").append(playersWithTabDisplayCount).append('/').append(totalOnlineCount).append('\n');
        return sb.toString();
    }

    /** Human-readable block for the on-screen "Diagnostik" panel body. */
    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ansluten: ").append(connected ? "Ja" : "Nej").append('\n');
        sb.append("Lokal spelare: ").append(localSnapshotFound ? orNull(localUsername) : "(hittades inte)").append('\n');
        sb.append('\n');
        sb.append("TAB header:\n").append(headerText != null ? headerText : "(ingen)").append('\n');
        sb.append("Längd: ").append(headerLength()).append(" | Escaped: ").append(headerEscaped()).append('\n');
        sb.append('\n');
        sb.append("Lokal TAB-rad:\n").append(localDisplayText != null ? localDisplayText : "(ingen)").append('\n');
        sb.append("Längd: ").append(localDisplayLength()).append(" | Escaped: ").append(localDisplayEscaped()).append('\n');
        sb.append('\n');
        sb.append("Parser:\n");
        sb.append("Settlement: ").append(settlementName != null ? settlementName : "okänt").append('\n');
        sb.append("Roll: ").append(role != null ? role : "okänd").append('\n');
        sb.append("Prefix: ").append(settlementPrefix != null ? settlementPrefix : "okänt").append('\n');
        sb.append('\n');
        sb.append("Samma settlement: ").append(sameSettlementCount).append(" spelare\n");
        sb.append("TAB-data tillgänglig: ").append(playersWithTabDisplayCount).append('/').append(totalOnlineCount).append(" spelare");
        return sb.toString();
    }
}
