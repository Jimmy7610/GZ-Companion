package se.jimmyeliasson.gzcompanion.gamezone.parsing;

/** How a {@code GameZoneParserDefinition}'s pattern is matched against a received message. */
public enum ParserMatchType {
    /** The message must equal the pattern exactly (after trimming). */
    EXACT,
    /** The message must contain the pattern as a substring. */
    CONTAINS,
    /** The pattern is a Java regular expression; named/positional groups become captured values. */
    REGEX
}
