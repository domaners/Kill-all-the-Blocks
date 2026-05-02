package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class GameStateStore {
    private static final String PREFS_NAME = "game_state";
    private static final String KEY_STATE = "state";

    private final SharedPreferences preferences;

    GameStateStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void save(GameEngine engine, long startedAtMillis, boolean gameEnded) {
        JSONObject state = new JSONObject();
        try {
            state.put("score", engine.getScore());
            state.put("selectedSlot", engine.getSelectedSlot());
            state.put("startedAtMillis", startedAtMillis);
            state.put("finishedDurationMillis", engine.getFinishedDurationMillis());
            state.put("gameEnded", gameEnded);
            state.put("board", engine.encodeBoard());

            JSONArray tray = new JSONArray();
            String[] pieceNames = engine.getPieceNames();
            for (String pieceName : pieceNames) {
                tray.put(pieceName);
            }
            state.put("tray", tray);
            preferences.edit().putString(KEY_STATE, state.toString()).apply();
        } catch (JSONException ignored) {
            // JSONObject only receives primitive values and arrays built in this method.
        }
    }

    SavedGame load() {
        String raw = preferences.getString(KEY_STATE, null);
        if (raw == null) {
            return null;
        }
        try {
            JSONObject state = new JSONObject(raw);
            String encodedBoard = state.getString("board");
            String[] pieceNames = new String[GameEngine.PIECE_SLOTS];
            JSONArray trayValues = state.getJSONArray("tray");
            for (int slot = 0; slot < GameEngine.PIECE_SLOTS; slot++) {
                pieceNames[slot] = trayValues.optString(slot, "");
            }

            return new SavedGame(
                    encodedBoard,
                    pieceNames,
                    state.getInt("score"),
                    state.optInt("selectedSlot", GameEngine.NO_SELECTION),
                    state.optLong("finishedDurationMillis", 0L),
                    state.optLong("startedAtMillis", System.currentTimeMillis()),
                    state.optBoolean("gameEnded", false));
        } catch (JSONException ignored) {
            return null;
        }
    }

    void clear() {
        preferences.edit().remove(KEY_STATE).apply();
    }

    static final class SavedGame {
        final String encodedBoard;
        final String[] pieceNames;
        final int score;
        final int selectedSlot;
        final long finishedDurationMillis;
        final long startedAtMillis;
        final boolean gameEnded;

        SavedGame(
                String encodedBoard,
                String[] pieceNames,
                int score,
                int selectedSlot,
                long finishedDurationMillis,
                long startedAtMillis,
                boolean gameEnded) {
            this.encodedBoard = encodedBoard;
            this.pieceNames = pieceNames;
            this.score = score;
            this.selectedSlot = selectedSlot;
            this.finishedDurationMillis = finishedDurationMillis;
            this.startedAtMillis = startedAtMillis;
            this.gameEnded = gameEnded;
        }
    }
}
