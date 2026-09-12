package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpdateManifestTest {

    private static final String VALID_SHA = "a".repeat(64);

    private static String manifestJson(String schemaVersion, String sha) {
        return """
                {
                  "schemaVersion": %s,
                  "version": "0.1.0-alpha.3",
                  "channel": "alpha",
                  "minecraftVersion": "26.1.2",
                  "fabricLoaderVersion": "0.19.5",
                  "fabricApiVersion": "0.155.3+26.1.2",
                  "installer": {
                    "fileName": "GZ-Companion-Setup.exe",
                    "sha256": "%s",
                    "sizeBytes": 52373858
                  },
                  "notes": ["Ny funktion", "Buggfix"]
                }
                """.formatted(schemaVersion, sha);
    }

    @Test
    @DisplayName("A valid manifest parses every field correctly")
    void validManifestParses() {
        Optional<UpdateManifest> result = UpdateManifest.parse(manifestJson("1", VALID_SHA));
        assertTrue(result.isPresent());
        UpdateManifest manifest = result.get();
        assertEquals(1, manifest.schemaVersion());
        assertEquals("0.1.0-alpha.3", manifest.version());
        assertEquals("alpha", manifest.channel());
        assertEquals("GZ-Companion-Setup.exe", manifest.installerFileName());
        assertEquals(VALID_SHA, manifest.installerSha256());
        assertEquals(52373858L, manifest.installerSizeBytes());
        assertEquals(2, manifest.notes().size());
    }

    @Test
    @DisplayName("An unsupported future schema version is rejected safely")
    void unsupportedSchemaVersionRejected() {
        assertTrue(UpdateManifest.parse(manifestJson("2", VALID_SHA)).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("99", VALID_SHA)).isEmpty());
    }

    @Test
    @DisplayName("A missing installer hash is rejected")
    void missingHashRejected() {
        String json = """
                {"schemaVersion":1,"version":"0.1.0-alpha.3","channel":"alpha","minecraftVersion":"26.1.2",
                 "fabricLoaderVersion":"0.19.5","fabricApiVersion":"0.155.3+26.1.2",
                 "installer":{"fileName":"GZ-Companion-Setup.exe","sizeBytes":100}}
                """;
        assertTrue(UpdateManifest.parse(json).isEmpty());
    }

    @Test
    @DisplayName("An invalid (non-hex or wrong-length) SHA-256 is rejected")
    void invalidShaRejected() {
        assertTrue(UpdateManifest.parse(manifestJson("1", "not-a-hash")).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("1", "abc123")).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("1", "g".repeat(64))).isEmpty());
    }

    @Test
    @DisplayName("A zero or negative installer size is rejected")
    void invalidSizeRejected() {
        String zeroSize = manifestJson("1", VALID_SHA).replace("52373858", "0");
        assertTrue(UpdateManifest.parse(zeroSize).isEmpty());
    }

    @Test
    @DisplayName("A path-traversal or separator-containing installer file name is rejected")
    void pathTraversalFileNameRejected() {
        assertTrue(UpdateManifest.parse(manifestJson("1", VALID_SHA).replace("GZ-Companion-Setup.exe", "..%2Fevil.exe")).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("1", VALID_SHA).replace("GZ-Companion-Setup.exe", "sub/evil.exe")).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("1", VALID_SHA).replace("GZ-Companion-Setup.exe", "..\\\\evil.exe")).isEmpty());
        assertTrue(UpdateManifest.parse(manifestJson("1", VALID_SHA).replace("GZ-Companion-Setup.exe", "C:evil.exe")).isEmpty());
    }

    @Test
    @DisplayName("Malformed JSON and non-object roots fail safely, never throw")
    void malformedJsonFailsSafely() {
        assertDoesNotThrow(() -> UpdateManifest.parse("not json at all"));
        assertTrue(UpdateManifest.parse("not json at all").isEmpty());
        assertTrue(UpdateManifest.parse("[1,2,3]").isEmpty());
        assertTrue(UpdateManifest.parse(null).isEmpty());
        assertTrue(UpdateManifest.parse("").isEmpty());
    }

    @Test
    @DisplayName("A missing required top-level field is rejected")
    void missingRequiredFieldRejected() {
        String json = manifestJson("1", VALID_SHA).replace("\"minecraftVersion\": \"26.1.2\",", "");
        assertTrue(UpdateManifest.parse(json).isEmpty());
    }
}
