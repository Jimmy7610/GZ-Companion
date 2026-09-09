package se.jimmyeliasson.gzcompanion.knowledge.commands;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;
import java.util.Objects;

/**
 * One GameZone command fact. Contains no Minecraft API types — pure Rule Pack knowledge.
 */
public record CommandDefinition(
    String id,
    String primaryCommand,
    List<String> aliases,
    String syntax,
    String description,
    String categoryId,
    List<String> keywords,
    List<String> examples,
    String requirements,
    VerificationMetadata verification
) {
    public CommandDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(primaryCommand, "primaryCommand");
        aliases = aliases != null ? List.copyOf(aliases) : List.of();
        keywords = keywords != null ? List.copyOf(keywords) : List.of();
        examples = examples != null ? List.copyOf(examples) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }
}
