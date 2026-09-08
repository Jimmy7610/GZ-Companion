package se.jimmyeliasson.gzcompanion.storage;

public class CompanionConfig {
    private int schemaVersion = 1;
    private String language = "sv_se";
    private boolean enableTranslucentUi = true;
    private boolean showNextObjectiveCard = true;
    private String lastSeenVersion = "0.1.0-alpha";

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isEnableTranslucentUi() {
        return enableTranslucentUi;
    }

    public void setEnableTranslucentUi(boolean enableTranslucentUi) {
        this.enableTranslucentUi = enableTranslucentUi;
    }

    public boolean isShowNextObjectiveCard() {
        return showNextObjectiveCard;
    }

    public void setShowNextObjectiveCard(boolean showNextObjectiveCard) {
        this.showNextObjectiveCard = showNextObjectiveCard;
    }

    public String getLastSeenVersion() {
        return lastSeenVersion;
    }

    public void setLastSeenVersion(String lastSeenVersion) {
        this.lastSeenVersion = lastSeenVersion;
    }
}
