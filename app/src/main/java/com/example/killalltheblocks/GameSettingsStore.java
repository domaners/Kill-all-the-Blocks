package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GameSettingsStore {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_FX_VOLUME = "fx_volume";
    private static final String KEY_MUSIC_VOLUME = "music_volume";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_PLAYER_NAME = "player_name";
    private static final String KEY_GAMEPLAY_MUSIC = "gameplay_music";
    private static final int DEFAULT_VOLUME = 70;
    private static final String DEFAULT_PLAYER_NAME = "Player 1";
    public static final String MUSIC_RANDOM = "Random";
    public static final String MUSIC_CLEAR_BLUE = "Clear Blue Ascent";
    public static final String MUSIC_CLOCKWORK = "Clockwork Bloom";
    public static final String MUSIC_LOGIC = "Logic of the Lock";
    public static final String MUSIC_IRON = "The Iron Pivot";
    public static final String MUSIC_SEVEN = "Seven Turns to Open";
    public static final String MUSIC_NOTCH = "The Final Notch";

    private static final String KEY_DEV_CONFIG = "dev_config";

    private final SharedPreferences preferences;
    private FirebaseStore firebaseStore;

    GameSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setDevConfig(String json) {
        preferences.edit().putString(KEY_DEV_CONFIG, json).apply();
    }

    public String getDevConfig() {
        return preferences.getString(KEY_DEV_CONFIG, "{}");
    }

    public void applyDevConfigToPieces() {
        try {
            org.json.JSONObject config = new org.json.JSONObject(getDevConfig());
            if (config.has("pieces")) {
                org.json.JSONObject piecesConfig = config.getJSONObject("pieces");
                for (BlockPiece piece : BlockPiece.standardPieces()) {
                    if (piecesConfig.has(piece.getName())) {
                        org.json.JSONObject p = piecesConfig.getJSONObject(piece.getName());
                        if (p.has("tier")) {
                            piece.setTier(BlockPiece.Tier.valueOf(p.getString("tier")));
                        }
                        if (p.has("baseScore")) {
                            piece.setBaseScore(p.getInt("baseScore"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public int[] getBackgroundColors(int[] defaults) {
        try {
            org.json.JSONObject config = new org.json.JSONObject(getDevConfig());
            if (config.has("bgColors")) {
                org.json.JSONArray arr = config.getJSONArray("bgColors");
                int[] colors = new int[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    colors[i] = android.graphics.Color.parseColor(arr.getString(i));
                }
                return colors;
            }
        } catch (Exception ignored) {}
        return defaults;
    }

    public int getWaveColor(int defaultColor) {
        try {
            org.json.JSONObject config = new org.json.JSONObject(getDevConfig());
            if (config.has("waveColor")) {
                return android.graphics.Color.parseColor(config.getString("waveColor"));
            }
        } catch (Exception ignored) {}
        return defaultColor;
    }

    void setFirebaseStore(FirebaseStore firebaseStore) {
        this.firebaseStore = firebaseStore;
    }

    public void syncToFirebase() {
        if (firebaseStore != null) {
            Map<String, Object> devConfigMap = null;
            try {
                org.json.JSONObject json = new org.json.JSONObject(getDevConfig());
                devConfigMap = jsonToMap(json);
            } catch (Exception ignored) {}
            firebaseStore.saveSettings(getFxVolumePercent(), getMusicVolumePercent(), isHapticsEnabled(), getPlayerName(), devConfigMap);
        }
    }

    public Map<String, Object> jsonToMap(org.json.JSONObject json) throws org.json.JSONException {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof org.json.JSONObject) {
                value = jsonToMap((org.json.JSONObject) value);
            } else if (value instanceof org.json.JSONArray) {
                value = jsonToList((org.json.JSONArray) value);
            }
            map.put(key, value);
        }
        return map;
    }

    private List<Object> jsonToList(org.json.JSONArray array) throws org.json.JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof org.json.JSONObject) {
                value = jsonToMap((org.json.JSONObject) value);
            } else if (value instanceof org.json.JSONArray) {
                value = jsonToList((org.json.JSONArray) value);
            }
            list.add(value);
        }
        return list;
    }

    int getFxVolumePercent() {
        // Fallback to old "volume" key if "fx_volume" doesn't exist yet
        if (!preferences.contains(KEY_FX_VOLUME) && preferences.contains("volume")) {
            int oldVal = preferences.getInt("volume", DEFAULT_VOLUME);
            setFxVolumePercent(oldVal);
            return oldVal;
        }
        return preferences.getInt(KEY_FX_VOLUME, DEFAULT_VOLUME);
    }

    void setFxVolumePercent(int volume) {
        preferences.edit().putInt(KEY_FX_VOLUME, Math.max(0, Math.min(100, volume))).apply();
        syncToFirebase();
    }

    int getMusicVolumePercent() {
        return preferences.getInt(KEY_MUSIC_VOLUME, DEFAULT_VOLUME);
    }

    void setMusicVolumePercent(int volume) {
        preferences.edit().putInt(KEY_MUSIC_VOLUME, Math.max(0, Math.min(100, volume))).apply();
        syncToFirebase();
    }

    boolean isHapticsEnabled() {
        return preferences.getBoolean(KEY_HAPTICS, true);
    }

    void setHapticsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_HAPTICS, enabled).apply();
        syncToFirebase();
    }

    String getPlayerName() {
        String name = preferences.getString(KEY_PLAYER_NAME, DEFAULT_PLAYER_NAME);
        if (name == null || name.trim().length() == 0) {
            return DEFAULT_PLAYER_NAME;
        }
        return name.trim();
    }

    void setPlayerName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) {
            value = DEFAULT_PLAYER_NAME;
        }
        preferences.edit().putString(KEY_PLAYER_NAME, value).apply();
        syncToFirebase();
    }

    String getGameplayMusic() {
        return preferences.getString(KEY_GAMEPLAY_MUSIC, MUSIC_RANDOM);
    }

    void setGameplayMusic(String music) {
        preferences.edit().putString(KEY_GAMEPLAY_MUSIC, music).apply();
    }
}
