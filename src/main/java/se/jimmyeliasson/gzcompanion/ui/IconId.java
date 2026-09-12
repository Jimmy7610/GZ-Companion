package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.resources.Identifier;

/**
 * High-definition, clean pixel UI icons bundled inside GZ Companion assets.
 */
public enum IconId {
    LOGO("logo"),
    HOME("home"),
    GUIDE("guide"),
    CRAFTING("crafting"),
    CHEST("chest"),
    SETTLEMENT("settlement"),
    BUILDING("building"),
    MARKET("market"),
    COMMANDS("commands"),
    SETTINGS("settings"),
    PLAYER("player"),
    SERVER("server"),
    LEADERBOARDS("leaderboards"),
    OBJECTIVE("objective"),
    COMPATIBILITY("compatibility");

    private final String filename;
    private final Identifier identifier;

    IconId(String filename) {
        this.filename = filename;
        this.identifier = Identifier.fromNamespaceAndPath("gzcompanion", "textures/gui/icons/" + filename + ".png");
    }

    public String getFilename() {
        return filename;
    }

    public Identifier getIdentifier() {
        return identifier;
    }
}