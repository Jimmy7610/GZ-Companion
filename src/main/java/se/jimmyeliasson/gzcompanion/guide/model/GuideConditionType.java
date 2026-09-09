package se.jimmyeliasson.gzcompanion.guide.model;

/**
 * Supported condition types for Guide Step evaluation.
 */
public enum GuideConditionType {
    MANUAL,
    HAS_ITEM,
    HAS_ANY_ITEM,
    HAS_ITEM_TAG,
    HAS_EDIBLE_ITEM,
    ALL_OF,
    ANY_OF
}