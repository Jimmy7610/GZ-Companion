package se.jimmyeliasson.gzcompanion.knowledge.items;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One GameZone-specific custom item fact (a relic or any future non-relic custom item). Every
 * field beyond the required identity fields is optional — an official source publishing only
 * some facts about an item must not force the rest to be invented to "complete" the entry (see
 * {@code docs/KNOWLEDGE-BASE.md}).
 */
public record CustomItemKnowledge(
    String id,
    String displayName,
    String baseMinecraftItemId,
    String description,
    List<String> lore,
    String category,
    String tier,
    String culture,
    String serial,
    List<String> enchants,
    String specialEffect,
    String acquisitionNotes,
    String craftingReferenceId,
    String releaseStatus,
    VerificationMetadata verification
) {
    public CustomItemKnowledge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        lore = lore != null ? List.copyOf(lore) : List.of();
        enchants = enchants != null ? List.copyOf(enchants) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }
}
