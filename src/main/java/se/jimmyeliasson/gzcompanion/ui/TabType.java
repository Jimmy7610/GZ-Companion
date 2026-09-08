package se.jimmyeliasson.gzcompanion.ui;

/**
 * The 9 navigation sections defined for GZ Companion.
 */
public enum TabType {
    HEM("Hem", "⌂", "Startskärm och översikt"),
    GUIDE("Guide", "📖", "Interaktiva guider och progression"),
    CRAFTING("Crafting", "🎲", "Recept och hantverkshjälp"),
    KISTOR("Kistor", "📦", "Kist- och förvaringsöversikt"),
    SETTLEMENT("Settlement", "🏰", "Samhälls- och stadsverktyg"),
    BYGGPLANER("Byggplaner", "📄", "Byggnads- och materialplanerare"),
    MARKETWATCH("MarketWatch", "📊", "Marknads- och ekonomibevakning"),
    KOMMANDON("Kommandon", "⌨", "Serverkommandon och snabbval"),
    INSTALLNINGAR("Inställningar", "⚙", "Inställningar för GZ Companion");

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