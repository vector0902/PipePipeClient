package org.schabi.newpipe.player.subtitle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying subtitle items in a RecyclerView.
 */
public class SubtitleAdapter extends RecyclerView.Adapter<SubtitleAdapter.SubtitleViewHolder> {

    private final List<SubtitleParser.SubtitleItem> items = new ArrayList<>();
    private int activeIndex = -1;
    private OnSubtitleClickListener clickListener;

    public interface OnSubtitleClickListener {
        void onSubtitleClick(SubtitleParser.SubtitleItem item, int position);
    }

    public void setOnSubtitleClickListener(OnSubtitleClickListener listener) {
        this.clickListener = listener;
    }

    public void setItems(List<SubtitleParser.SubtitleItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setActiveIndex(int index) {
        // Don't update if index hasn't changed
        if (index == activeIndex) {
            return;
        }
        int oldIndex = activeIndex;
        activeIndex = index;
        if (oldIndex >= 0 && oldIndex < items.size()) {
            notifyItemChanged(oldIndex);
        }
        if (index >= 0 && index < items.size()) {
            notifyItemChanged(index);
        }
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    @NonNull
    @Override
    public SubtitleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subtitle, parent, false);
        return new SubtitleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubtitleViewHolder holder, int position) {
        SubtitleParser.SubtitleItem item = items.get(position);
        holder.bind(item, position == activeIndex);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSubtitleClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SubtitleViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTime;
        private final TextView textIndex;
        private final TextView textContent;

        SubtitleViewHolder(@NonNull View itemView) {
            super(itemView);
            textTime = itemView.findViewById(R.id.text_time);
            textIndex = itemView.findViewById(R.id.text_index);
            textContent = itemView.findViewById(R.id.text_content);
        }

        void bind(SubtitleParser.SubtitleItem item, boolean isActive) {
            textTime.setText(formatTime(item.startTimeMs));
            textIndex.setText("#" + (item.index + 1));
            textContent.setText(item.text);

            // Highlight active subtitle
            if (isActive) {
                itemView.setBackgroundColor(0x40FF9800); // Semi-transparent orange
                textTime.setTextColor(0xFFFF9800); // Orange
                textContent.setTextColor(0xFFFFFFFF); // White
            } else {
                itemView.setBackgroundColor(0x00000000); // Transparent
                textTime.setTextColor(0xFF888888); // Gray
                textContent.setTextColor(0xFFFFFFFF); // White
            }
        }

        private String formatTime(long ms) {
            long seconds = ms / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            return String.format("%02d:%02d:%02d",
                    hours % 24, minutes % 60, seconds % 60);
        }
    }
}
