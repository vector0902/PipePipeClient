package org.schabi.newpipe.player.subtitle;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.BackgroundColorSpan;
import android.view.View;

import java.util.List;

/**
 * Helper class for building article-style subtitle view.
 * Joins all subtitles into continuous text with clickable paragraphs.
 */
public class SubtitleArticleHelper {

    public interface OnSubtitleClickListener {
        void onSubtitleClick(SubtitleParser.SubtitleItem item, int index);
    }

    private final List<SubtitleParser.SubtitleItem> subtitleItems;
    private OnSubtitleClickListener clickListener;
    private int activeIndex = -1;

    // Track span ranges for each subtitle item [start, end]
    private int[] spanStarts;
    private int[] spanEnds;

    public SubtitleArticleHelper(List<SubtitleParser.SubtitleItem> items) {
        this.subtitleItems = items;
        if (items != null && !items.isEmpty()) {
            spanStarts = new int[items.size()];
            spanEnds = new int[items.size()];
        }
    }

    public void setOnSubtitleClickListener(OnSubtitleClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Build SpannableString with all subtitles joined together.
     * Each paragraph is clickable and can be highlighted.
     */
    public SpannableString buildArticleText() {
        if (subtitleItems == null || subtitleItems.isEmpty()) {
            return new SpannableString("");
        }

        StringBuilder builder = new StringBuilder();
        
        // Build text content first to calculate positions
        for (int i = 0; i < subtitleItems.size(); i++) {
            SubtitleParser.SubtitleItem item = subtitleItems.get(i);
            
            // Record start position for this subtitle
            spanStarts[i] = builder.length();
            
            // Add subtitle text
            builder.append(item.text);
            
            // Record end position (exclusive)
            spanEnds[i] = builder.length();
            
            // Add separator between subtitles (double newline for paragraph break)
            if (i < subtitleItems.size() - 1) {
                builder.append("\n\n");
            }
        }

        SpannableString spannable = new SpannableString(builder.toString());

        // Apply ClickableSpan to each subtitle paragraph
        for (int i = 0; i < subtitleItems.size(); i++) {
            final int index = i;
            final SubtitleParser.SubtitleItem item = subtitleItems.get(i);
            
            ClickableSpan clickSpan = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    if (clickListener != null) {
                        clickListener.onSubtitleClick(item, index);
                    }
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(0xFFFFFFFF); // White text
                    ds.setUnderlineText(false); // Remove underline
                }
            };
            
            spannable.setSpan(clickSpan, spanStarts[i], spanEnds[i], 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    /**
     * Update highlight on the active subtitle.
     * Returns updated SpannableString with background color on active paragraph.
     */
    public SpannableString updateHighlight(SpannableString spannable, int newActiveIndex) {
        if (spannable == null || subtitleItems == null || subtitleItems.isEmpty()) {
            return spannable;
        }

        // Remove old highlight
        if (activeIndex >= 0 && activeIndex < subtitleItems.size()) {
            BackgroundColorSpan[] oldSpans = spannable.getSpans(
                    spanStarts[activeIndex], spanEnds[activeIndex], BackgroundColorSpan.class);
            for (BackgroundColorSpan span : oldSpans) {
                spannable.removeSpan(span);
            }
        }

        // Apply new highlight
        activeIndex = newActiveIndex;
        if (activeIndex >= 0 && activeIndex < subtitleItems.size()) {
            BackgroundColorSpan highlightSpan = new BackgroundColorSpan(0x40FF9800); // Semi-transparent orange
            spannable.setSpan(highlightSpan, spanStarts[activeIndex], spanEnds[activeIndex],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    /**
     * Get character offset for a subtitle index.
     * Useful for scrolling to specific subtitle.
     */
    public int getCharOffset(int subtitleIndex) {
        if (subtitleIndex >= 0 && subtitleIndex < spanStarts.length) {
            return spanStarts[subtitleIndex];
        }
        return 0;
    }

    /**
     * Find subtitle index from character offset.
     */
    public int findSubtitleIndexFromOffset(int charOffset) {
        for (int i = 0; i < spanStarts.length; i++) {
            if (charOffset >= spanStarts[i] && charOffset < spanEnds[i]) {
                return i;
            }
        }
        return -1;
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public List<SubtitleParser.SubtitleItem> getSubtitleItems() {
        return subtitleItems;
    }
}
