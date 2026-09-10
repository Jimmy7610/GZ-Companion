package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Layout for the MarketWatch tab: a fixed reference card (verified system facts + "Kopiera
 * /marketwatch") above a search bar and a single scrollable local-notes list. Unlike Crafting/
 * Settlement/Byggplaner there is no separate detail pane - notes are simple enough to edit
 * inline within their own list row.
 */
public record MarketWatchLayout(
    UiRect bounds,
    UiRect headerRect,
    UiRect referenceCardRect,
    UiRect searchRect,
    UiRect clearBtnRect,
    UiRect addBtnRect,
    UiRect listRect
) {
    public static MarketWatchLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        int headerH = 13;
        UiRect header = new UiRect(x, y, width, headerH);

        int refCardH = 46;
        UiRect refCard = new UiRect(x, y + headerH + 2, width, refCardH);

        int searchY = refCard.bottom() + 3;
        int searchH = 13;
        int clearW = 11;
        int addW = Math.min(70, (int) (width * 0.3f));
        int gap = 2;
        int searchW = Math.max(10, width - clearW - addW - (gap * 2));
        UiRect search = new UiRect(x, searchY, searchW, searchH);
        UiRect clearBtn = new UiRect(search.right() + gap, searchY, clearW, searchH);
        UiRect addBtn = new UiRect(clearBtn.right() + gap, searchY, addW, searchH);

        int listTop = search.bottom() + 3;
        int listH = Math.max(10, (y + height) - listTop);
        UiRect list = new UiRect(x, listTop, width, listH);

        return new MarketWatchLayout(bounds, header, refCard, search, clearBtn, addBtn, list);
    }
}
