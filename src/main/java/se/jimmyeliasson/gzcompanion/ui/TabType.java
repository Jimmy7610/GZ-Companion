package se.jimmyeliasson.gzcompanion.ui;

/**
 * The 9 navigation sections defined for GZ Companion.
 * Uses bundled high-definition pixel icons and authentic Swedish localization.
 */
public enum TabType {
    HEM("Hem", IconId.HOME, "Startskärm och översikt"),
    GUIDE("Guide", IconId.GUIDE, "Interaktiva guider och progression"),
    CRAFTING("Crafting", IconId.CRAFTING, "Recept och hantverkshjälp"),
    KISTOR("Kistor", IconId.CHEST, "Kist- och förvaringsöversikt"),
    SETTLEMENT("Settlement", IconId.SETTLEMENT, "Samhälls- och stadsverktyg"),
    BYGGPLANER("Byggplaner", IconId.BUILDING, "Byggnads- och materialplanerare"),
    MARKETWATCH("MarketWatch", IconId.MARKET, "Marknads- och ekonomibevakning"),
    KOMMANDON("Kommandon", IconId.COMMANDS, "Serverkommandon och snabbval"),
    INSTALLNINGAR("Inställningar", IconId.SETTINGS, "Inställningar för GZ Companion");

    private final String displayName;
    private final IconId icon;
    private final String description;

    TabType(String displayName, IconId icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public IconId getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}