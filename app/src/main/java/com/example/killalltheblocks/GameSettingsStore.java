package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

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

    private final SharedPreferences preferences;
    private FirebaseStore firebaseStore;

    GameSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void setFirebaseStore(FirebaseStore firebaseStore) {
        this.firebaseStore = firebaseStore;
    }

    private void syncToFirebase() {
        if (firebaseStore != null) {
            firebaseStore.saveSettings(getFxVolumePercent(), getMusicVolumePercent(), isHapticsEnabled(), getPlayerName());
        }
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
