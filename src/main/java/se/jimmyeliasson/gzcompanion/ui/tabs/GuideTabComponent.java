package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.guide.GuideEngine;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.condition.GuideConditionEvaluator;
import se.jimmyeliasson.gzcompanion.guide.model.*;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.GuideLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Guide tab component rendering 2-pane progression navigation,
 * dynamic keybindings, live inventory condition validation, manual toggles, and reset controls.
 * Read-only during rendering (no mutation or disk writes).
 */
public class GuideTabComponent {
    private String selectedStepId = null;
    private int scrollOffset = 0;
    private boolean confirmingReset = false;
    private long resetConfirmExpiry = 0;
    private boolean compactShowingDetail = false;

    private GuideLayout layout;
    private final List<StepRowHit> stepHitTargets = new ArrayList<>();

    public record StepRowHit(UiRect rect, String stepId) {}

    public GuideLayout getLayout() {
        return layout;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
    }

    public List<StepRowHit> getStepHitTargets() {
        return stepHitTargets;
    }

    public void selectStep(String stepId) {
        this.selectedStepId = stepId;
        this.compactShowingDetail = (stepId != null);
    }

    public String getSelectedStepId() {
        return selectedStepId;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = GuideLayout.calculate(bounds);
        stepHitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        GuideEngine engine = session.getGuideEngine();
        GuideContext context = session.getCurrentGuideContext();

        if (engine == null || engine.getLoadStatus() != GuideLoadStatus.LOADED || engine.getActiveGuide() == null) {
            drawUnavailableState(extractor, font, bounds, engine != null ? engine.getLoadStatus() : GuideLoadStatus.UNAVAILABLE);
            return;
        }

        GuideDefinition guide = engine.getActiveGuide();
        GuideEngine.ProgressSummary progress = engine.getOverallProgress(context);

        // Auto-select active step if nothing selected
        if (selectedStepId == null) {
            GuideStep activeStep = engine.getActiveOrNextStep(context);
            if (activeStep != null) {
                selectedStepId = activeStep.id();
            } else if (!guide.steps().isEmpty()) {
                selectedStepId = guide.steps().get(0).id();
            }
        }

        // 1. TOP HEADER & PROGRESS STRIP
        renderHeader(extractor, font, layout.headerRect(), layout.progressBarRect(), guide, progress);

        // 2. CONTENT (2-Pane or Compact Single-Pane)
        if (layout.isCompact()) {
            if (compactShowingDetail && selectedStepId != null) {
                renderDetailPane(extractor, font, layout.detailRect(), engine, context, mouseX, mouseY, true);
            } else {
                renderNavigator(extractor, font, layout.leftNavRect(), engine, context, guide, mouseX, mouseY);
            }
        } else {
            renderNavigator(extractor, font, layout.leftNavRect(), engine, context, guide, mouseX, mouseY);
            renderDetailPane(extractor, font, layout.detailRect(), engine, context, mouseX, mouseY, false);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, UiRect progressBarRect,
                              GuideDefinition guide, GuideEngine.ProgressSummary progress) {
        GZTheme.drawCard(extractor, headerRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int iconY = headerRect.y() + ((headerRect.height() - 10) / 2);
        GZTheme.drawIcon(extractor, IconId.GUIDE, headerRect.x() + 5, iconY, 10, GZTheme.COLOR_MINT);

        String title = guide.title();
        TextUtil.drawScaledEllipsizedText(extractor, font, title, headerRect.x() + 18, headerRect.y() + 3,
                headerRect.width() - 120, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        // Progress Text Badge (e.g. "21/21 (100%)")
        String progressText = progress.completedCount() + "/" + progress.totalCount() + " (" + progress.percent() + "%)";
        int badgeW = TextUtil.scaledWidth(font, progressText, TypographyScale.SMALL.getScale()) + 10;
        int badgeX = headerRect.right() - badgeW - 5;
        GZTheme.drawBadge(extractor, font, badgeX, headerRect.y() + 2, progressText,
                progress.percent() == 100 ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_MINT,
                progress.percent() == 100 ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_EMERALD);

        // Progress Bar
        extractor.fill(progressBarRect.x(), progressBarRect.y(), progressBarRect.right(), progressBarRect.bottom(), 0xFF1E293B);
        if (progress.totalCount() > 0 && progress.completedCount() > 0) {
            int fillW = Math.max(2, (progressBarRect.width() * progress.completedCount()) / progress.totalCount());
            extractor.fill(progressBarRect.x(), progressBarRect.y(), progressBarRect.x() + fillW, progressBarRect.bottom(), GZTheme.COLOR_EMERALD);
        }
    }

    public static int calculateMaxScroll(UiRect navRect, GuideEngine engine, GuideDefinition guide) {
        if (navRect == null || engine == null || guide == null) return 0;
        int itemH = 14;
        int chHeaderH = 15;
        int totalContentH = 6;
        for (GuideChapter chapter : guide.chapters()) {
            totalContentH += chHeaderH + 1;
            totalContentH += (engine.getStepsForChapter(chapter.id()).size() * (itemH + 1));
            totalContentH += 3;
        }
        int visibleH = Math.max(1, navRect.height() - 4);
        return Math.max(0, totalContentH - visibleH);
    }

    private void renderNavigator(GuiGraphicsExtractor extractor, Font font, UiRect navRect, GuideEngine engine,
                                 GuideContext context, GuideDefinition guide, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, navRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int maxScroll = calculateMaxScroll(navRect, engine, guide);
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        extractor.enableScissor(navRect.x() + 1, navRect.y() + 1, navRect.right() - 1, navRect.bottom() - 1);

        int itemH = 14;
        int chHeaderH = 15;
        int currentY = navRect.y() + 3 - scrollOffset;

        int chIndex = 1;
        for (GuideChapter chapter : guide.chapters()) {
            List<GuideStep> chapterSteps = engine.getStepsForChapter(chapter.id());

            // Chapter Header
            UiRect chRect = new UiRect(navRect.x() + 2, currentY, navRect.width() - 4, chHeaderH);
            if (currentY + chHeaderH >= navRect.y() && currentY <= navRect.bottom()) {
                GuideEngine.ProgressSummary chProg = engine.getChapterProgress(context, chapter.id());
                extractor.fill(chRect.x(), chRect.y(), chRect.right(), chRect.bottom(), 0x401E293B);

                String chTitle = chapter.title();
                String chStatus = chProg.completedCount() + "/" + chProg.totalCount();
                int chStatusW = TextUtil.scaledWidth(font, chStatus, TypographyScale.META.getScale());

                TextUtil.drawScaledEllipsizedText(extractor, font, chTitle, chRect.x() + 3, chRect.y() + 3,
                        chRect.width() - chStatusW - 10, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
                TextUtil.drawScaledRightAlignedText(extractor, font, chStatus, chRect.right() - 3, chRect.y() + 3,
                        chStatusW + 2, TypographyScale.META.getScale(),
                        chProg.percent() == 100 ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
            }
            currentY += chHeaderH + 1;

            int stepIndex = 1;
            for (GuideStep step : chapterSteps) {
                UiRect stepRect = new UiRect(navRect.x() + 3, currentY, navRect.width() - 6, itemH);

                // Only register hit testing for rows within visible navigator view
                if (stepRect.bottom() >= navRect.y() + 2 && stepRect.y() <= navRect.bottom() - 2) {
                    stepHitTargets.add(new StepRowHit(stepRect, step.id()));
                }

                if (currentY + itemH >= navRect.y() && currentY <= navRect.bottom()) {
                    boolean isSelected = step.id().equals(selectedStepId);
                    boolean isHovered = stepRect.contains(mouseX, mouseY);
                    GuideStepState state = engine.getStepState(context, step.id());

                    int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                    int border = isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0;
                    if (bg != 0) {
                        GZTheme.drawCard(extractor, stepRect, bg, border);
                    }

                    // Step Status Dot
                    int dotColor = getStepColor(state);
                    GZTheme.drawStatusDot(extractor, stepRect.x() + 4, stepRect.y() + 4, dotColor);

                    // Step Label
                    String stepLabel = chIndex + "." + stepIndex + " " + step.title();
                    int textTint = isSelected ? GZTheme.COLOR_TEXT_PRIMARY : (isHovered ? GZTheme.COLOR_TEXT_PRIMARY : getStepTextColor(state));
                    TextUtil.drawScaledEllipsizedText(extractor, font, stepLabel, stepRect.x() + 12, stepRect.y() + 2,
                            stepRect.width() - 16, TypographyScale.SMALL.getScale(), textTint, isSelected);
                }

                currentY += itemH + 1;
                stepIndex++;
            }

            currentY += 3;
            chIndex++;
        }

        extractor.disableScissor();
    }

    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, GuideEngine engine,
                                  GuideContext context, int mouseX, int mouseY, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        GuideStep step = engine.getStep(selectedStepId);
        if (step == null) {
            TextUtil.drawCenteredText(extractor, font, "Välj ett steg i listan", detailRect.x() + (detailRect.width() / 2),
                    detailRect.y() + (detailRect.height() / 2) - 4, detailRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        GuideStepState state = engine.getStepState(context, step.id());
        int pad = 6;
        int currY = detailRect.y() + pad;

        // Compact Back Button
        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            boolean backHover = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHover, TypographyScale.SMALL.getScale());
            currY += 14;
        }

        // 1. Header: Step Title & State Badge
        String badgeLabel = getStateBadgeLabel(state);
        int badgeColor = getStepColor(state);
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 8;
        int badgeX = detailRect.right() - badgeW - pad;

        int availTitleW = badgeX - (detailRect.x() + pad) - 6;
        TextUtil.drawScaledEllipsizedText(extractor, font, step.title(), detailRect.x() + pad, currY,
                availTitleW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        GZTheme.drawBadge(extractor, font, badgeX, currY, badgeLabel, badgeColor, badgeColor);
        currY += 14;

        // 2. Summary
        if (step.summary() != null && !step.summary().isBlank()) {
            String summary = engine.resolveTokens(step.summary());
            TextUtil.drawScaledEllipsizedText(extractor, font, summary, detailRect.x() + pad, currY,
                    detailRect.width() - (pad * 2), TypographyScale.BODY.getScale(), GZTheme.COLOR_MINT, false);
            currY += 11;
        }

        // Description
        String desc = engine.resolveTokens(step.description());
        int descW = detailRect.width() - (pad * 2);
        TextUtil.drawScaledEllipsizedText(extractor, font, desc, detailRect.x() + pad, currY,
                descW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        currY += 12;

        // Divider
        extractor.fill(detailRect.x() + pad, currY, detailRect.right() - pad, currY + 1, GZTheme.COLOR_BORDER_SUBTLE);
        currY += 4;

        // 3. Why / Rationale
        if (step.why() != null && !step.why().isBlank()) {
            TextUtil.drawScaledText(extractor, font, "Varför?", detailRect.x() + pad, currY,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, true);
            currY += 8;
            String whyText = engine.resolveTokens(step.why());
            TextUtil.drawScaledEllipsizedText(extractor, font, whyText, detailRect.x() + pad, currY,
                    detailRect.width() - (pad * 2), TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            currY += 11;
        }

        // 4. Live Condition / Inventory Requirements Box
        if (step.conditions() != null && !step.conditions().isEmpty()) {
            for (GuideCondition cond : step.conditions()) {
                if (cond.type() != GuideConditionType.MANUAL) {
                    UiRect condBox = new UiRect(detailRect.x() + pad, currY, detailRect.width() - (pad * 2), 22);
                    GZTheme.drawCard(extractor, condBox, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);

                    GuidePlayerSnapshot snapshot = engine.getLastSnapshot();
                    boolean isMet = (state == GuideStepState.COMPLETED_AUTO || state == GuideStepState.SATISFIED_BY_LATER_PROGRESS);
                    if (!isMet && snapshot != null) {
                        isMet = GuideConditionEvaluator.evaluate(cond, snapshot).satisfied();
                    }

                    String reqLabel = getConditionRequirementLabel(cond);
                    String statusLabel = isMet ? "Uppfyllt" : "Ej uppfyllt";
                    int condDotColor = isMet ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_YELLOW;

                    TextUtil.drawScaledText(extractor, font, "Krav i ryggsäck:", condBox.x() + 4, condBox.y() + 2,
                            TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
                    TextUtil.drawScaledEllipsizedText(extractor, font, reqLabel, condBox.x() + 4, condBox.y() + 10,
                            condBox.width() - 50, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);

                    GZTheme.drawStatusDot(extractor, condBox.right() - 44, condBox.y() + 7, condDotColor);
                    TextUtil.drawScaledRightAlignedText(extractor, font, statusLabel, condBox.right() - 4, condBox.y() + 5,
                            36, TypographyScale.META.getScale(), condDotColor, false);

                    currY += 25;
                }
            }
        }

        // 5. Tip Box (if exists)
        if (step.tip() != null && !step.tip().isBlank()) {
            int tipH = 15;
            UiRect tipBox = new UiRect(detailRect.x() + pad, currY, detailRect.width() - (pad * 2), tipH);
            GZTheme.drawCard(extractor, tipBox, 0x3310B981, GZTheme.COLOR_BORDER_EMERALD);
            String tipText = "Tips: " + engine.resolveTokens(step.tip());
            TextUtil.drawScaledEllipsizedText(extractor, font, tipText, tipBox.x() + 4, tipBox.y() + 3,
                    tipBox.width() - 8, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, false);
            currY += 18;
        }

        // 6. Action Buttons at Bottom
        UiRect markBtn = layout.markDoneBtnRect();
        UiRect resetBtn = layout.resetBtnRect();

        boolean isCompleted = (state == GuideStepState.COMPLETED_AUTO || state == GuideStepState.COMPLETED_MANUAL || state == GuideStepState.SATISFIED_BY_LATER_PROGRESS);

        if (state == GuideStepState.COMPLETED_MANUAL) {
            boolean hov = markBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, markBtn, "Ångra markering", false, hov, TypographyScale.BODY.getScale());
        } else if (!isCompleted && state != GuideStepState.LOCKED) {
            if (step.manualCompletionAllowed()) {
                boolean hov = markBtn.contains(mouseX, mouseY);
                GZTheme.drawButton(extractor, font, markBtn, "Markera klar", true, hov, TypographyScale.BODY.getScale());
            } else {
                GZTheme.drawButton(extractor, font, markBtn, "Kräver inventarier", false, false, TypographyScale.SMALL.getScale());
            }
        } else if (state == GuideStepState.LOCKED) {
            GZTheme.drawButton(extractor, font, markBtn, "Låst", false, false, TypographyScale.SMALL.getScale());
        } else {
            GZTheme.drawButton(extractor, font, markBtn, "Slutförd ✓", false, false, TypographyScale.BODY.getScale());
        }

        // Reset Button
        boolean resetHov = resetBtn.contains(mouseX, mouseY);
        String resetLabel = isResetConfirmActive() ? "Bekräfta!" : "Återställ";
        int resetBg = isResetConfirmActive() ? 0x99EF4444 : (resetHov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
        int resetBorder = isResetConfirmActive() ? 0xFFEF4444 : GZTheme.COLOR_BORDER_SUBTLE;
        int resetText = isResetConfirmActive() ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED;

        GZTheme.drawCard(extractor, resetBtn, resetBg, resetBorder);
        TextUtil.drawCenteredText(extractor, font, resetLabel, resetBtn.x() + (resetBtn.width() / 2),
                resetBtn.y() + ((resetBtn.height() - 7) / 2), resetBtn.width(), resetText, false);
    }

    private String getConditionRequirementLabel(GuideCondition cond) {
        if (cond == null) return "Inga krav";
        if (cond.description() != null && !cond.description().isBlank()) {
            return cond.description();
        }
        return switch (cond.type()) {
            case MANUAL -> "Manuell kontroll";
            case HAS_ITEM -> cond.count() + "x " + cond.itemId();
            case HAS_ANY_ITEM -> "Något av: " + (cond.itemIds() != null ? String.join(", ", cond.itemIds()) : "");
            case HAS_ITEM_TAG -> cond.count() + "x #" + cond.tag();
            case HAS_EDIBLE_ITEM -> cond.count() + "x Ätbar mat";
            case ALL_OF -> "Flera villkor (" + (cond.subConditions() != null ? cond.subConditions().size() : 0) + ")";
            case ANY_OF -> "Ett av alternativen";
        };
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect bounds, GuideLoadStatus status) {
        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av guider.";
            case INCOMPATIBLE -> "Guiden är inte kompatibel med denna version.";
            case UNAVAILABLE -> "Inga guider är tillgängliga för närvarande.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawCenteredText(extractor, font, msg, bounds.x() + (bounds.width() / 2),
                bounds.y() + (bounds.height() / 2) - 4, bounds.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private int getStepColor(GuideStepState state) {
        return switch (state) {
            case COMPLETED_AUTO, COMPLETED_MANUAL -> GZTheme.COLOR_STATUS_GREEN;
            case SATISFIED_BY_LATER_PROGRESS -> 0xFF34D399;
            case ACTIVE -> GZTheme.COLOR_EMERALD;
            case AVAILABLE -> 0xFF94A3B8;
            case LOCKED -> 0xFF475569;
        };
    }

    private int getStepTextColor(GuideStepState state) {
        return switch (state) {
            case COMPLETED_AUTO, COMPLETED_MANUAL, SATISFIED_BY_LATER_PROGRESS -> GZTheme.COLOR_TEXT_MUTED;
            case ACTIVE -> GZTheme.COLOR_MINT;
            case AVAILABLE -> GZTheme.COLOR_TEXT_PRIMARY;
            case LOCKED -> GZTheme.COLOR_TEXT_MUTED;
        };
    }

    private String getStateBadgeLabel(GuideStepState state) {
        return switch (state) {
            case COMPLETED_AUTO -> "KLAR (AUTO)";
            case COMPLETED_MANUAL -> "KLAR";
            case SATISFIED_BY_LATER_PROGRESS -> "ERSATT";
            case ACTIVE -> "PÅGÅENDE";
            case AVAILABLE -> "TILLGÄNGLIG";
            case LOCKED -> "LÅST";
        };
    }

    private boolean isResetConfirmActive() {
        return confirmingReset && System.currentTimeMillis() < resetConfirmExpiry;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        if (layout == null) layout = GuideLayout.calculate(bounds);

        CompanionSession session = CompanionSession.getInstance();
        GuideEngine engine = session.getGuideEngine();
        GuideContext context = session.getCurrentGuideContext();

        // Compact Back Button
        if (layout.isCompact() && compactShowingDetail) {
            if (layout.backBtnRect().contains(mouseX, mouseY)) {
                compactShowingDetail = false;
                return true;
            }
        }

        // Navigator Step Clicks
        for (StepRowHit hit : stepHitTargets) {
            if (hit.rect.contains(mouseX, mouseY)) {
                selectStep(hit.stepId);
                return true;
            }
        }

        // Detail Pane Button Actions
        if (selectedStepId != null && engine != null) {
            GuideStepState state = engine.getStepState(context, selectedStepId);

            // Mark Done / Undo button
            if (layout.markDoneBtnRect().contains(mouseX, mouseY)) {
                if (state == GuideStepState.COMPLETED_MANUAL) {
                    engine.undoStepCompletion(context, selectedStepId);
                    return true;
                } else if (state == GuideStepState.AVAILABLE || state == GuideStepState.ACTIVE) {
                    engine.markStepCompleted(context, selectedStepId, true);
                    return true;
                }
            }

            // Reset button
            if (layout.resetBtnRect().contains(mouseX, mouseY)) {
                if (isResetConfirmActive()) {
                    engine.resetGuideProgress(context);
                    confirmingReset = false;
                    resetConfirmExpiry = 0;
                } else {
                    confirmingReset = true;
                    resetConfirmExpiry = System.currentTimeMillis() + 4000;
                }
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout != null && layout.leftNavRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            GuideEngine engine = session.getGuideEngine();
            GuideDefinition guide = engine != null ? engine.getActiveGuide() : null;
            int maxScroll = calculateMaxScroll(layout.leftNavRect(), engine, guide);
            this.scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (scrollY * 14), maxScroll));
            return true;
        }
        return false;
    }
}
