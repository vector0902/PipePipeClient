package org.schabi.newpipe.player.subtitle;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

import java.util.List;

/**
 * Activity for displaying and navigating subtitle list.
 * Allows users to browse all subtitles and click to seek to specific positions.
 */
public class SubtitleNavigationActivity extends AppCompatActivity {
    private static final String TAG = "SubtitleNavigation";

    public static final String EXTRA_SUBTITLE_CONTENT = "subtitle_content";
    public static final String EXTRA_SUBTITLE_LANGUAGE = "subtitle_language";
    public static final String EXTRA_CURRENT_POSITION = "current_position";

    private RecyclerView recyclerView;
    private SubtitleAdapter adapter;
    private ProgressBar progressBar;
    private ToggleButton syncToggle;
    private boolean isAutoSync = false;

    private List<SubtitleParser.SubtitleItem> subtitleItems;
    private Handler handler;
    private Runnable positionUpdater;

    // Current playback position from intent
    private long currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subtitle_navigation);

        handler = new Handler(Looper.getMainLooper());

        initViews();
        loadSubtitles();
        startPositionUpdater();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.subtitle_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        syncToggle = findViewById(R.id.btn_sync_toggle);

        // Setup RecyclerView
        adapter = new SubtitleAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Click listener for subtitles
        adapter.setOnSubtitleClickListener((item, position) -> {
            if (isAutoSync) {
                // Disable auto sync when user manually clicks
                isAutoSync = false;
                syncToggle.setChecked(false);
            }
            seekToSubtitle(item);
        });

        // Close button
        ImageButton closeBtn = findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(v -> finish());

        // Sync toggle
        syncToggle.setChecked(isAutoSync);
        syncToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAutoSync = isChecked;
            if (isChecked) {
                // Re-sync to current position
                updateCurrentPosition();
            }
        });
    }

    private void loadSubtitles() {
        progressBar.setVisibility(View.VISIBLE);

        String subtitleContent = getIntent().getStringExtra(EXTRA_SUBTITLE_CONTENT);
        String language = getIntent().getStringExtra(EXTRA_SUBTITLE_LANGUAGE);
        currentPosition = getIntent().getLongExtra(EXTRA_CURRENT_POSITION, 0);

        if (subtitleContent == null || subtitleContent.isEmpty()) {
            Toast.makeText(this, "No subtitle content available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Parse subtitles
        subtitleItems = SubtitleParser.parse(subtitleContent);
        adapter.setItems(subtitleItems);

        progressBar.setVisibility(View.GONE);

        if (subtitleItems.isEmpty()) {
            Toast.makeText(this, "Failed to parse subtitles", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loaded " + subtitleItems.size() + " subtitle items for language: " + language);

        // Scroll to current position
        updateCurrentPosition();
    }

    private void startPositionUpdater() {
        positionUpdater = new Runnable() {
            @Override
            public void run() {
                if (isAutoSync && !subtitleItems.isEmpty()) {
                    // In a real implementation, we would get the current position from the player
                    // For now, we rely on the position passed via intent or manual updates
                    updateCurrentPosition();
                }
                handler.postDelayed(this, 1000); // Update every second
            }
        };
        handler.post(positionUpdater);
    }

    private void updateCurrentPosition() {
        if (!subtitleItems.isEmpty()) {
            int index = SubtitleParser.findSubtitleIndex(subtitleItems, currentPosition);
            if (index >= 0) {
                adapter.setActiveIndex(index);
                recyclerView.scrollToPosition(index);
            }
        }
    }

    private void seekToSubtitle(SubtitleParser.SubtitleItem item) {
        Log.d(TAG, "Seeking to: " + item.startTimeMs + "ms - " + item.text);

        // Send broadcast to seek
        android.content.Intent intent = new android.content.Intent(
            org.schabi.newpipe.fragments.detail.VideoDetailFragment.ACTION_SEEK_TO);
        intent.putExtra("Timestamp", (int)(item.startTimeMs / 1000)); // Convert ms to seconds
        sendBroadcast(intent);

        Toast.makeText(this, "Jumped to " + formatTime(item.startTimeMs),
                Toast.LENGTH_SHORT).show();
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%02d:%02d:%02d",
                hours % 24, minutes % 60, seconds % 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (handler != null && positionUpdater != null) {
            handler.post(positionUpdater);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && positionUpdater != null) {
            handler.removeCallbacks(positionUpdater);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && positionUpdater != null) {
            handler.removeCallbacks(positionUpdater);
        }
    }
}
