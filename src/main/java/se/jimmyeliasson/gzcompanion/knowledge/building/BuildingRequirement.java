package se.jimmyeliasson.gzcompanion.knowledge.building;

/**
 * One "special requirement" (a concrete block/item/entity and quantity) a building needs, per
 * the GameZone Wiki. {@code itemId} may be null for the same reason
 * {@code se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement} allows it: a
 * genuinely ambiguous category rather than a concrete Minecraft item.
 */
public record BuildingRequirement(String itemId, String displayName, int count, String note) {
    public BuildingRequirement {
        displayName = (displayName != null && !displayName.isBlank()) ? displayName : (itemId != null ? itemId : "Okänt föremål");
        if (count < 0) count = 0;
    }

    public boolean hasConcreteItem() {
        return itemId != null && !itemId.isBlank();
    }
}
