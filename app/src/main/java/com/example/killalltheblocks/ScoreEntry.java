package com.example.killalltheblocks;

import java.util.Locale;

public class ScoreEntry {
    public final int score;
    public final long finishedAtMillis;
    public final long durationMillis;

    public ScoreEntry(int score, long finishedAtMillis, long durationMillis) {
        this.score = score;
        this.finishedAtMillis = finishedAtMillis;
        this.durationMillis = durationMillis;
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
}
