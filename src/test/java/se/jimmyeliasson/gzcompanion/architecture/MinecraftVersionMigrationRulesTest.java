package se.jimmyeliasson.gzcompanion.architecture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Minecraft 26.3 port: the target version is declared consistently everywhere, and the
 * input pitfalls that compile fine but break silently on 26.3 cannot come back.
 */
class MinecraftVersionMigrationRulesTest {
    private static final Path MAIN_SRC = Path.of("src", "main", "java");

    @Test
    @DisplayName("gradle.properties, fabric.mod.json and CompanionConstants agree on the target Minecraft version")
    void targetVersionIsConsistent() throws IOException {
        Properties props = new Properties();
        try (var in = Files.newInputStream(Path.of("gradle.properties"))) {
            props.load(in);
        }
        assertEquals(CompanionConstants.TARGET_MINECRAFT_VERSION, props.getProperty("minecraft_version"));
        assertTrue(props.getProperty("fabric_version").endsWith("+" + CompanionConstants.TARGET_MINECRAFT_VERSION),
                "Fabric API must be the build for the target Minecraft version");

        JsonObject modJson = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources", "fabric.mod.json"))).getAsJsonObject();
        assertEquals(CompanionConstants.TARGET_MINECRAFT_VERSION, modJson.getAsJsonObject("depends").get("minecraft").getAsString());
    }

    @Test
    @DisplayName("Both bundled data packs list the target Minecraft version (the Guide is disabled otherwise)")
    void dataPacksListTargetVersion() throws IOException {
        for (Path manifest : List.of(Path.of("guide-content", "manifest.json"), Path.of("gamezone-pack", "manifest.json"))) {
            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            List<String> tested = json.getAsJsonArray("testedMinecraftVersions").asList().stream().map(e -> e.getAsString()).toList();
            assertTrue(tested.contains(CompanionConstants.TARGET_MINECRAFT_VERSION), manifest + " lists " + tested);
        }
    }

    @Test
    @DisplayName("Without Fabric Loader the running Minecraft version falls back to the target version")
    void runningVersionFallsBackToTarget() {
        assertEquals(CompanionConstants.TARGET_MINECRAFT_VERSION, CompanionConstants.runningMinecraftVersion());
    }

    @Test
    @DisplayName("No GLFW input constants (removed in 26.3) and no raw mouse-button numbers in tab click handlers")
    void noVersionSpecificInputLiterals() throws IOException {
        Pattern rawButton = Pattern.compile("button\\s*(==|!=)\\s*\\d");
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN_SRC)) {
            violations = files.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    String src = Files.readString(p);
                    Stream.Builder<String> found = Stream.builder();
                    if (src.contains("org.lwjgl.glfw")) found.add(p + ": uses GLFW");
                    if (rawButton.matcher(src).find()) found.add(p + ": compares a raw mouse button number");
                    return found.build();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        }
        assertEquals(List.of(), violations, "use InputConstants.KEY_* and UiMouseButton instead");
    }
}
