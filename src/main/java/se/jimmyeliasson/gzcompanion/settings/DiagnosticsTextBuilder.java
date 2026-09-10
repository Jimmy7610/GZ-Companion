package se.jimmyeliasson.gzcompanion.settings;

import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;

/**
 * Builds the exact safe, redacted diagnostics text shown by the Settings tab and copied via
 * "Kopiera diagnostik". Every field here is a status/count only - this MUST NEVER include chat
 * text, chest coordinates, chest contents, MarketWatch note text, or any other player-authored
 * or potentially sensitive content. See docs/SETTINGS.md for the exact contract.
 */
public final class DiagnosticsTextBuilder {
    private DiagnosticsTextBuilder() {}

    public static String build(CompanionSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("GZ Companion-diagnostik\n");
        sb.append("Companion-version: ").append(CompanionConstants.getModVersion()).append('\n');
        sb.append("Minecraft-version: ").append(CompanionConstants.TARGET_MINECRAFT_VERSION).append('\n');
        sb.append("Rule Pack-version: ").append(session.getActiveRulePack() != null
                ? session.getActiveRulePack().manifest().packVersion() : "okänd").append('\n');
        sb.append("Aktiv profil: ").append(session.getCurrentServerProfile()).append('\n');
        sb.append("Guide-status: ").append(session.getGuideEngine().getLoadStatus()).append('\n');
        sb.append("Kistor-status: ").append(session.getChestManager().getStatus()).append('\n');
        sb.append("Kommando-status: ").append(session.getCommandCatalogStatus()).append('\n');
        sb.append("Crafting-status: ").append(session.getCraftingKnowledgeStatus()).append(" / ").append(session.getItemKnowledgeStatus()).append('\n');
        sb.append("Settlement-status: ").append(session.getSettlementCatalogStatus()).append('\n');
        sb.append("Byggplaner-status: ").append(session.getBuildingKnowledgeStatus()).append('\n');
        sb.append("MarketWatch-status: ").append(session.getMarketWatchInfoStatus()).append('\n');
        sb.append("Händelsemotor-status: ").append(session.getParserCatalogStatus())
                .append(" (").append(session.getParserCatalog().activeCount()).append(" verifierade parsrar)\n");
        return sb.toString();
    }
}
