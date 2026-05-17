package com.example.killalltheblocks;

import java.util.Locale;

public class ScoreEntry {
    public final int score;
    public final long finishedAtMillis;
    public final long durationMillis;
    public final String playerName;
    public final String uid;

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis) {
        this(score, finishedAtMillis, durationMillis, "Player 1", null);
    }

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis, String playerName) {
        this(score, finishedAtMillis, durationMillis, playerName, null);
    }

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis, String playerName, String uid) {
        this.score = score;
        this.finishedAtMillis = finishedAtMillis;
        this.durationMillis = durationMillis;
        this.playerName = playerName == null || playerName.trim().isEmpty()
                ? "Player 1"
                : playerName.trim();
        this.uid = uid;
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

    public String getUid() {
        return uid;
    }
}
