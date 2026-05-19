package com.example.killalltheblocks;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

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
    private FirebaseAuth firebaseAuth;
    private CredentialManager credentialManager;
    private FirebaseStore firebaseStore;
    private MediaPlayer titleMusic;
    private MediaPlayer gameplayMusic;
    private MediaPlayer highScoreMusic;
    private MediaPlayer gameOverMusic;
    private final List<String> musicShuffleQueue = new ArrayList<>();
    private int draggingSlot = GameEngine.NO_SELECTION;
    private boolean showingGlobalScores = true;
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

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseStore = new FirebaseStore();
        credentialManager = CredentialManager.create(this);
        
        settingsStore.setFirebaseStore(firebaseStore);

        showTitleScreen();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        saveCurrentGame();
        stopMusic();
        stopGameplayMusic();
        stopHighScoreMusic();
        stopGameOverMusic();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        pauseGameClock();
        saveCurrentGame();
        if (titleMusic != null && titleMusic.isPlaying()) {
            titleMusic.pause();
        }
        if (gameplayMusic != null && gameplayMusic.isPlaying()) {
            gameplayMusic.pause();
        }
        if (highScoreMusic != null && highScoreMusic.isPlaying()) {
            highScoreMusic.pause();
        }
        if (gameOverMusic != null && gameOverMusic.isPlaying()) {
            gameOverMusic.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (titleMusic != null && !titleMusic.isPlaying() && boardView == null) {
            titleMusic.start();
        }
        if (gameplayMusic != null && !gameplayMusic.isPlaying() && boardView != null) {
            gameplayMusic.start();
        }
        if (highScoreMusic != null && !highScoreMusic.isPlaying()) {
            highScoreMusic.start();
        }
        if (gameOverMusic != null && !gameOverMusic.isPlaying()) {
            gameOverMusic.start();
        }
        if (boardView != null && !gameEnded) {
            resumeGameClock();
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
        }
    }

    private void showTitleScreen() {
        timerHandler.removeCallbacks(timerRunnable);
        boardView = null;
        stopGameplayMusic();
        stopHighScoreMusic();
        startMusic();
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

        if (firebaseAuth.getCurrentUser() == null) {
            Button login = menuButton("Login");
            login.setOnClickListener(v -> showLoginScreen());
            root.addView(login, buttonParams());
        } else {
            TextView welcome = new TextView(this);
            welcome.setText("Welcome back " + firebaseAuth.getCurrentUser().getDisplayName());
            welcome.setTextColor(Color.argb(190, 255, 255, 255));
            welcome.setTextSize(12);
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(0, dp(18), 0, 0);
            root.addView(welcome, fullWrapParams());
        }

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
        stopGameplayMusic();
        stopHighScoreMusic();
        PatternLayout root = baseRoot();

        TextView heading = screenHeading("High Scores");
        root.addView(heading, fullWrapParams());

        LinearLayout scoresContainer = new LinearLayout(this);
        scoresContainer.setOrientation(LinearLayout.VERTICAL);
        scoresContainer.setPadding(0, dp(10), 0, dp(10));

        if (firebaseAuth.getCurrentUser() != null) {
            Button toggle = menuButton(showingGlobalScores ? "Switch to My Scores" : "Switch to All Users");
            toggle.setOnClickListener(v -> {
                showingGlobalScores = !showingGlobalScores;
                showHighScoresScreen();
            });
            scoresContainer.addView(toggle, buttonParams());
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout scoresList = new LinearLayout(this);
        scoresList.setOrientation(LinearLayout.VERTICAL);
        scoresList.setPadding(0, dp(18), 0, dp(18));
        
        TextView loadingLabel = leaderText("Loading scores...", true);
        scoresList.addView(loadingLabel);

        FirebaseStore.OnScoresFetchedListener scoreListener = scores -> {
            scoresList.removeAllViews();
            if (!scores.isEmpty()) {
                String currentUid = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : null;
                for (int i = 0; i < scores.size(); i++) {
                    ScoreEntry entry = scores.get(i);
                    String text = String.format(Locale.getDefault(), "%d. %s - %d pts",
                            i + 1, entry.getPlayerName(), entry.getScore());
                    TextView row = leaderText(text, true);
                    if (currentUid != null && currentUid.equals(entry.getUid())) {
                        row.setTextColor(Color.YELLOW);
                        row.setTypeface(Typeface.DEFAULT_BOLD);
                    }
                    scoresList.addView(row);
                }
            } else {
                scoresList.addView(leaderText("No scores to display.", true));
            }
        };

        if (firebaseAuth.getCurrentUser() != null) {
            if (showingGlobalScores) {
                firebaseStore.fetchGlobalScores(scoreListener);
            } else {
                firebaseStore.fetchUserScores(scoreListener);
            }
        } else {
            // Local fallback if not logged in
            scoresList.removeAllViews();
            addScoreRows(scoresList, true);
        }

        scrollView.addView(scoresList);
        scoresContainer.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        
        root.addView(scoresContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button back = menuButton("Back");
        back.setOnClickListener(v -> showTitleScreen());
        root.addView(back, buttonParams());
        setContentView(root);
    }

    private void showSettingsScreen(boolean fromGame) {
        settingsOpenedFromGame = fromGame;
        timerHandler.removeCallbacks(timerRunnable);
        if (fromGame) {
            pauseGameClock();
            saveCurrentGame();
            stopGameplayMusic();
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

        TextView fxVolumeLabel = settingLabel("FX Volume: " + settingsStore.getFxVolumePercent() + "%");
        root.addView(fxVolumeLabel, fullWrapParams());
        SeekBar fxVolume = new SeekBar(this);
        fxVolume.setMax(100);
        fxVolume.setProgress(settingsStore.getFxVolumePercent());
        fxVolume.setPadding(0, dp(8), 0, dp(18));
        fxVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settingsStore.setFxVolumePercent(progress);
                fxVolumeLabel.setText("FX Volume: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                playTone(ToneGenerator.TONE_PROP_BEEP);
            }
        });
        root.addView(fxVolume, fullWrapParams());

        TextView musicVolumeLabel = settingLabel("Music Volume: " + settingsStore.getMusicVolumePercent() + "%");
        root.addView(musicVolumeLabel, fullWrapParams());
        SeekBar musicVolume = new SeekBar(this);
        musicVolume.setMax(100);
        musicVolume.setProgress(settingsStore.getMusicVolumePercent());
        musicVolume.setPadding(0, dp(8), 0, dp(18));
        musicVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settingsStore.setMusicVolumePercent(progress);
                musicVolumeLabel.setText("Music Volume: " + progress + "%");
                updateMusicVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(musicVolume, fullWrapParams());

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

        TextView musicLabel = settingLabel("Gameplay Music");
        root.addView(musicLabel, fullWrapParams());
        Spinner musicSpinner = new Spinner(this);
        String[] musicOptions = {
                GameSettingsStore.MUSIC_RANDOM,
                GameSettingsStore.MUSIC_CLEAR_BLUE,
                GameSettingsStore.MUSIC_CLOCKWORK,
                GameSettingsStore.MUSIC_LOGIC,
                GameSettingsStore.MUSIC_IRON,
                GameSettingsStore.MUSIC_SEVEN,
                GameSettingsStore.MUSIC_NOTCH
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, musicOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        musicSpinner.setAdapter(adapter);
        
        String currentMusic = settingsStore.getGameplayMusic();
        for (int i = 0; i < musicOptions.length; i++) {
            if (musicOptions[i].equals(currentMusic)) {
                musicSpinner.setSelection(i);
                break;
            }
        }
        
        musicSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settingsStore.setGameplayMusic(musicOptions[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(musicSpinner, fullWrapParams());

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
                startGameplayMusic();
                timerHandler.removeCallbacks(timerRunnable);
                timerHandler.post(timerRunnable);
                refreshAll(false);
            } else {
                showTitleScreen();
            }
        });
        root.addView(back, buttonParams());

        if (firebaseAuth.getCurrentUser() != null) {
            Button logout = menuButton("Logout");
            logout.setOnClickListener(v -> {
                firebaseAuth.signOut();
                credentialManager.clearCredentialStateAsync(
                        new ClearCredentialStateRequest(),
                        new CancellationSignal(),
                        Executors.newSingleThreadExecutor(),
                        new CredentialManagerCallback<Void, ClearCredentialException>() {
                            @Override
                            public void onResult(Void result) {
                                runOnUiThread(() -> showTitleScreen());
                            }

                            @Override
                            public void onError(@NonNull ClearCredentialException e) {
                                runOnUiThread(() -> showTitleScreen());
                            }
                        }
                );
            });
            root.addView(logout, buttonParams());
        }

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
        root.setOnDragListener((view, event) -> {
            if (boardView == null) {
                return false;
            }
            return boardView.handleDragEvent(
                    event,
                    event.getX() - boardView.getLeft(),
                    event.getY() - boardView.getTop());
        });

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
        stopMusic();
        startGameplayMusic();
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
        stopMusic();
        startGameplayMusic();
        engine.restoreState(savedGame.encodedBoard, savedGame.encodedBoardColors,
                savedGame.pieceNames, savedGame.score, savedGame.comboStreak);
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
            int combo = engine.getComboStreak();
            
            boardView.triggerClearAnimations(1, false, combo);
            performClearHaptics(1);
            playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD);
            shakeBoard();
        }
        draggingSlot = GameEngine.NO_SELECTION;
        saveCurrentGame();
        if (!engine.hasAnyMove()) {
            finishGame();
        } else {
            refreshAll(true);
            refreshPieceViews();
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
        stopGameplayMusic();
        long duration = activeElapsedMillis;
        engine.setFinishedDurationMillis(duration);
        timerHandler.removeCallbacks(timerRunnable);

        int previousHighScore = getHighScore();
        final ScoreEntry entry = new ScoreEntry(engine.getScore(), System.currentTimeMillis(), duration, settingsStore.getPlayerName());
        scoreStore.addScore(entry);
        gameStateStore.clear();
        refreshAll(true);
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT);

        if (firebaseAuth.getCurrentUser() != null) {
            firebaseStore.submitScore(entry, (isPersonalBest, globalRank) -> {
                StringBuilder message = new StringBuilder();
                message.append("Score: ").append(entry.getScore()).append("\n");
                message.append("Duration: ").append(formatDuration(entry.getDurationMillis())).append("\n\n");

                boolean highAchievement = false;
                if (isPersonalBest) {
                    message.append("New Personal Best! 🌟\n");
                    startHighScoreMusic();
                    highAchievement = true;
                }

                if (globalRank > 0 && globalRank <= 10) {
                    message.append("Congratulations! You've reached #").append(globalRank).append(" on the Global Leaderboard! 🏆");
                    if (!isPersonalBest) startHighScoreMusic();
                    highAchievement = true;
                }

                if (!highAchievement) {
                    startGameOverMusic();
                }

                showGameOverDialog(message.toString());
            });
        } else {
            boolean beatHighScore = engine.getScore() > previousHighScore && previousHighScore > 0;
            if (beatHighScore) {
                startHighScoreMusic();
            } else {
                startGameOverMusic();
            }
            String message = "Score: " + entry.getScore() + "\nDuration: " + formatDuration(duration);
            if (beatHighScore) {
                message += "\n\nNew High Score! 🌟";
            }
            showGameOverDialog(message);
        }
    }

    private void showGameOverDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Game over")
                .setMessage(message)
                .setPositiveButton("New Game", (dialog, which) -> {
                    stopHighScoreMusic();
                    stopGameOverMusic();
                    startNewGame();
                })
                .setNegativeButton("Menu", (dialog, which) -> {
                    stopHighScoreMusic();
                    stopGameOverMusic();
                    showTitleScreen();
                })
                .setCancelable(false)
                .show();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dp(float value) {
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
        int volume = settingsStore == null ? 70 : settingsStore.getFxVolumePercent();
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
        if (titleMusic != null) {
            if (titleMusic.isPlaying()) {
                titleMusic.stop();
            }
            titleMusic.release();
            titleMusic = null;
        }
    }

    private void stopGameplayMusic() {
        if (gameplayMusic != null) {
            if (gameplayMusic.isPlaying()) {
                gameplayMusic.stop();
            }
            gameplayMusic.release();
            gameplayMusic = null;
        }
    }

    private void stopHighScoreMusic() {
        if (highScoreMusic != null) {
            if (highScoreMusic.isPlaying()) {
                highScoreMusic.stop();
            }
            highScoreMusic.release();
            highScoreMusic = null;
        }
    }

    private void stopGameOverMusic() {
        if (gameOverMusic != null) {
            if (gameOverMusic.isPlaying()) {
                gameOverMusic.stop();
            }
            gameOverMusic.release();
            gameOverMusic = null;
        }
    }

    private void startMusic() {
        if (titleMusic == null) {
            titleMusic = MediaPlayer.create(this, R.raw.clear_blue_ascent);
            if (titleMusic != null) {
                titleMusic.setLooping(true);
                updateMusicVolume();
                titleMusic.start();
            }
        } else if (!titleMusic.isPlaying()) {
            titleMusic.start();
        }
    }

    private void startGameplayMusic() {
        if (gameplayMusic != null) {
            stopGameplayMusic();
        }

        String selectedSetting = settingsStore.getGameplayMusic();
        boolean isRandom = GameSettingsStore.MUSIC_RANDOM.equals(selectedSetting);
        String selectedTrack;

        if (isRandom) {
            if (musicShuffleQueue.isEmpty()) {
                musicShuffleQueue.addAll(Arrays.asList(
                        GameSettingsStore.MUSIC_CLEAR_BLUE,
                        GameSettingsStore.MUSIC_CLOCKWORK,
                        GameSettingsStore.MUSIC_LOGIC,
                        GameSettingsStore.MUSIC_IRON,
                        GameSettingsStore.MUSIC_NOTCH,
                        GameSettingsStore.MUSIC_SEVEN
                ));
                Collections.shuffle(musicShuffleQueue);
            }
            selectedTrack = musicShuffleQueue.remove(0);
        } else {
            selectedTrack = selectedSetting;
            musicShuffleQueue.clear();
        }

        int resId = 0;
        if (GameSettingsStore.MUSIC_CLEAR_BLUE.equals(selectedTrack)) {
            resId = R.raw.clear_blue_ascent;
        } else if (GameSettingsStore.MUSIC_CLOCKWORK.equals(selectedTrack)) {
            resId = R.raw.clockwork_bloom;
        } else if (GameSettingsStore.MUSIC_LOGIC.equals(selectedTrack)) {
            resId = R.raw.logic_of_the_lock;
        } else if (GameSettingsStore.MUSIC_IRON.equals(selectedTrack)) {
            resId = R.raw.the_iron_pivot;
        } else if (GameSettingsStore.MUSIC_NOTCH.equals(selectedTrack)) {
            resId = R.raw.the_final_notch;
        } else if (GameSettingsStore.MUSIC_SEVEN.equals(selectedTrack)) {
            resId = R.raw.seven_turns_to_open;
        }

        if (resId != 0) {
            gameplayMusic = MediaPlayer.create(this, resId);
            if (gameplayMusic != null) {
                gameplayMusic.setLooping(!isRandom);
                if (isRandom) {
                    gameplayMusic.setOnCompletionListener(mp -> startGameplayMusic());
                }
                updateMusicVolume();
                gameplayMusic.start();
            }
        }
    }

    private void startHighScoreMusic() {
        if (highScoreMusic == null) {
            highScoreMusic = MediaPlayer.create(this, R.raw.shifting_rooms);
            if (highScoreMusic != null) {
                highScoreMusic.setLooping(true);
                updateMusicVolume();
                highScoreMusic.start();
            }
        }
    }

    private void startGameOverMusic() {
        if (gameOverMusic == null) {
            gameOverMusic = MediaPlayer.create(this, R.raw.controller_unplugged);
            if (gameOverMusic != null) {
                gameOverMusic.setLooping(true);
                updateMusicVolume();
                gameOverMusic.start();
            }
        }
    }

    private void updateMusicVolume() {
        float volume = settingsStore.getMusicVolumePercent() / 100f;
        if (titleMusic != null) {
            titleMusic.setVolume(volume, volume);
        }
        if (gameplayMusic != null) {
            gameplayMusic.setVolume(volume, volume);
        }
        if (highScoreMusic != null) {
            highScoreMusic.setVolume(volume, volume);
        }
        if (gameOverMusic != null) {
            gameOverMusic.setVolume(volume, volume);
        }
    }

    private void refreshPieceViews() {
        if (pieceViews == null) {
            return;
        }
        for (PieceView pieceView : pieceViews) {
            pieceView.invalidate();
        }
    }

    private void startSignIn() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e("GameActivity", "Credential Manager error", e);
                        runOnUiThread(() -> Toast.makeText(GameActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null && user.getDisplayName() != null) {
                            settingsStore.setPlayerName(user.getDisplayName());
                        }
                        firebaseStore.syncOnLogin(scoreStore, settingsStore);
                        showTitleScreen();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Auth failed";
                        Toast.makeText(this, "Firebase Auth failed: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleSignIn(Credential credential) {
        if (credential instanceof CustomCredential && credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
            } catch (Exception e) {
                Log.e("GameActivity", "Error parsing Google ID Token", e);
                Toast.makeText(this, "Error parsing Google ID Token", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLoginScreen() {
        PatternLayout root = baseRoot();
        root.setGravity(Gravity.CENTER);

        TextView heading = screenHeading("Login");
        root.addView(heading, fullWrapParams());

        Button googleLogin = menuButton("Login with Google");
        googleLogin.setOnClickListener(v -> startSignIn());
        root.addView(googleLogin, buttonParams());

        TextView or = new TextView(this);
        or.setText("— OR —");
        or.setTextColor(Color.WHITE);
        or.setGravity(Gravity.CENTER);
        or.setPadding(0, dp(16), 0, dp(16));
        root.addView(or, fullWrapParams());

        EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        emailInput.setTextColor(Color.WHITE);
        emailInput.setHintTextColor(Color.LTGRAY);
        root.addView(emailInput, fullWrapParams());

        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(Color.LTGRAY);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordInput, fullWrapParams());

        Button emailLogin = menuButton("Login with Email");
        emailLogin.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }
            loginEmailUser(email, password);
        });
        root.addView(emailLogin, buttonParams());

        Button createAccount = menuButton("Create Account");
        createAccount.setOnClickListener(v -> showEmailRegisterDialog());
        root.addView(createAccount, buttonParams());

        Button back = menuButton("Back");
        back.setOnClickListener(v -> showTitleScreen());
        root.addView(back, buttonParams());

        setContentView(root);
    }

    private void showEmailRegisterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create Account");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        layout.addView(nameInput);

        final EditText emailInput = new EditText(this);
        emailInput.setHint("Email");
        emailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(emailInput);

        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);

        final EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setHint("Confirm Password");
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            String confirm = confirmPasswordInput.getText().toString();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            registerEmailUser(name, email, password);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loginEmailUser(String email, String password) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null && user.getDisplayName() != null) {
                            settingsStore.setPlayerName(user.getDisplayName());
                        }
                        firebaseStore.syncOnLogin(scoreStore, settingsStore);
                        showTitleScreen();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Login failed";
                        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void registerEmailUser(final String name, String email, String password) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        settingsStore.setPlayerName(name);
                                        firebaseStore.syncOnLogin(scoreStore, settingsStore);
                                        showTitleScreen();
                                    });
                        }
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private final class PatternLayout extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float glowPos = -0.5f;
        private final List<GhostPiece> ghosts = new ArrayList<>();
        private ValueAnimator glowAnimator;
        private final Matrix shaderMatrix = new Matrix();
        private LinearGradient glowShader;
        private final RectF ghostRect = new RectF();

        private int lastWidth = -1;
        private int lastHeight = -1;

        private class GhostPiece {
            float x, y, scale;
            BlockPiece piece;
            int rotation;
        }

        PatternLayout(Activity context) {
            super(context);
            setWillNotDraw(false);
            initGhosts();
            startAnimation();
        }

        private void initGhosts() {
            Random r = new Random(42);
            List<BlockPiece> all = BlockPiece.standardPieces();
            float spacing = dp(50);
            for (int i = 0; i < 20; i++) {
                GhostPiece g = new GhostPiece();
                boolean overlaps;
                int attempts = 0;
                do {
                    overlaps = false;
                    // Align to grid spacing
                    g.x = (float) Math.floor(r.nextFloat() * 15);
                    g.y = (float) Math.floor(r.nextFloat() * 25);
                    g.scale = 1.0f; // Scale 1.0 to fit grid perfectly
                    g.piece = all.get(r.nextInt(all.size()));
                    g.rotation = r.nextInt(4) * 90;
                    
                    for (GhostPiece other : ghosts) {
                        float dx = Math.abs(g.x - other.x);
                        float dy = Math.abs(g.y - other.y);
                        // Simple grid-based overlap check
                        if (dx < 4 && dy < 4) {
                            overlaps = true;
                            break;
                        }
                    }
                    attempts++;
                } while (overlaps && attempts < 30);
                
                ghosts.add(g);
            }
        }

        private void startAnimation() {
            glowAnimator = ValueAnimator.ofFloat(-0.8f, 1.8f);
            glowAnimator.setDuration(8000); // Slightly faster for more visibility
            glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            glowAnimator.setRepeatMode(ValueAnimator.RESTART);
            glowAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            glowAnimator.addUpdateListener(animation -> {
                glowPos = (float) animation.getAnimatedValue();
                invalidate();
            });
            glowAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                super.dispatchDraw(canvas);
                return;
            }

            // Darker Deep Blue/Purple/Pink/Orange Background
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    0, 0, w, h,
                    new int[]{0xff0f172a, 0xff4c1d95, 0xff831843, 0xff7c2d12},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            float spacing = dp(50);

            // Draw ghost shapes aligned to grid
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(0x33ffffff);
            for (GhostPiece g : ghosts) {
                canvas.save();
                canvas.translate(g.x * spacing, g.y * spacing);
                canvas.rotate(g.rotation);
                
                float cellSize = spacing / 4f; // Fit inside the grid spacing
                for (BlockPiece.Cell cell : g.piece.getCells()) {
                    float left = cell.col * cellSize;
                    float top = cell.row * cellSize;
                    ghostRect.set(left + dp(2), top + dp(2), left + cellSize - dp(2), top + cellSize - dp(2));
                    canvas.drawRoundRect(ghostRect, dp(6), dp(6), paint);
                }
                canvas.restore();
            }

            // Draw Stars
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < STAR_X.length; i++) {
                float cx = STAR_X[i] * w;
                float cy = STAR_Y[i] * h;
                paint.setColor(0x88ffffff); 
                canvas.drawCircle(cx, cy, dp(1.5f), paint);
            }

            // Highlight wave only on grid lines
            if (w != lastWidth || h != lastHeight) {
                glowShader = new LinearGradient(
                        0, 0, spacing * 2, 0,
                        new int[]{0x0039ff14, 0xaa39ff14, 0xaa39ff14, 0x0039ff14},
                        null, Shader.TileMode.CLAMP);
                lastWidth = (int) w;
                lastHeight = (int) h;
            }

            if (glowShader != null) {
                shaderMatrix.setTranslate(glowPos * (w + spacing * 4) - spacing * 2, 0);
                glowShader.setLocalMatrix(shaderMatrix);
                paint.setShader(glowShader);
                paint.setStrokeWidth(dp(2));
                
                // Draw ONLY the lines with the shader
                for (float x = 0; x <= w; x += spacing) {
                    canvas.drawLine(x, 0, x, h, paint);
                }
                for (float y = 0; y <= h; y += spacing) {
                    canvas.drawLine(0, y, w, y, paint);
                }
                paint.setShader(null);
            }

            super.dispatchDraw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (glowAnimator != null) glowAnimator.cancel();
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
        private boolean[] previewRows = new boolean[GameEngine.BOARD_SIZE];
        private boolean[] previewCols = new boolean[GameEngine.BOARD_SIZE];
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

        private boolean handleDragEvent(DragEvent event, float x, float y) {
            Integer slot = getDraggedSlot(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return slot != null && !gameEnded && engine.getPiece(slot) != null;
                case DragEvent.ACTION_DRAG_LOCATION:
                    if (slot != null) {
                        updatePreview(slot, x, y);
                    }
                    return true;
                case DragEvent.ACTION_DROP:
                    if (slot != null && updatePreview(slot, x, y)) {
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

            BlockPiece piece = engine.getPiece(slot);
            if (piece != null && engine.canPlace(piece, previewRow, previewCol)) {
                boolean[][] clears = engine.predictLineClears(piece, previewRow, previewCol);
                previewRows = clears[0];
                previewCols = clears[1];
            } else {
                java.util.Arrays.fill(previewRows, false);
                java.util.Arrays.fill(previewCols, false);
            }

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
            float[] pieceTopLeft = draggedPieceTopLeft(slot, piece, x, y, cell);
            float maxLeft = left + (GameEngine.BOARD_SIZE - piece.getWidth()) * cell;
            float maxTop = top + (GameEngine.BOARD_SIZE - piece.getHeight()) * cell;
            float visibleLeft = clamp(pieceTopLeft[0], left, maxLeft);
            float visibleTop = clamp(pieceTopLeft[1], top, maxTop);
            int col = (int) Math.floor((visibleLeft - left) / cell);
            int row = (int) Math.floor((visibleTop - top) / cell);
            return new int[]{row, col};
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private float[] draggedPieceTopLeft(int slot, BlockPiece piece, float fingerX, float fingerY, float gridCellSize) {
            View pieceView = pieceViews == null || slot < 0 || slot >= pieceViews.length ? null : pieceViews[slot];
            float shadowWidth = pieceView == null ? piece.getWidth() * gridCellSize : Math.max(1, pieceView.getWidth());
            float shadowHeight = pieceView == null ? piece.getHeight() * gridCellSize : Math.max(1, pieceView.getHeight());
            
            // Adjust pieceCellSize to better match gridCellSize
            float pieceCellSize = pieceView == null
                    ? gridCellSize
                    : Math.min(gridCellSize, Math.min(
                            (shadowWidth - dp(4)) / Math.max(1, piece.getWidth()),
                            (shadowHeight - dp(4)) / Math.max(1, piece.getHeight())));
            
            float pieceOffsetX = (shadowWidth - piece.getWidth() * pieceCellSize) / 2f;
            float pieceOffsetY = (shadowHeight - piece.getHeight() * pieceCellSize) / 2f;
            
            // Center the piece shadow exactly under the finger
            float shadowLeft = fingerX - shadowWidth / 2f;
            float shadowTop = fingerY - (shadowHeight / 2f + dragHoverOffsetPx());
            
            return new float[]{shadowLeft + pieceOffsetX, shadowTop + pieceOffsetY};
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

            if (canPlace) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(80, 255, 255, 255));
                for (int r = 0; r < GameEngine.BOARD_SIZE; r++) {
                    if (previewRows[r]) {
                        canvas.drawRoundRect(new RectF(left, top + r * cellSize,
                                left + GameEngine.BOARD_SIZE * cellSize, top + (r + 1) * cellSize),
                                dp(2), dp(2), paint);
                    }
                }
                for (int c = 0; c < GameEngine.BOARD_SIZE; c++) {
                    if (previewCols[c]) {
                        canvas.drawRoundRect(new RectF(left + c * cellSize, top,
                                left + (c + 1) * cellSize, top + GameEngine.BOARD_SIZE * cellSize),
                                dp(2), dp(2), paint);
                    }
                }
            }

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
            java.util.Arrays.fill(previewRows, false);
            java.util.Arrays.fill(previewCols, false);
            invalidate();
        }

        void triggerClearAnimations(int multiplier, boolean boardCleared, int combo) {
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

            if (combo > 1) {
                String praise = PRAISE_TEXT[praiseRandom.nextInt(PRAISE_TEXT.length)];
                if (combo >= 4) {
                    praise = "MEGA COMBO!";
                }
                praiseText = praise + " x" + combo;
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
                    View.DragShadowBuilder shadowBuilder = new PieceDragShadowBuilder(view);
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
        private final Bitmap bitmap;

        PieceDragShadowBuilder(View view) {
            super(view);
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
            
            // Fix horizontal alignment: center the touch point exactly
            int touchX = width / 2;
            int touchY = Math.max(1, Math.round(height / 2f + dragHoverOffsetPx()));
            shadowTouchPoint.set(touchX, touchY);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
    }

}
