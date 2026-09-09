package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.guide.model.GuideChapter;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCondition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideDefinition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideStep;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuideValidatorTest {

    @Test
    @DisplayName("Should pass validation for a well-formed guide")
    void testValidGuide() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step1 = new GuideStep("s1", "ch1", 1, "Step 1", "Sum 1", "Desc 1", "Why 1", null, null, List.of(), false, true, List.of(GuideCondition.manual()), List.of());
        GuideStep step2 = new GuideStep("s2", "ch1", 2, "Step 2", "Sum 2", "Desc 2", "Why 2", null, null, List.of("s1"), false, true, List.of(GuideCondition.hasItem("minecraft:stick", 2, "Sticks")), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step1, step2));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should detect duplicate step IDs")
    void testDuplicateStepIds() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step1 = new GuideStep("dup_id", "ch1", 1, "Step 1", "Sum 1", "Desc 1", null, null, null, List.of(), false, true, List.of(), List.of());
        GuideStep step2 = new GuideStep("dup_id", "ch1", 2, "Step 2", "Sum 2", "Desc 2", null, null, null, List.of(), false, true, List.of(), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step1, step2));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate step ID")));
    }

    @Test
    @DisplayName("Should detect missing prerequisite references")
    void testMissingPrerequisite() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step1 = new GuideStep("s1", "ch1", 1, "Step 1", "Sum 1", "Desc 1", null, null, null, List.of("non_existent_prereq"), false, true, List.of(), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step1));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("non-existent prerequisite")));
    }

    @Test
    @DisplayName("Should detect circular prerequisite dependencies")
    void testCircularPrerequisites() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step1 = new GuideStep("s1", "ch1", 1, "Step 1", "Sum 1", "Desc 1", null, null, null, List.of("s2"), false, true, List.of(), List.of());
        GuideStep step2 = new GuideStep("s2", "ch1", 2, "Step 2", "Sum 2", "Desc 2", null, null, null, List.of("s1"), false, true, List.of(), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step1, step2));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Circular prerequisite")));
    }

    @Test
    @DisplayName("Should accept supported guide tags defined in GuideSupportedTags")
    void testSupportedGuideTagAccepted() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step = new GuideStep("s1", "ch1", 1, "Logs", "Logs", "Logs", null, null, null,
                List.of(), false, true, List.of(GuideCondition.hasItemTag("minecraft:logs", 4, "Logs")), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());

        GuideValidator.ValidationResult regResult = GuideValidator.validateRegistry(guide);
        assertTrue(regResult.isValid());
    }

    @Test
    @DisplayName("Should reject syntactically valid but unsupported guide tag")
    void testSyntacticallyValidButUnsupportedGuideTagRejected() {
        GuideChapter chapter = new GuideChapter("ch1", "Chapter 1", 1);
        GuideStep step = new GuideStep("s1", "ch1", 1, "Logz", "Logz", "Logz", null, null, null,
                List.of(), false, true, List.of(GuideCondition.hasItemTag("minecraft:logz", 4, "Logz")), List.of());

        GuideDefinition guide = new GuideDefinition(1, "test_guide", "Test Guide", "Desc", List.of(chapter), List.of(step));
        GuideValidator.ValidationResult result = GuideValidator.validate(guide);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unsupported tag: 'minecraft:logz'")));

        GuideValidator.ValidationResult regResult = GuideValidator.validateRegistry(guide);
        assertFalse(regResult.isValid());
        assertTrue(regResult.errors().stream().anyMatch(e -> e.contains("unsupported item tag in GuideSupportedTags: 'minecraft:logz'")));
    }
}
