package se.jimmyeliasson.gzcompanion.ui;

/**
 * Centralized constants for all controlled static text on the Home ("Hem") tab.
 * Prevents string scattering and accidental truncation regressions.
 */
public final class HomeCopy {
    private HomeCopy() {}

    // Welcome Card
    public static final String WELCOME_PREFIX = "Välkommen,";
    public static final String WELCOME_SUBTITLE = "Din hjälp i spelet.";
    public static final String WELCOME_NOTE = "Allt sparas lokalt.";

    // Server Status Card
    public static final String SERVER_TITLE = "Serverstatus";
    public static final String SERVER_ONLINE_BADGE = "Online";
    public static final String SERVER_LOCAL_BADGE = "Lokal";
    public static final String SERVER_LABEL_PROFILE = "Profil:";
    public static final String SERVER_LABEL_STATUS = "Status:";
    public static final String SERVER_LABEL_SERVER = "Server:";
    public static final String SERVER_PROFILE_GAMEZONE = "GameZoneMC";
    public static final String SERVER_PROFILE_STANDALONE = "Fristående";
    public static final String SERVER_STATUS_CONNECTED = "Ansluten";
    public static final String SERVER_STATUS_DISCONNECTED = "Ej ansluten";
    public static final String SERVER_LOCAL_WORLD = "Lokal värld";

    // Version Strip Headers
    public static final String STRIP_HEADER_MINECRAFT = "Minecraft";
    public static final String STRIP_HEADER_COMPANION = "Companion";
    public static final String STRIP_HEADER_RULE_PACK = "Rule Pack";
    public static final String STRIP_HEADER_STATUS = "Status";
    public static final String RULE_PACK_FALLBACK = "Ej laddad";

    // Objective Card
    public static final String OBJECTIVE_HEADER = "Nästa uppgift";
    public static final String OBJECTIVE_TITLE = "Öppna guiden och kom igång";
    public static final String OBJECTIVE_DESC = "Lär dig steg för steg.";
    public static final String CHECK_ITEM_1 = "[ ] Öppna nybörjarguiden";
    public static final String CHECK_ITEM_2 = "[ ] Lär dig grunderna";
    public static final String CHECK_ITEM_3 = "[ ] Utforska säkert";

    // Actions
    public static final String ACTION_OPEN_GUIDE = "Öppna Guide";
    public static final String ACTION_WHAT_TO_DO = "Vad ska jag göra?";
    public static final String ACTION_COMPATIBILITY = "Kompatibilitet";

    // Module Status Card
    public static final String MODULES_TITLE = "Modulstatus";
    public static final String MODULE_STATUS_ACTIVE = "Aktiv";
    public static final String MODULE_STATUS_SOON = "Snart";
}