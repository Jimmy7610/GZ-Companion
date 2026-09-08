package se.jimmyeliasson.gzcompanion.ui;

/**
 * The 9 navigation sections defined for GZ Companion.
 * Uses reliable ASCII/Latin-1 glyphs that render crisply across all Minecraft GUI scales and font engines.
 */
public enum TabType {
    HEM("Hem", "#", "Startskarm och oversikt"),
    GUIDE("Guide", "?", "Interaktiva guider och progression"),
    CRAFTING("Crafting", "+", "Recept och hantverkshjalp"),
    KISTOR("Kistor", "=", "Kist- och forvaringsoversikt"),
    SETTLEMENT("Settlement", "^", "Samhalls- och stadsverktyg"),
    BYGGPLANER("Byggplaner", "%", "Byggnads- och materialplanerare"),
    MARKETWATCH("MarketWatch", "$", "Marknads- och ekonomibevakning"),
    KOMMANDON("Kommandon", "/", "Serverkommandon och snabbval"),
    INSTALLNINGAR("Installningar", "*", "Installningar for GZ Companion");

    private final String displayName;
    private final String iconSymbol;
    private final String description;

    TabType(String displayName, String iconSymbol, String description) {
        this.displayName = displayName;
        this.iconSymbol = iconSymbol;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconSymbol() {
        return iconSymbol;
    }

    public String getDescription() {
        return description;
    }
}