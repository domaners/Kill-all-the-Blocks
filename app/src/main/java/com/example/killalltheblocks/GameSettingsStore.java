package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

final class GameSettingsStore {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_PLAYER_NAME = "player_name";
    private static final int DEFAULT_VOLUME = 70;
    private static final String DEFAULT_PLAYER_NAME = "Player 1";

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
            firebaseStore.saveSettings(getVolumePercent(), isHapticsEnabled(), getPlayerName());
        }
    }

    int getVolumePercent() {
        return preferences.getInt(KEY_VOLUME, DEFAULT_VOLUME);
    }

    void setVolumePercent(int volume) {
        preferences.edit().putInt(KEY_VOLUME, Math.max(0, Math.min(100, volume))).apply();
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
}
