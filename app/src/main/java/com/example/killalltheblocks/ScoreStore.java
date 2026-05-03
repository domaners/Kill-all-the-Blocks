package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScoreStore {
    private static final String PREFS_NAME = "scores";
    private static final String KEY_TOP_SCORES = "top_scores";
    private static final int MAX_SCORES = 10;

    private final SharedPreferences preferences;

    public ScoreStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ScoreEntry> loadTopScores() {
        String raw = preferences.getString(KEY_TOP_SCORES, "[]");
        List<ScoreEntry> scores = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                long finishedAt = object.getLong("finishedAtMillis");
                long duration = object.getLong("durationMillis");
                scores.add(new ScoreEntry(object.getInt("score"), finishedAt, duration));
            }
        } catch (JSONException ignored) {
            scores.clear();
        }
        sortScores(scores);
        return scores;
    }

    public List<ScoreEntry> addScore(int score, long timestamp, long durationMillis) {
        List<ScoreEntry> scores = loadTopScores();
        scores.add(new ScoreEntry(score, timestamp, durationMillis));
        sortScores(scores);
        while (scores.size() > MAX_SCORES) {
            scores.remove(scores.size() - 1);
        }
        saveScores(scores);
        return scores;
    }

    public List<ScoreEntry> addScore(ScoreEntry entry) {
        return addScore(entry.getScore(), entry.getFinishedAtMillis(), entry.getDurationMillis());
    }

    public void clearScores() {
        preferences.edit().remove(KEY_TOP_SCORES).apply();
    }

    private void saveScores(List<ScoreEntry> scores) {
        JSONArray array = new JSONArray();
        for (ScoreEntry score : scores) {
            JSONObject object = new JSONObject();
            try {
                object.put("score", score.getScore());
                object.put("finishedAtMillis", score.getFinishedAtMillis());
                object.put("durationMillis", score.getDurationMillis());
                array.put(object);
            } catch (JSONException ignored) {
                // Values are primitives and should always be accepted by JSONObject.
            }
        }
        preferences.edit().putString(KEY_TOP_SCORES, array.toString()).apply();
    }

    private static void sortScores(List<ScoreEntry> scores) {
        Collections.sort(scores, new Comparator<ScoreEntry>() {
            @Override
            public int compare(ScoreEntry first, ScoreEntry second) {
                int scoreCompare = Integer.compare(second.getScore(), first.getScore());
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return Long.compare(first.getDurationMillis(), second.getDurationMillis());
            }
        });
    }
}
