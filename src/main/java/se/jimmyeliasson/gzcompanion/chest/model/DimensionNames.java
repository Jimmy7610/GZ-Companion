package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Short human-readable dimension names for Kistor UI/search. Pure string mapping - never queries
 * the running game.
 */
public final class DimensionNames {
    private DimensionNames() {}

    public static String shortName(String dimensionKey) {
        if (dimensionKey == null) return "Okänd värld";
        return switch (dimensionKey) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimensionKey.contains(":") ? dimensionKey.substring(dimensionKey.indexOf(':') + 1) : dimensionKey;
        };
    }
}
