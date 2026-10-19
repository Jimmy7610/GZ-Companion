package se.jimmyeliasson.gzcompanion.usage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural privacy guarantees for the anonymous installation counter, enforced by scanning its
 * source: the id comes only from a secure random UUID, and nothing about the player, account,
 * machine, network, world or gameplay can reach the counter.
 */
class UsagePrivacyTest {
    private static final Path USAGE_DIR = Path.of("src", "main", "java", "se", "jimmyeliasson", "gzcompanion", "usage");

    private static List<String> sources() throws IOException {
        List<String> contents = new ArrayList<>();
        try (Stream<Path> s = Files.list(USAGE_DIR)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) contents.add(Files.readString(p));
        }
        assertTrue(contents.size() >= 7, "expected the usage sources");
        return contents;
    }

    @Test
    @DisplayName("No Minecraft, account or gameplay code is reachable from the counter")
    void noGameOrIdentityAccess() throws IOException {
        for (String src : sources()) {
            for (String forbidden : List.of("import net.minecraft", "import net.fabricmc", "getUUID", "getGameProfile", "getName()",
                    "Minecraft.getInstance", "getUser()", "getSession(", "CompanionSession", "gzcompanion.chest", "gzcompanion.guide", "gzcompanion.marketwatch",
                    "gzcompanion.settlement", "gzcompanion.settings", "gzcompanion.online", "gzcompanion.events", "gzcompanion.core")) {
                assertFalse(src.contains(forbidden), "The usage counter must not touch: " + forbidden);
            }
        }
    }

    @Test
    @DisplayName("No device fingerprinting: no hardware, network, OS-user or environment identifiers")
    void noFingerprinting() throws IOException {
        for (String src : sources()) {
            for (String forbidden : List.of("NetworkInterface", "getHardwareAddress", "InetAddress", "getLocalHost", "System.getenv",
                    "System.getProperty", "user.name", "COMPUTERNAME", "MachineGuid", "nameUUIDFromBytes", "hostname", "getHostName")) {
                assertFalse(src.contains(forbidden), "No fingerprinting: " + forbidden);
            }
        }
    }

    @Test
    @DisplayName("The only id source is UUID.randomUUID() (a cryptographically secure random v4 UUID)")
    void onlyRandomIdSource() throws IOException {
        long randomUuidCalls = sources().stream().filter(s -> s.contains("UUID.randomUUID()")).count();
        assertEquals(1, randomUuidCalls, "Exactly one place generates ids");
        for (String src : sources()) {
            assertFalse(src.contains("new Random("), "Never a non-secure or seeded random");
        }
    }
}
