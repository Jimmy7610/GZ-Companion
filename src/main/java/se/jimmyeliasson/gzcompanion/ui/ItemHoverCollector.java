package se.jimmyeliasson.gzcompanion.ui;

import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Per-frame collector of hover regions - the exact same convention every tab already uses for
 * CLICK targets (a {@code ListRowHit(UiRect rect, ...)} list cleared at the top of {@code render()}
 * and repopulated every frame), repurposed here for hover. Every user clears this at the start of
 * its own render() and registers one target per region of interest; later-registered targets win
 * ties, since later draws paint over earlier ones, so "last registered" is exactly "topmost on
 * screen" - never multiple overlapping results.
 */
public final class ItemHoverCollector<T> {
    public record Target<T>(UiRect rect, T payload) {}

    private final List<Target<T>> targets = new ArrayList<>();

    public void clear() {
        targets.clear();
    }

    public void register(UiRect rect, T payload) {
        targets.add(new Target<>(rect, payload));
    }

    /**
     * Drops any already-registered target matching the predicate - for filtering out targets that
     * scrolled outside their own scissored area after the fact, mirroring the identical
     * {@code hitTargets.subList(...).removeIf(...)} pattern already used for click targets in
     * BuildingsTabComponent, where rows are drawn unconditionally and clipped only by GL scissor.
     */
    public void removeIf(Predicate<Target<T>> predicate) {
        targets.removeIf(predicate);
    }

    /** The most recently registered target whose rect contains the point, or empty if none. */
    public Optional<T> hovered(int mouseX, int mouseY) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target<T> target = targets.get(i);
            if (target.rect().contains(mouseX, mouseY)) {
                return Optional.of(target.payload());
            }
        }
        return Optional.empty();
    }

    public int size() {
        return targets.size();
    }

    /**
     * A target rect counts as outside a scissored/visible area if it is entirely above or below
     * it - the same vertical-only check every scrollable list/detail pane in this mod already uses
     * for its click-target equivalent (rows only ever scroll vertically here, never horizontally).
     */
    public static boolean isOutsideVisibleArea(UiRect rect, UiRect visibleArea) {
        return rect.bottom() <= visibleArea.y() || rect.y() >= visibleArea.bottom();
    }
}
