package com.example.killalltheblocks;

import java.util.Locale;

public class ScoreEntry {
    public final int score;
    public final long finishedAtMillis;
    public final long durationMillis;
    public final String playerName;

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis) {
        this(score, finishedAtMillis, durationMillis, "Player 1");
    }

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis, String playerName) {
        this.score = score;
        this.finishedAtMillis = finishedAtMillis;
        this.durationMillis = durationMillis;
        this.playerName = playerName == null || playerName.trim().isEmpty()
                ? "Player 1"
                : playerName.trim();
    }

    public int getScore() {
        return score;
    }

    public long getFinishedAtMillis() {
        return finishedAtMillis;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getPlayerName() {
        return playerName;
    }
}
