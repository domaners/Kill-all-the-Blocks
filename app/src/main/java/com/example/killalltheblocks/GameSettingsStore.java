package com.example.killalltheblocks;

import android.content.Context;
import android.content.SharedPreferences;

final class GameSettingsStore {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_HAPTICS = "haptics";
    private static final int DEFAULT_VOLUME = 70;

    private final SharedPreferences preferences;

    GameSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    int getVolumePercent() {
        return preferences.getInt(KEY_VOLUME, DEFAULT_VOLUME);
    }

    void setVolumePercent(int volume) {
        preferences.edit().putInt(KEY_VOLUME, Math.max(0, Math.min(100, volume))).apply();
    }

    boolean isHapticsEnabled() {
        return preferences.getBoolean(KEY_HAPTICS, true);
    }

    void setHapticsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_HAPTICS, enabled).apply();
    }
}
