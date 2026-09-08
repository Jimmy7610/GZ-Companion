package se.jimmyeliasson.gzcompanion.profile;

public record ServerProfile(
    String id,
    String displayName,
    String primaryHost,
    boolean isGameZone
) {
    public static final ServerProfile GAMEZONE = new ServerProfile(
        "gamezone",
        "GameZoneMC",
        "play.gamezonemc.se",
        true
    );

    public static final ServerProfile GENERIC = new ServerProfile(
        "generic",
        "Annan server / Lokal",
        "",
        false
    );
}
