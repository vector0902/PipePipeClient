package org.schabi.newpipe.player.subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for SRT subtitle format.
 * Converts SRT content into a list of SubtitleItem objects.
 */
public class SubtitleParser {

    /**
     * Represents a single subtitle entry.
     */
    public static class SubtitleItem {
        public final int index;
        public final long startTimeMs;
        public final long endTimeMs;
        public final String text;

        public SubtitleItem(int index, long startTimeMs, long endTimeMs, String text) {
            this.index = index;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = endTimeMs;
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s -> %s: %s",
                    index,
                    formatTime(startTimeMs),
                    formatTime(endTimeMs),
                    text);
        }

        private static String formatTime(long ms) {
            long seconds = ms / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            return String.format("%02d:%02d:%02d,%03d",
                    hours % 24, minutes % 60, seconds % 60, ms % 1000);
        }
    }

    // Pattern to match SRT timestamp: 00:00:00,000 --> 00:00:00,000
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
    );

    /**
     * Parse SRT content into a list of SubtitleItem.
     *
     * @param srtContent The SRT format content
     * @return List of parsed SubtitleItem objects
     */
    public static List<SubtitleItem> parse(String srtContent) {
        List<SubtitleItem> subtitles = new ArrayList<>();

        if (srtContent == null || srtContent.trim().isEmpty()) {
            return subtitles;
        }

        // Split by double newlines (subtitle blocks)
        String[] blocks = srtContent.trim().split("\\n\\s*\\n");

        for (String block : blocks) {
            String[] lines = block.split("\\n");
            if (lines.length < 2) continue;

            // Try to parse timestamp from first line (index line) or second line
            String timestampLine = null;
            int textStartIndex = 2;

            for (int i = 0; i < lines.length; i++) {
                if (TIMESTAMP_PATTERN.matcher(lines[i]).find()) {
                    timestampLine = lines[i];
                    textStartIndex = i + 1;
                    break;
                }
            }

            if (timestampLine == null) {
                // Try to find index line followed by timestamp
                for (int i = 0; i < lines.length - 1; i++) {
                    if (lines[i].trim().matches("\\d+")) {
                        String nextLine = lines[i + 1].trim();
                        if (TIMESTAMP_PATTERN.matcher(nextLine).find()) {
                            timestampLine = nextLine;
                            textStartIndex = i + 2;
                            break;
                        }
                    }
                }
            }

            if (timestampLine == null) continue;

            // Parse timestamp
            Matcher matcher = TIMESTAMP_PATTERN.matcher(timestampLine);
            if (!matcher.find()) continue;

            long startTimeMs = parseTimestamp(matcher.group(1), matcher.group(2),
                    matcher.group(3), matcher.group(4));
            long endTimeMs = parseTimestamp(matcher.group(5), matcher.group(6),
                    matcher.group(7), matcher.group(8));

            // Parse text (remaining lines)
            StringBuilder textBuilder = new StringBuilder();
            for (int i = textStartIndex; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(line);
                }
            }

            // Try to parse index from first line
            int index = 0;
            try {
                String firstLine = lines[0].trim();
                if (firstLine.matches("\\d+")) {
                    index = Integer.parseInt(firstLine) - 1; // 0-based
                }
            } catch (Exception ignored) {
            }

            subtitles.add(new SubtitleItem(index, startTimeMs, endTimeMs, textBuilder.toString()));
        }

        return subtitles;
    }

    /**
     * Parse timestamp components to milliseconds.
     */
    private static long parseTimestamp(String hour, String minute, String second, String millis) {
        return (Long.parseLong(hour) * 3600
                + Long.parseLong(minute) * 60
                + Long.parseLong(second)) * 1000
                + Long.parseLong(millis);
    }

    /**
     * Find the subtitle index for a given playback position.
     * Uses binary search for O(log n) performance.
     *
     * @param subtitles List of subtitles
     * @param positionMs Current playback position in milliseconds
     * @return Index of the subtitle that should be shown, or -1 if not found
     */
    public static int findSubtitleIndex(List<SubtitleItem> subtitles, long positionMs) {
        if (subtitles == null || subtitles.isEmpty()) {
            return -1;
        }

        int left = 0;
        int right = subtitles.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            SubtitleItem midItem = subtitles.get(mid);

            if (positionMs >= midItem.startTimeMs && positionMs <= midItem.endTimeMs) {
                return mid;
            } else if (positionMs < midItem.startTimeMs) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        // If not found, return the last subtitle before the position
        if (left > 0 && left <= subtitles.size()) {
            return left - 1;
        }

        return -1;
    }
}
