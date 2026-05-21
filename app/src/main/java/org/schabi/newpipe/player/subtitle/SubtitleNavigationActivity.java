package org.schabi.newpipe.player.subtitle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;

import java.util.List;

/**
 * Activity for displaying and navigating subtitle list.
 * Supports two view modes:
 * - List View: Traditional RecyclerView with separate items
 * - Article View: Continuous text with clickable paragraphs
 */
public class SubtitleNavigationActivity extends AppCompatActivity {
    private static final String TAG = "SubtitleNavigation";

    public static final String EXTRA_SUBTITLE_CONTENT = "subtitle_content";
    public static final String EXTRA_SUBTITLE_LANGUAGE = "subtitle_language";
    public static final String EXTRA_CURRENT_POSITION = "current_position";
    public static final String EXTRA_INITIAL_VIEW_MODE = "initial_view_mode";

    // View modes (public for external access)
    public static final int VIEW_MODE_LIST = 0;
    public static final int VIEW_MODE_ARTICLE = 1;

    // List View components
    private RecyclerView recyclerView;
    private SubtitleAdapter adapter;

    // Article View components
    private ScrollView articleScrollView;
    private TextView articleTextView;
    private SubtitleArticleHelper articleHelper;
    private SpannableString articleSpannable;

    // Common UI components
    private ToggleButton syncToggle;
    private ToggleButton viewToggle;
    private boolean isAutoSync = false;
    private int currentViewMode = VIEW_MODE_LIST;

    private List<SubtitleParser.SubtitleItem> subtitleItems;
    private Handler handler;
    private Runnable positionRequester;

    // Current playback position from player
    private long currentPosition = 0;

    // Broadcast receiver for player position updates
    private BroadcastReceiver positionReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subtitle_navigation);

        handler = new Handler(Looper.getMainLooper());

        // Read initial view mode from Intent extras
        int initialViewMode = getIntent().getIntExtra(EXTRA_INITIAL_VIEW_MODE, VIEW_MODE_LIST);
        if (initialViewMode == VIEW_MODE_ARTICLE) {
            currentViewMode = VIEW_MODE_ARTICLE;
        }

        initViews();
        setupPositionReceiver();
        loadSubtitles();

        // Apply initial view mode after subtitles are loaded
        if (initialViewMode == VIEW_MODE_ARTICLE) {
            switchToArticleView();
            if (viewToggle != null) {
                viewToggle.setChecked(true);
            }
        }

        startPositionUpdates();
    }

    private void initViews() {
        // List View components
        recyclerView = findViewById(R.id.subtitle_recycler_view);
        
        // Article View components (hidden initially)
        articleScrollView = findViewById(R.id.article_scroll_view);
        articleTextView = findViewById(R.id.article_text_view);
        
        // Common UI components
        syncToggle = findViewById(R.id.btn_sync_toggle);
        viewToggle = findViewById(R.id.btn_view_toggle);

        // Setup RecyclerView (List View)
        adapter = new SubtitleAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Click listener for subtitles in List View
        adapter.setOnSubtitleClickListener(new SubtitleAdapter.OnSubtitleClickListener() {
            @Override
            public void onSubtitleClick(SubtitleParser.SubtitleItem item, int position) {
                if (isAutoSync) {
                    isAutoSync = false;
                    syncToggle.setChecked(false);
                }
                seekToSubtitle(item);
            }
        });

        // Close button
        ImageButton closeBtn = findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Sync toggle
        syncToggle.setChecked(isAutoSync);
        syncToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAutoSync = isChecked;
                if (isChecked) {
                    requestPlayerPosition();
                }
            }
        });

        // View mode toggle (List <-> Article)
        if (viewToggle != null) {
            viewToggle.setChecked(false); // Default to List View (textOff = "Article")
            viewToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        switchToArticleView();
                    } else {
                        switchToListView();
                    }
                }
            });
        }

        // Initially hide Article View components
        showListView();
    }

    private void showListView() {
        currentViewMode = VIEW_MODE_LIST;
        recyclerView.setVisibility(View.VISIBLE);
        if (articleScrollView != null) {
            articleScrollView.setVisibility(View.GONE);
        }
    }

    private void showArticleView() {
        currentViewMode = VIEW_MODE_ARTICLE;
        recyclerView.setVisibility(View.GONE);
        if (articleScrollView != null) {
            articleScrollView.setVisibility(View.VISIBLE);
        }
    }

    private void switchToListView() {
        showListView();
        updateSubtitleHighlight();
    }

    private void switchToArticleView() {
        // Safety check: ensure Article View components are available
        if (articleTextView == null || articleScrollView == null) {
            Log.w(TAG, "switchToArticleView: Article View components not available, falling back to List View");
            showListView();
            return;
        }
        
        showArticleView();
        
        if (articleHelper == null && subtitleItems != null && !subtitleItems.isEmpty()) {
            articleHelper = new SubtitleArticleHelper(subtitleItems);
            articleHelper.setOnSubtitleClickListener(new SubtitleArticleHelper.OnSubtitleClickListener() {
                @Override
                public void onSubtitleClick(SubtitleParser.SubtitleItem item, int index) {
                    if (isAutoSync) {
                        isAutoSync = false;
                        syncToggle.setChecked(false);
                    }
                    seekToSubtitle(item);
                }
            });
            
            articleSpannable = articleHelper.buildArticleText();
            articleTextView.setText(articleSpannable);
            articleTextView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
        
        if (articleSpannable != null && articleTextView != null) {
            int activeIdx = SubtitleParser.findSubtitleIndex(subtitleItems, currentPosition);
            articleSpannable = articleHelper.updateHighlight(articleSpannable, activeIdx);
            articleTextView.setText(articleSpannable);
            
            // Scroll to active subtitle
            scrollToActiveSubtitle(activeIdx);
        }
    }

    private void scrollToActiveSubtitle(final int index) {
        if (index < 0 || articleHelper == null || articleTextView == null) return;
        
        final int charOffset = articleHelper.getCharOffset(index);
        articleTextView.post(new Runnable() {
            @Override
            public void run() {
                Layout layout = articleTextView.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(charOffset);
                    int y = layout.getLineTop(line);
                    articleScrollView.smoothScrollTo(0, y);
                }
            }
        });
    }

    private void setupPositionReceiver() {
        positionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && VideoDetailFragment.ACTION_PLAYER_POSITION_RESPONSE.equals(intent.getAction())) {
                    long position = intent.getLongExtra(VideoDetailFragment.EXTRA_PLAYER_POSITION, 0);
                    currentPosition = position;
                    updateSubtitleHighlight();
                }
            }
        };
    }

    private void loadSubtitles() {
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

        if (subtitleItems.isEmpty()) {
            Toast.makeText(this, "Failed to parse subtitles", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loaded " + subtitleItems.size() + " subtitle items for language: " + language);

        // Highlight current position
        updateSubtitleHighlight();
    }

    private void startPositionUpdates() {
        // Request position every 500ms
        positionRequester = new Runnable() {
            @Override
            public void run() {
                requestPlayerPosition();
                handler.postDelayed(this, 500); // Update every 500ms
            }
        };
        handler.post(positionRequester);
    }

    private void requestPlayerPosition() {
        Intent intent = new Intent(VideoDetailFragment.ACTION_REQUEST_PLAYER_POSITION);
        sendBroadcast(intent);
    }

    private void updateSubtitleHighlight() {
        if (subtitleItems != null && !subtitleItems.isEmpty()) {
            int index = SubtitleParser.findSubtitleIndex(subtitleItems, currentPosition);
            
            if (currentViewMode == VIEW_MODE_LIST) {
                // Update List View highlight
                if (index >= 0) {
                    adapter.setActiveIndex(index);
                    if (isAutoSync) {
                        recyclerView.scrollToPosition(index);
                    }
                }
            } else if (currentViewMode == VIEW_MODE_ARTICLE) {
                // Update Article View highlight
                if (articleHelper != null && articleSpannable != null && articleTextView != null) {
                    articleSpannable = articleHelper.updateHighlight(articleSpannable, index);
                    articleTextView.setText(articleSpannable);
                    
                    if (isAutoSync) {
                        scrollToActiveSubtitle(index);
                    }
                }
            }
        }
    }

    private void seekToSubtitle(SubtitleParser.SubtitleItem item) {
        Log.d(TAG, "Seeking to: " + item.startTimeMs + "ms - " + item.text);

        // Send broadcast to seek
        Intent intent = new Intent(VideoDetailFragment.ACTION_SEEK_TO);
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
        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(VideoDetailFragment.ACTION_PLAYER_POSITION_RESPONSE);
        registerReceiver(positionReceiver, filter);

        // Start position updates
        if (handler != null && positionRequester != null) {
            handler.post(positionRequester);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister broadcast receiver
        if (positionReceiver != null) {
            unregisterReceiver(positionReceiver);
        }

        // Stop position updates
        if (handler != null && positionRequester != null) {
            handler.removeCallbacks(positionRequester);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && positionRequester != null) {
            handler.removeCallbacks(positionRequester);
        }
    }
}
