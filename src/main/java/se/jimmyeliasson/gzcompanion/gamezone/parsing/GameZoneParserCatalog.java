package se.jimmyeliasson.gzcompanion.gamezone.parsing;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable, loaded-once collection of {@link GameZoneParserDefinition}s. Precomputes the active
 * subset once at construction - {@link GameZoneParserEngine} only ever iterates
 * {@link #activeParsers()}, never re-filters per message.
 */
public final class GameZoneParserCatalog {
    private final List<GameZoneParserDefinition> parsers;
    private final List<GameZoneParserDefinition> activeParsers;
    private final List<String> loadWarnings;

    public GameZoneParserCatalog(List<GameZoneParserDefinition> parsers, List<String> loadWarnings) {
        this.parsers = parsers != null ? List.copyOf(parsers) : List.of();
        this.loadWarnings = loadWarnings != null ? List.copyOf(loadWarnings) : List.of();

        List<GameZoneParserDefinition> active = new ArrayList<>();
        for (GameZoneParserDefinition parser : this.parsers) {
            if (parser.isActive()) active.add(parser);
        }
        this.activeParsers = List.copyOf(active);
    }

    public static GameZoneParserCatalog empty() {
        return new GameZoneParserCatalog(List.of(), List.of());
    }

    public List<GameZoneParserDefinition> parsers() {
        return parsers;
    }

    /** Only parsers that are both {@code enabled} and VERIFIED - see {@link GameZoneParserDefinition#isActive()}. */
    public List<GameZoneParserDefinition> activeParsers() {
        return activeParsers;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public int size() {
        return parsers.size();
    }

    public int activeCount() {
        return activeParsers.size();
    }
}
