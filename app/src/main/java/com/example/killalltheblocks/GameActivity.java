package com.example.killalltheblocks;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameActivity extends Activity {
    private static final String PIECE_DRAG_LABEL = "piece";
    private static final String[] PRAISE_TEXT = {
            "Great!", "Awesome!", "Excellent!", "Amazing!", "Brilliant!", "Combo!"
    };
    private static final float[] STAR_X = {
            0.06f, 0.12f, 0.18f, 0.27f, 0.34f, 0.43f, 0.51f, 0.60f, 0.67f, 0.74f,
            0.82f, 0.90f, 0.96f, 0.14f, 0.24f, 0.37f, 0.49f, 0.58f, 0.72f, 0.86f,
            0.31f, 0.79f, 0.04f, 0.93f
    };
    private static final float[] STAR_Y = {
            0.08f, 0.23f, 0.61f, 0.14f, 0.80f, 0.35f, 0.09f, 0.67f, 0.21f, 0.48f,
            0.13f, 0.74f, 0.39f, 0.91f, 0.44f, 0.56f, 0.84f, 0.29f, 0.93f, 0.58f,
            0.04f, 0.32f, 0.72f, 0.18f
    };
    private static final float DRAG_SHADOW_TOUCH_X = 0.5f;
    private static final float DRAG_SHADOW_TOUCH_Y = 1.15f;
    private static final float DRAG_BOARD_SPEED = 1.12f;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final GameEngine engine = new GameEngine();
    private final Random praiseRandom = new Random();
    private ScoreStore scoreStore;
    private GameStateStore gameStateStore;
    private GameSettingsStore settingsStore;
    private ToneGenerator toneGenerator;
    private BoardView boardView;
    private PieceView[] pieceViews;
    private RollingScoreView scoreView;
    private TextView highScoreView;
    private TextView timerView;
    private TextView statusView;
    private int draggingSlot = GameEngine.NO_SELECTION;
    private boolean settingsOpenedFromGame;
    private long gameStartedAt;
    private long activeElapsedMillis;
    private long activeResumedAtMillis;
    private boolean gameClockRunning;
    private boolean gameEnded;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scoreStore = new ScoreStore(this);
        gameStateStore = new GameStateStore(this);
        settingsStore = new GameSettingsStore(this);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        showTitleScreen();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        saveCurrentGame();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        pauseGameClock();
        saveCurrentGame();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (boardView != null && !gameEnded) {
            resumeGameClock();
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        }
    }

    private void showTitleScreen() {
        timerHandler.removeCallbacks(timerRunnable);
        stopMusic();
        PatternLayout root = baseRoot();
        root.setGravity(Gravity.CENTER);

        TitleLogoView logo = new TitleLogoView(this);
        root.addView(logo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));

        TextView subtitle = new TextView(this);
        subtitle.setText("Drag blocks. Clear rows and columns. Chase the gold score.");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(16), 0, dp(28));
        root.addView(subtitle, fullWrapParams());

        Button start = menuButton("Start New Game");
        start.setOnClickListener(v -> startNewGame());
        root.addView(start, buttonParams());

        if (gameStateStore.load() != null) {
            Button resume = menuButton("Resume Game");
            resume.setOnClickListener(v -> resumeSavedGame());
            root.addView(resume, buttonParams());
        }

        Button scores = menuButton("View High Scores");
        scores.setOnClickListener(v -> showHighScoresScreen());
        root.addView(scores, buttonParams());

        Button settings = menuButton("Settings");
        settings.setOnClickListener(v -> showSettingsScreen(false));
        root.addView(settings, buttonParams());

        TextView version = new TextView(this);
        version.setText("Version " + getVersionName());
        version.setTextColor(Color.argb(190, 255, 255, 255));
        version.setTextSize(12);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(18), 0, 0);
        root.addView(version, fullWrapParams());

        setContentView(root);
    }

    private void showHighScoresScreen() {
        timerHandler.removeCallbacks(timerRunnable);
        stopMusic();
        PatternLayout root = baseRoot();

        TextView heading = screenHeading("High Scores");
        root.addView(heading, fullWrapParams());

        ScrollView scrollView = new ScrollView(this);
        LinearLayout scores = new LinearLayout(this);
        scores.setOrientation(LinearLayout.VERTICAL);
        scores.setPadding(0, dp(18), 0, dp(18));
        addScoreRows(scores, true);
        scrollView.addView(scores);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button back = menuButton("Back");
        back.setOnClickListener(v -> showTitleScreen());
        root.addView(back, buttonParams());
        setContentView(root);
    }

    private void showSettingsScreen(boolean fromGame) {
        settingsOpenedFromGame = fromGame;
        timerHandler.removeCallbacks(timerRunnable);
        if (!fromGame) {
            stopMusic();
        } else {
            pauseGameClock();
            saveCurrentGame();
        }
        PatternLayout root = baseRoot();

        TextView heading = screenHeading("Settings");
        root.addView(heading, fullWrapParams());

        TextView nameLabel = settingLabel("Player Name");
        root.addView(nameLabel, fullWrapParams());
        EditText playerName = new EditText(this);
        playerName.setSingleLine(true);
        playerName.setText(settingsStore.getPlayerName());
        playerName.setTextColor(Color.WHITE);
        playerName.setTextSize(18);
        playerName.setSelectAllOnFocus(false);
        playerName.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                settingsStore.setPlayerName(playerName.getText().toString());
            }
        });
        root.addView(playerName, fullWrapParams());

        TextView volumeLabel = settingLabel("Volume: " + settingsStore.getVolumePercent() + "%");
        root.addView(volumeLabel, fullWrapParams());
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(settingsStore.getVolumePercent());
        volume.setPadding(0, dp(8), 0, dp(18));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settingsStore.setVolumePercent(progress);
                volumeLabel.setText("Volume: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                playTone(ToneGenerator.TONE_PROP_BEEP);
            }
        });
        root.addView(volume, fullWrapParams());

        CheckBox haptics = new CheckBox(this);
        haptics.setText("Haptics");
        haptics.setTextColor(Color.WHITE);
        haptics.setTextSize(18);
        haptics.setChecked(settingsStore.isHapticsEnabled());
        haptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsStore.setHapticsEnabled(isChecked);
            if (isChecked) {
                buttonView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        });
        root.addView(haptics, fullWrapParams());

        Button clearScores = menuButton("Clear High Scores");
        clearScores.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear high scores?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> scoreStore.clearScores())
                .setNegativeButton("Cancel", null)
                .show());
        root.addView(clearScores, buttonParams());

        Button back = menuButton("Back");
        back.setOnClickListener(v -> {
            settingsStore.setPlayerName(playerName.getText().toString());
            if (settingsOpenedFromGame) {
                setContentView(createGameView());
                resumeGameClock();
                timerHandler.removeCallbacks(timerRunnable);
                timerHandler.post(timerRunnable);
                refreshAll(false);
            } else {
                showTitleScreen();
            }
        });
        root.addView(back, buttonParams());
        setContentView(root);
    }

    private View createGameView() {
        PatternLayout root = baseRoot();

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = iconButton("\u2039");
        back.setOnClickListener(v -> showTitleScreen());
        TextView cog = iconButton("\u2699");
        cog.setOnClickListener(v -> showSettingsScreen(true));
        topBar.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));
        topBar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(48), 1f));
        topBar.addView(cog, new LinearLayout.LayoutParams(dp(56), dp(48)));
        root.addView(topBar, fullWrapParams());

        highScoreView = new TextView(this);
        highScoreView.setTextColor(Color.rgb(255, 232, 143));
        highScoreView.setTextSize(15);
        highScoreView.setGravity(Gravity.CENTER);
        highScoreView.setShadowLayer(dp(3), 0, dp(1), Color.rgb(60, 34, 0));
        root.addView(highScoreView, fullWrapParams());

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, dp(4), 0, dp(10));
        scoreView = new RollingScoreView(this);
        timerView = statText();
        stats.addView(scoreView, new LinearLayout.LayoutParams(0, dp(58), 1.25f));
        stats.addView(timerView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f));
        root.addView(stats);

        boardView = new BoardView(this);
        root.addView(boardView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));

        LinearLayout tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.HORIZONTAL);
        tray.setGravity(Gravity.CENTER);
        tray.setPadding(0, dp(44), 0, 0);
        pieceViews = new PieceView[GameEngine.PIECE_SLOTS];
        for (int i = 0; i < pieceViews.length; i++) {
            PieceView pieceView = new PieceView(this, i);
            pieceViews[i] = pieceView;
            LinearLayout.LayoutParams pieceParams = new LinearLayout.LayoutParams(0, dp(120), 1f);
            pieceParams.setMargins(dp(2), 0, dp(2), 0);
            tray.addView(pieceView, pieceParams);
        }
        root.addView(tray, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(10), 0, 0);
        root.addView(statusView);

        return root;
    }

    private PatternLayout baseRoot() {
        PatternLayout root = new PatternLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(42), dp(16), dp(16));
        return root;
    }

    private TextView screenHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(Color.rgb(255, 244, 176));
        heading.setTextSize(30);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setShadowLayer(dp(6), 0, dp(2), Color.rgb(96, 53, 0));
        return heading;
    }

    private Button menuButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setShadowLayer(dp(2), 0, dp(1), Color.rgb(30, 30, 30));
        button.setMinHeight(dp(54));
        button.setBackground(roundedButtonDrawable(text));
        return button;
    }

    private TextView settingLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(18);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(12), 0, 0);
        return label;
    }

    private TextView iconButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(36);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setShadowLayer(dp(4), 0, dp(1), Color.BLACK);
        return view;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams fullWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView statText() {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(18);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams weightedWrapParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void startNewGame() {
        engine.reset();
        gameStartedAt = System.currentTimeMillis();
        activeElapsedMillis = 0L;
        gameEnded = false;
        setContentView(createGameView());
        saveCurrentGame();
        timerHandler.removeCallbacks(timerRunnable);
        resumeGameClock();
        timerHandler.post(timerRunnable);
        refreshAll(false);
    }

    private void resumeSavedGame() {
        GameStateStore.SavedGame savedGame = gameStateStore.load();
        if (savedGame == null) {
            startNewGame();
            return;
        }
        engine.restoreState(savedGame.encodedBoard, savedGame.encodedBoardColors,
                savedGame.pieceNames, savedGame.score, savedGame.selectedSlot, savedGame.comboStreak);
        gameStartedAt = savedGame.startedAtMillis;
        activeElapsedMillis = savedGame.elapsedMillis;
        gameEnded = savedGame.gameEnded;
        engine.setFinishedDurationMillis(savedGame.finishedDurationMillis);
        setContentView(createGameView());
        timerHandler.removeCallbacks(timerRunnable);
        if (!gameEnded) {
            resumeGameClock();
        }
        timerHandler.post(timerRunnable);
        refreshAll(false);
    }

    private void refreshAll(boolean animateScore) {
        int highScore = getHighScore();
        highScoreView.setText("High Score: " + highScore);
        if (animateScore) {
            scoreView.animateTo(engine.getScore());
        } else {
            scoreView.setScore(engine.getScore());
        }
        updateTimer();
        statusView.setText(gameEnded
                ? "Game over. Start a new game to try for a higher score."
                : "Drag a block onto the grid. Your finger tracks the block's center.");
        boardView.invalidate();
        for (PieceView pieceView : pieceViews) {
            pieceView.invalidate();
        }
    }

    private int getHighScore() {
        List<ScoreEntry> entries = scoreStore.loadTopScores();
        return entries.isEmpty() ? 0 : entries.get(0).getScore();
    }

    private void updateTimer() {
        if (timerView == null) {
            return;
        }
        long duration = gameEnded ? engine.getFinishedDurationMillis() : currentElapsedMillis();
        timerView.setText("Time: " + formatDuration(duration));
    }

    private void addScoreRows(LinearLayout parent, boolean largeRows) {
        parent.removeAllViews();
        List<ScoreEntry> entries = scoreStore.loadTopScores();
        if (entries.isEmpty()) {
            TextView empty = leaderText("No completed games yet.", largeRows);
            parent.addView(empty);
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry entry = entries.get(i);
            String text = String.format(Locale.getDefault(), "%d. %s - %d pts - %s - %s",
                    i + 1,
                    entry.getPlayerName(),
                    entry.getScore(),
                    formatter.format(new Date(entry.getFinishedAtMillis())),
                    formatDuration(entry.getDurationMillis()));
            parent.addView(leaderText(text, largeRows));
        }
    }

    private TextView leaderText(String text, boolean largeRows) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(largeRows ? 17 : 14);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private void onPieceDropped(int slot, int row, int col) {
        if (gameEnded) {
            return;
        }
        if (!engine.placePiece(slot, row, col)) {
            statusView.setText("That block does not fit there. Try another spot.");
            boardView.clearPreview();
            draggingSlot = GameEngine.NO_SELECTION;
            refreshPieceViews();
            return;
        }
        int clearedLines = engine.getLastClearedLines();
        playTone(ToneGenerator.TONE_PROP_BEEP);
        if (clearedLines > 0) {
            int multiplier = engine.getLastScoreMultiplier();
            boardView.triggerClearAnimations(clearedLines, multiplier);
            performClearHaptics(multiplier);
            playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD);
            shakeBoard();
        }
        draggingSlot = GameEngine.NO_SELECTION;
        saveCurrentGame();
        if (!engine.hasAnyMove()) {
            finishGame();
        } else {
            refreshAll(true);
        }
    }

    private void shakeBoard() {
        final float distance = dp(4);
        boardView.animate().cancel();
        boardView.animate().translationX(distance).setDuration(35).withEndAction(() ->
                boardView.animate().translationX(-distance).setDuration(35).withEndAction(() ->
                        boardView.animate().translationX(distance / 2f).setDuration(35).withEndAction(() ->
                                boardView.animate().translationX(0f).setDuration(35).start()).start()).start()).start();
    }

    private void performClearHaptics(int multiplier) {
        if (!settingsStore.isHapticsEnabled()) {
            return;
        }
        if (boardView == null) {
            return;
        }
        if (multiplier > 1) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, Math.min(255, 90 + multiplier * 35)));
                    return;
                }
            } catch (RuntimeException ignored) {
                // Fall back to view haptics if the device disallows direct vibration.
            }
            boardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else {
            boardView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void finishGame() {
        gameEnded = true;
        pauseGameClock();
        stopMusic();
        long duration = activeElapsedMillis;
        engine.setFinishedDurationMillis(duration);
        timerHandler.removeCallbacks(timerRunnable);
        scoreStore.addScore(engine.getScore(), System.currentTimeMillis(), duration, settingsStore.getPlayerName());
        gameStateStore.clear();
        refreshAll(true);
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);
        new AlertDialog.Builder(this)
                .setTitle("Game over")
                .setMessage("Score: " + engine.getScore() + "\nDuration: " + formatDuration(duration))
                .setPositiveButton("New Game", (dialog, which) -> startNewGame())
                .setNegativeButton("Menu", (dialog, which) -> showTitleScreen())
                .show();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dragHoverOffsetPx() {
        return getResources().getDisplayMetrics().heightPixels * 0.15f;
    }

    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "1.0";
        }
    }

    private void saveCurrentGame() {
        if (!gameEnded && gameStateStore != null && boardView != null) {
            gameStateStore.save(engine, gameStartedAt, currentElapsedMillis(), false);
        }
    }

    private void resumeGameClock() {
        if (!gameClockRunning && !gameEnded) {
            activeResumedAtMillis = System.currentTimeMillis();
            gameClockRunning = true;
        }
    }

    private void pauseGameClock() {
        if (gameClockRunning) {
            activeElapsedMillis += System.currentTimeMillis() - activeResumedAtMillis;
            gameClockRunning = false;
        }
    }

    private long currentElapsedMillis() {
        if (!gameClockRunning) {
            return activeElapsedMillis;
        }
        return activeElapsedMillis + (System.currentTimeMillis() - activeResumedAtMillis);
    }

    private GradientDrawable roundedButtonDrawable(String text) {
        int startColor = Color.rgb(59, 130, 246);
        int endColor = Color.rgb(29, 78, 216);
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("start") || lower.contains("resume")) {
            startColor = Color.rgb(34, 197, 94);
            endColor = Color.rgb(21, 128, 61);
        } else if (lower.contains("score")) {
            startColor = Color.rgb(245, 158, 11);
            endColor = Color.rgb(180, 83, 9);
        } else if (lower.contains("clear")) {
            startColor = Color.rgb(239, 68, 68);
            endColor = Color.rgb(153, 27, 27);
        } else if (lower.contains("settings")) {
            startColor = Color.rgb(168, 85, 247);
            endColor = Color.rgb(109, 40, 217);
        }
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(2), Color.argb(190, 255, 255, 255));
        return drawable;
    }

    private void playTone(int toneType) {
        int volume = settingsStore == null ? 70 : settingsStore.getVolumePercent();
        if (toneGenerator == null || volume <= 0) {
            return;
        }
        try {
            int duration = Math.max(35, 80 + volume);
            toneGenerator.release();
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, volume);
            toneGenerator.startTone(toneType, duration);
        } catch (RuntimeException ignored) {
            // ToneGenerator can fail on some devices if audio focus is unavailable.
        }
    }

    private void stopMusic() {
        // No bundled music asset is available in the repository; sound effects remain enabled.
    }

    private void refreshPieceViews() {
        if (pieceViews == null) {
            return;
        }
        for (PieceView pieceView : pieceViews) {
            pieceView.invalidate();
        }
    }

    private final class PatternLayout extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PatternLayout(Activity context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setShader(new LinearGradient(
                    0, 0, getWidth(), getHeight(),
                    new int[]{0xff26105f, 0xff0f766e, 0xffe11d48, 0xfff59e0b},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < STAR_X.length; i++) {
                float cx = STAR_X[i] * getWidth();
                float cy = STAR_Y[i] * getHeight();
                paint.setColor(i % 4 == 0 ? 0xfffff7ad : 0xeeffffff);
                float size = dp(i % 3 == 0 ? 3 : 2);
                canvas.drawLine(cx - size, cy, cx + size, cy, paint);
                canvas.drawLine(cx, cy - size, cx, cy + size, paint);
            }
            paint.setColor(0x33ffffff);
            for (int i = 0; i < 7; i++) {
                float startX = getWidth() * (0.08f + i * 0.13f);
                float startY = getHeight() * (0.25f + (i % 3) * 0.12f);
                canvas.drawRoundRect(
                        new RectF(startX, startY, startX + dp(70 + i * 8), startY + dp(2)),
                        dp(2), dp(2), paint);
            }
            super.onDraw(canvas);
        }
    }

    private final class RollingScoreView extends TextView {
        private int displayedScore;
        private int previousScore;
        private float rollProgress = 1f;
        private ValueAnimator animator;

        RollingScoreView(Activity context) {
            super(context);
            setTextColor(Color.rgb(255, 215, 0));
            setTextSize(34);
            setGravity(Gravity.CENTER);
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            setShadowLayer(dp(6), 0, dp(2), Color.rgb(100, 58, 0));
        }

        void setScore(int score) {
            if (animator != null) {
                animator.cancel();
            }
            previousScore = Math.max(0, score);
            displayedScore = previousScore;
            rollProgress = 1f;
            setText("");
            invalidate();
        }

        void animateTo(int targetScore) {
            final int target = Math.max(0, targetScore);
            if (target <= displayedScore) {
                setScore(target);
                return;
            }
            if (animator != null) {
                animator.cancel();
            }
            previousScore = displayedScore;
            final int startScore = previousScore;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(Math.min(900, 220 + (target - displayedScore) * 35L));
            animator.addUpdateListener(animation -> {
                rollProgress = (float) animation.getAnimatedValue();
                displayedScore = startScore + Math.round((target - startScore) * rollProgress);
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint paint = getPaint();
            paint.setColor(getCurrentTextColor());
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setShadowLayer(dp(6), 0, dp(2), Color.rgb(100, 58, 0));

            String from = String.valueOf(previousScore);
            String to = String.valueOf(displayedScore);
            int width = Math.max(from.length(), to.length());
            from = leftPad(from, width);
            to = leftPad(to, width);

            float y = getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f;
            float x = dp(4);

            for (int i = 0; i < width; i++) {
                String oldDigit = String.valueOf(from.charAt(i));
                String newDigit = String.valueOf(to.charAt(i));
                float digitWidth = paint.measureText("8");
                float centerX = x + digitWidth / 2f;
                if (oldDigit.equals(newDigit) || rollProgress >= 1f) {
                    canvas.drawText(newDigit, centerX, y, paint);
                } else {
                    float offset = rollProgress * getHeight();
                    canvas.drawText(oldDigit, centerX, y - offset, paint);
                    canvas.drawText(newDigit, centerX, y + getHeight() - offset, paint);
                }
                x += digitWidth;
            }
        }

        private String leftPad(String value, int width) {
            StringBuilder builder = new StringBuilder();
            for (int i = value.length(); i < width; i++) {
                builder.append(' ');
            }
            builder.append(value);
            return builder.toString();
        }
    }

    private final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int previewSlot = GameEngine.NO_SELECTION;
        private int previewRow = -1;
        private int previewCol = -1;
        private float clearFlashProgress;
        private boolean[] flashingRows = new boolean[GameEngine.BOARD_SIZE];
        private boolean[] flashingCols = new boolean[GameEngine.BOARD_SIZE];
        private String praiseText;
        private float praiseProgress = 1f;
        private ValueAnimator clearFlashAnimator;
        private ValueAnimator praiseAnimator;
        private ValueAnimator dangerAnimator;

        BoardView(Activity context) {
            super(context);
            setOnDragListener((view, event) -> handleDragEvent(event));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = boardSize();
            float cell = cellSize();
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(15, 23, 42));
            canvas.drawRoundRect(new RectF(left, top, left + size, top + size), dp(12), dp(12), paint);

            int[][] boardColors = engine.copyBoardColors();
            for (int row = 0; row < GameEngine.BOARD_SIZE; row++) {
                for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
                    int color = boardColors[row][col];
                    paint.setColor(color != 0 ? color : Color.rgb(18, 26, 45));
                    float inset = dp(2);
                    canvas.drawRoundRect(
                            new RectF(left + col * cell + inset, top + row * cell + inset,
                                    left + (col + 1) * cell - inset, top + (row + 1) * cell - inset),
                            dp(5), dp(5), paint);
                    if (color != 0) {
                        paint.setColor(Color.argb(95, 255, 255, 255));
                        canvas.drawRoundRect(
                                new RectF(left + col * cell + inset, top + row * cell + inset,
                                        left + (col + 1) * cell - inset, top + row * cell + cell * 0.38f),
                                dp(5), dp(5), paint);
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1));
                    paint.setColor(Color.argb(150, 148, 163, 184));
                    canvas.drawRoundRect(
                            new RectF(left + col * cell + inset, top + row * cell + inset,
                                    left + (col + 1) * cell - inset, top + (row + 1) * cell - inset),
                            dp(5), dp(5), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            drawPreview(canvas, cell, left, top);
            drawLineFlash(canvas, cell, left, top);
            drawDangerBorder(canvas, left, top, size);
            drawPraise(canvas, left, top, size);
        }

        float cellSize() {
            return boardSize() / GameEngine.BOARD_SIZE;
        }

        private float boardSize() {
            return Math.min(getWidth(), getHeight());
        }

        private boolean handleDragEvent(DragEvent event) {
            Integer slot = getDraggedSlot(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return slot != null && !gameEnded && engine.getPiece(slot) != null;
                case DragEvent.ACTION_DRAG_LOCATION:
                    if (slot != null) {
                        updatePreview(slot, event.getX(), event.getY());
                    }
                    return true;
                case DragEvent.ACTION_DROP:
                    if (slot != null && updatePreview(slot, event.getX(), event.getY())) {
                        onPieceDropped(slot, previewRow, previewCol);
                    } else {
                        clearPreview();
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    draggingSlot = GameEngine.NO_SELECTION;
                    refreshPieceViews();
                    clearPreview();
                    return true;
                default:
                    return true;
            }
        }

        private Integer getDraggedSlot(DragEvent event) {
            Object localState = event.getLocalState();
            if (localState instanceof Integer) {
                return (Integer) localState;
            }
            return null;
        }

        private boolean updatePreview(int slot, float x, float y) {
            int[] cell = anchoredCellAt(slot, x, y);
            previewSlot = slot;
            previewRow = cell[0];
            previewCol = cell[1];
            invalidate();
            return previewRow > -GameEngine.BOARD_SIZE && previewCol > -GameEngine.BOARD_SIZE;
        }

        private int[] anchoredCellAt(int slot, float x, float y) {
            float size = boardSize();
            float cell = cellSize();
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            BlockPiece piece = engine.getPiece(slot);
            if (piece == null) {
                return new int[]{-1, -1};
            }
            float visibleCenterX = x;
            float visibleCenterY = y - dragHoverOffsetPx();
            int col = (int) Math.floor((visibleCenterX - left) / cell);
            int row = (int) Math.floor((visibleCenterY - top) / cell);
            if (row < 0 || row >= GameEngine.BOARD_SIZE || col < 0 || col >= GameEngine.BOARD_SIZE) {
                return new int[]{-1, -1};
            }
            return new int[]{row, col - piece.getWidth() / 2};
        }

        private void drawPreview(Canvas canvas, float cellSize, float left, float top) {
            if (previewSlot == GameEngine.NO_SELECTION) {
                return;
            }
            BlockPiece piece = engine.getPiece(previewSlot);
            if (piece == null) {
                return;
            }
            boolean canPlace = engine.canPlace(piece, previewRow, previewCol);
            paint.setColor(canPlace ? Color.argb(225, 250, 204, 21) : Color.argb(225, 255, 23, 68));
            for (BlockPiece.Cell cellPos : piece.getCells()) {
                int row = previewRow + cellPos.row;
                int col = previewCol + cellPos.col;
                if (row < 0 || row >= GameEngine.BOARD_SIZE || col < 0 || col >= GameEngine.BOARD_SIZE) {
                    continue;
                }
                float inset = dp(1);
                canvas.drawRoundRect(
                        new RectF(left + col * cellSize + inset, top + row * cellSize + inset,
                                left + (col + 1) * cellSize - inset, top + (row + 1) * cellSize - inset),
                        dp(7), dp(7), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(
                        new RectF(left + col * cellSize + dp(3), top + row * cellSize + dp(3),
                                left + (col + 1) * cellSize - dp(3), top + (row + 1) * cellSize - dp(3)),
                        dp(7), dp(7), paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(canPlace ? Color.argb(225, 250, 204, 21) : Color.argb(225, 255, 23, 68));
            }
        }

        void clearPreview() {
            previewSlot = GameEngine.NO_SELECTION;
            previewRow = -1;
            previewCol = -1;
            invalidate();
        }

        void triggerClearAnimations(int clearedLines, int multiplier) {
            flashingRows = engine.getLastClearedRowsCopy();
            flashingCols = engine.getLastClearedColsCopy();
            if (clearFlashAnimator != null) {
                clearFlashAnimator.cancel();
            }
            clearFlashAnimator = ValueAnimator.ofFloat(1f, 0f);
            clearFlashAnimator.setDuration(420);
            clearFlashAnimator.addUpdateListener(animation -> {
                clearFlashProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            clearFlashAnimator.start();

            if (multiplier > 1) {
                String praise = PRAISE_TEXT.length == 0
                        ? "Great!"
                        : PRAISE_TEXT[praiseRandom.nextInt(PRAISE_TEXT.length)];
                praiseText = praise + " x" + multiplier;
                praiseProgress = 0f;
                if (praiseAnimator != null) {
                    praiseAnimator.cancel();
                }
                praiseAnimator = ValueAnimator.ofFloat(0f, 1f);
                praiseAnimator.setDuration(720);
                praiseAnimator.addUpdateListener(animation -> {
                    praiseProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                praiseAnimator.start();
            }
        }

        private void drawLineFlash(Canvas canvas, float cellSize, float left, float top) {
            if (clearFlashProgress <= 0f) {
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (210 * clearFlashProgress), 255, 255, 255));
            for (int row = 0; row < GameEngine.BOARD_SIZE; row++) {
                if (flashingRows[row]) {
                    canvas.drawRoundRect(new RectF(left, top + row * cellSize,
                            left + GameEngine.BOARD_SIZE * cellSize, top + (row + 1) * cellSize),
                            dp(7), dp(7), paint);
                }
            }
            for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
                if (flashingCols[col]) {
                    canvas.drawRoundRect(new RectF(left + col * cellSize, top,
                            left + (col + 1) * cellSize, top + GameEngine.BOARD_SIZE * cellSize),
                            dp(7), dp(7), paint);
                }
            }
        }

        private void drawPraise(Canvas canvas, float left, float top, float size) {
            if (praiseText == null || praiseProgress >= 1f) {
                return;
            }
            float fadeProgress = Math.max(0f, (praiseProgress - 0.7f) / 0.3f);
            int alpha = Math.max(0, (int) (255 * (1f - fadeProgress)));
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(42));
            paint.setColor(Color.argb(alpha, 255, 235, 59));
            paint.setShadowLayer(dp(8), 0, dp(2), Color.BLACK);
            canvas.drawText(praiseText, left + size / 2f,
                    top + size / 2f - (paint.descent() + paint.ascent()) / 2f, paint);
            paint.clearShadowLayer();
        }

        private void drawDangerBorder(Canvas canvas, float left, float top, float size) {
            if (engine.countAvailablePlacements() > 8) {
                return;
            }
            if (dangerAnimator == null || !dangerAnimator.isStarted()) {
                dangerAnimator = ValueAnimator.ofFloat(0.25f, 1f);
                dangerAnimator.setDuration(360);
                dangerAnimator.setRepeatCount(ValueAnimator.INFINITE);
                dangerAnimator.setRepeatMode(ValueAnimator.REVERSE);
                dangerAnimator.addUpdateListener(animation -> invalidate());
                dangerAnimator.start();
            }
            float pulse = (float) dangerAnimator.getAnimatedValue();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.argb((int) (220 * pulse), 255, 23, 68));
            canvas.drawRoundRect(new RectF(left + dp(2), top + dp(2), left + size - dp(2), top + size - dp(2)),
                    dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class PieceView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slot;

        PieceView(Activity context, int slot) {
            super(context);
            this.slot = slot;
            setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && !gameEnded && engine.getPiece(slot) != null) {
                    view.performClick();
                    draggingSlot = slot;
                    invalidate();
                    ClipData dragData = ClipData.newPlainText(PIECE_DRAG_LABEL, String.valueOf(slot));
                    View.DragShadowBuilder shadowBuilder = new PieceDragShadowBuilder(view, engine.getPiece(slot));
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        view.startDragAndDrop(dragData, shadowBuilder, slot, 0);
                    } else {
                        view.startDrag(dragData, shadowBuilder, slot, 0);
                    }
                    return true;
                }
                return false;
            });
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            BlockPiece piece = engine.getPiece(slot);
            if (piece == null) {
                return;
            }

            float gridCellSize = boardView == null ? dp(40) : boardView.cellSize();
            float maxCellWidth = (getWidth() - dp(4)) / Math.max(1, piece.getWidth());
            float maxCellHeight = (getHeight() - dp(4)) / Math.max(1, piece.getHeight());
            float cellSize = Math.min(gridCellSize, Math.min(maxCellWidth, maxCellHeight));
            float originX = (getWidth() - piece.getWidth() * cellSize) / 2f;
            float originY = (getHeight() - piece.getHeight() * cellSize) / 2f;
            if (draggingSlot == slot) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(90, 255, 255, 255));
                canvas.drawRoundRect(new RectF(originX - dp(4), originY - dp(4),
                        originX + piece.getWidth() * cellSize + dp(4),
                        originY + piece.getHeight() * cellSize + dp(4)), dp(10), dp(10), paint);
            }
            drawPieceCells(canvas, paint, piece, cellSize, originX, originY, dp(2));
        }
    }

    private void drawPieceCells(Canvas canvas, Paint paint, BlockPiece piece, float cellSize,
            float originX, float originY, float inset) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(piece.getColor());
        for (BlockPiece.Cell cellPos : piece.getCells()) {
            float x = originX + cellPos.col * cellSize;
            float y = originY + cellPos.row * cellSize;
            canvas.drawRoundRect(new RectF(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset),
                    dp(5), dp(5), paint);
            paint.setColor(Color.argb(100, 255, 255, 255));
            canvas.drawRoundRect(new RectF(x + inset, y + inset, x + cellSize - inset, y + cellSize * 0.38f),
                    dp(5), dp(5), paint);
            paint.setColor(piece.getColor());
        }
    }

    private final class TitleLogoView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TitleLogoView(Activity context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float y = dp(58);
            drawLogoLine(canvas, "Kill all", centerX, y, dp(52), 0xffffd86b);
            drawLogoLine(canvas, "the", centerX, y + dp(62), dp(48), 0xffffe38c);
            drawLogoLine(canvas, "Blocks", centerX, y + dp(122), dp(50), 0xffff9f55);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xccffd86b);
            for (int i = 0; i < 8; i++) {
                float x = (i % 4) * dp(34) + centerX - dp(68);
                float blockY = y + dp(172) + (i / 4) * dp(14);
                canvas.drawRoundRect(new RectF(x, blockY, x + dp(10), blockY + dp(10)), dp(2), dp(2), paint);
            }
        }

        private void drawLogoLine(Canvas canvas, String text, float centerX, float baseline, float size, int fill) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(size);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(8));
            paint.setColor(0xff12224f);
            canvas.drawText(text, centerX, baseline, paint);
            paint.setStrokeWidth(dp(4));
            paint.setColor(0xfffff0b7);
            canvas.drawText(text, centerX, baseline, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            paint.setShadowLayer(dp(8), 0, dp(2), 0xaa000000);
            canvas.drawText(text, centerX, baseline, paint);
            paint.clearShadowLayer();
        }
    }

    private final class PieceDragShadowBuilder extends View.DragShadowBuilder {
        private final BlockPiece piece;
        private final Bitmap bitmap;

        PieceDragShadowBuilder(View view, BlockPiece piece) {
            super(view);
            this.piece = piece;
            bitmap = Bitmap.createBitmap(
                    Math.max(1, view.getWidth()),
                    Math.max(1, view.getHeight()),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            shadowSize.set(width, height);
            shadowTouchPoint.set(width / 2, Math.max(1, Math.round(height / 2f + dragHoverOffsetPx())));
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
    }

}
