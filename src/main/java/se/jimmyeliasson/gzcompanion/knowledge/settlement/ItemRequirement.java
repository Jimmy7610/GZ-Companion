package se.jimmyeliasson.gzcompanion.knowledge.settlement;

/**
 * One material requirement inside a {@link SettlementLevel}. {@code itemId} is deliberately
 * nullable: some GameZone requirements name a genuinely ambiguous Minecraft category (any Wool
 * color, any Music Disc, an Armor Trim Template, a generic Smithing Template) rather than one
 * concrete item, and inventing a specific id for those would misrepresent the source. When the
 * source requires several distinct variants of such a category (e.g. "2 olika" Armor Trim
 * Templates), that count is carried in {@code distinctVariantsRequired}; otherwise it is null.
 */
public record ItemRequirement(String itemId, String displayName, int count, Integer distinctVariantsRequired) {
    public ItemRequirement {
        displayName = (displayName != null && !displayName.isBlank()) ? displayName : (itemId != null ? itemId : "Okänt föremål");
        if (count < 0) count = 0;
    }

    public boolean hasConcreteItem() {
        return itemId != null && !itemId.isBlank();
    }

    /** A stable merge key for aggregating identical requirements across a level range. */
    public String mergeKey() {
        return hasConcreteItem() ? itemId : ("category:" + displayName);
    }
}
