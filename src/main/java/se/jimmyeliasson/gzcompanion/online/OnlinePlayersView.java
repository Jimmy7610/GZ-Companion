package se.jimmyeliasson.gzcompanion.online;

import java.util.ArrayList;
import java.util.List;

/**
 * The fully grouped, sorted, and (if a search query was given) filtered result of
 * {@link OnlinePlayersGrouping#build} - ready for the Online tab to render section-by-section.
 *
 * @param onlineCount the RAW count of currently online players, unaffected by any search filter -
 *                    this is what the tab header's "N spelare" and Home's clickable player count
 *                    should show, so searching never changes the displayed server population.
 */
public record OnlinePlayersView(
    List<OnlinePlayerRow> favorites,
    List<OnlinePlayerRow> settlementMembers,
    List<OnlinePlayerRow> others,
    int onlineCount
) {
    public List<OnlinePlayerRow> allRows() {
        List<OnlinePlayerRow> all = new ArrayList<>(favorites.size() + settlementMembers.size() + others.size());
        all.addAll(favorites);
        all.addAll(settlementMembers);
        all.addAll(others);
        return all;
    }
}
