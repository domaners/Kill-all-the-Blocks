package com.example.killalltheblocks;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GameActivity extends Activity {
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final GameEngine engine = new GameEngine();
    private ScoreStore scoreStore;
    private GameStateStore gameStateStore;
    private BoardView boardView;
    private PieceView[] pieceViews;
    private TextView scoreView;
    private TextView timerView;
    private TextView statusView;
    private LinearLayout leaderboardLayout;
    private long gameStartedAt;
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
        GameStateStore.SavedGame savedGame = gameStateStore.load();
        if (savedGame != null) {
            engine.restoreState(savedGame.encodedBoard, savedGame.pieceNames, savedGame.score, savedGame.selectedSlot);
            gameStartedAt = savedGame.startedAtMillis;
            gameEnded = savedGame.gameEnded;
            engine.setFinishedDurationMillis(savedGame.finishedDurationMillis);
        } else {
            gameStartedAt = System.currentTimeMillis();
        }
        setContentView(createContentView());
        refreshAll();
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        saveCurrentGame();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        saveCurrentGame();
        super.onPause();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(17, 24, 39));

        TextView title = new TextView(this);
        title.setText("Kill All the Blocks");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, dp(10), 0, dp(10));
        scoreView = statText();
        timerView = statText();
        stats.addView(scoreView, weightedWrapParams());
        stats.addView(timerView, weightedWrapParams());
        root.addView(stats);

        LinearLayout playArea = new LinearLayout(this);
        playArea.setOrientation(LinearLayout.HORIZONTAL);
        playArea.setGravity(Gravity.CENTER);
        playArea.setBaselineAligned(false);

        boardView = new BoardView(this);
        LinearLayout.LayoutParams boardParams = new LinearLayout.LayoutParams(0, dp(300), 1f);
        playArea.addView(boardView, boardParams);

        LinearLayout tray = new LinearLayout(this);
        tray.setOrientation(LinearLayout.VERTICAL);
        tray.setGravity(Gravity.CENTER);
        tray.setPadding(dp(12), 0, 0, 0);
        pieceViews = new PieceView[GameEngine.PIECE_SLOTS];
        for (int i = 0; i < pieceViews.length; i++) {
            PieceView pieceView = new PieceView(this, i);
            pieceViews[i] = pieceView;
            LinearLayout.LayoutParams pieceParams = new LinearLayout.LayoutParams(dp(86), dp(86));
            pieceParams.setMargins(0, dp(4), 0, dp(4));
            tray.addView(pieceView, pieceParams);
        }
        playArea.addView(tray, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(300)));
        root.addView(playArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(209, 213, 219));
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusView);

        Button newGameButton = new Button(this);
        newGameButton.setText("New Game");
        newGameButton.setOnClickListener(v -> startNewGame());
        root.addView(newGameButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView leadersTitle = new TextView(this);
        leadersTitle.setText("Top 10 Scores");
        leadersTitle.setTextColor(Color.WHITE);
        leadersTitle.setTextSize(20);
        leadersTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        leadersTitle.setPadding(0, dp(14), 0, dp(4));
        root.addView(leadersTitle);

        ScrollView scrollView = new ScrollView(this);
        leaderboardLayout = new LinearLayout(this);
        leaderboardLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(leaderboardLayout);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
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
        gameEnded = false;
        saveCurrentGame();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        refreshAll();
    }

    private void refreshAll() {
        scoreView.setText("Score: " + engine.getScore());
        updateTimer();
        statusView.setText(gameEnded
                ? "Game over. Start a new game to try for a higher score."
                : "Tap a block, then tap the grid where its top-left corner should land.");
        boardView.invalidate();
        for (PieceView pieceView : pieceViews) {
            pieceView.invalidate();
        }
        refreshLeaderboard();
    }

    private void updateTimer() {
        long duration = gameEnded ? engine.getFinishedDurationMillis() : System.currentTimeMillis() - gameStartedAt;
        timerView.setText("Time: " + formatDuration(duration));
    }

    private void refreshLeaderboard() {
        leaderboardLayout.removeAllViews();
        List<ScoreEntry> entries = scoreStore.loadTopScores();
        if (entries.isEmpty()) {
            TextView empty = leaderText("No completed games yet.");
            leaderboardLayout.addView(empty);
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry entry = entries.get(i);
            String text = String.format(Locale.getDefault(), "%d. %d pts - %s - %s",
                    i + 1,
                    entry.getScore(),
                    formatter.format(new Date(entry.getFinishedAtMillis())),
                    formatDuration(entry.getDurationMillis()));
            leaderboardLayout.addView(leaderText(text));
        }
    }

    private TextView leaderText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(229, 231, 235));
        view.setTextSize(14);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private void onBoardTapped(int row, int col) {
        if (gameEnded || engine.getSelectedSlot() == GameEngine.NO_SELECTION) {
            return;
        }
        if (!engine.placeSelected(row, col)) {
            statusView.setText("That block does not fit there.");
            return;
        }
        saveCurrentGame();
        if (!engine.hasAnyMove()) {
            finishGame();
        } else {
            refreshAll();
        }
    }

    private void finishGame() {
        gameEnded = true;
        long duration = System.currentTimeMillis() - gameStartedAt;
        engine.setFinishedDurationMillis(duration);
        timerHandler.removeCallbacks(timerRunnable);
        scoreStore.addScore(engine.getScore(), System.currentTimeMillis(), duration);
        gameStateStore.clear();
        refreshAll();
        new AlertDialog.Builder(this)
                .setTitle("Game over")
                .setMessage("Score: " + engine.getScore() + "\nDuration: " + formatDuration(duration))
                .setPositiveButton("New Game", (dialog, which) -> startNewGame())
                .setNegativeButton("Close", null)
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

    private void saveCurrentGame() {
        if (!gameEnded && gameStateStore != null) {
            gameStateStore.save(engine, gameStartedAt);
        }
    }

    private final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BoardView(Activity context) {
            super(context);
            setOnClickListener(null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float cell = size / GameEngine.BOARD_SIZE;
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(31, 41, 55));
            canvas.drawRoundRect(new RectF(left, top, left + size, top + size), dp(10), dp(10), paint);

            boolean[][] board = engine.copyBoard();
            for (int row = 0; row < GameEngine.BOARD_SIZE; row++) {
                for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
                    paint.setColor(board[row][col] ? Color.rgb(96, 165, 250) : Color.rgb(55, 65, 81));
                    float inset = dp(2);
                    canvas.drawRoundRect(
                            new RectF(left + col * cell + inset, top + row * cell + inset,
                                    left + (col + 1) * cell - inset, top + (row + 1) * cell - inset),
                            dp(5), dp(5), paint);
                }
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                performClick();
                float size = Math.min(getWidth(), getHeight());
                float cell = size / GameEngine.BOARD_SIZE;
                float left = (getWidth() - size) / 2f;
                float top = (getHeight() - size) / 2f;
                int col = (int) ((event.getX() - left) / cell);
                int row = (int) ((event.getY() - top) / cell);
                if (row >= 0 && row < GameEngine.BOARD_SIZE && col >= 0 && col < GameEngine.BOARD_SIZE) {
                    onBoardTapped(row, col);
                }
                return true;
            }
            return true;
        }
    }

    private final class PieceView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slot;

        PieceView(Activity context, int slot) {
            super(context);
            this.slot = slot;
            setOnClickListener(v -> {
                if (!gameEnded && engine.selectSlot(slot)) {
                    refreshAll();
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            BlockPiece piece = engine.getPiece(slot);
            boolean selected = engine.getSelectedSlot() == slot;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(59, 130, 246) : Color.rgb(31, 41, 55));
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), dp(10), dp(10), paint);
            if (piece == null) {
                paint.setColor(Color.rgb(107, 114, 128));
                paint.setTextSize(dp(14));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Placed", getWidth() / 2f, getHeight() / 2f + dp(5), paint);
                return;
            }

            float cellSize = Math.min(getWidth() / (piece.getWidth() + 1.5f),
                    getHeight() / (piece.getHeight() + 1.5f));
            float originX = (getWidth() - piece.getWidth() * cellSize) / 2f;
            float originY = (getHeight() - piece.getHeight() * cellSize) / 2f;
            paint.setColor(piece.getColor());
            for (BlockPiece.Cell cellPos : piece.getCells()) {
                float x = originX + cellPos.col * cellSize;
                float y = originY + cellPos.row * cellSize;
                float inset = dp(2);
                canvas.drawRoundRect(new RectF(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset),
                        dp(5), dp(5), paint);
            }
        }
    }
}
