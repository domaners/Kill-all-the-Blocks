package com.example.killalltheblocks;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseStore {
    private static final String TAG = "FirebaseStore";
    private static final String NODES_SCORES = "scores";
    private static final String NODES_USERS = "users";
    private static final String NODES_SETTINGS = "settings";

    private final DatabaseReference database;
    private final FirebaseAuth auth;

    public FirebaseStore() {
        database = FirebaseDatabase.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
    }

    public void pushScore(ScoreEntry entry) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String key = database.child(NODES_SCORES).push().getKey();
        if (key == null) return;

        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("score", entry.getScore());
        scoreData.put("playerName", entry.getPlayerName());
        scoreData.put("finishedAtMillis", entry.getFinishedAtMillis());
        scoreData.put("durationMillis", entry.getDurationMillis());
        scoreData.put("uid", user.getUid());

        database.child(NODES_SCORES).child(key).setValue(scoreData);
        
        // Also save to user's personal scores
        database.child(NODES_USERS).child(user.getUid()).child(NODES_SCORES).child(key).setValue(true);
    }

    public void saveSettings(int volume, boolean haptics, String playerName) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("volume", volume);
        settings.put("haptics", haptics);
        settings.put("playerName", playerName);

        database.child(NODES_USERS).child(user.getUid()).child(NODES_SETTINGS).setValue(settings);
    }

    public void fetchGlobalScores(final OnScoresFetchedListener listener) {
        Query topScoresQuery = database.child(NODES_SCORES).orderByChild("score").limitToLast(10);
        topScoresQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ScoreEntry> scores = new ArrayList<>();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    Map<String, Object> data = (Map<String, Object>) postSnapshot.getValue();
                    if (data != null) {
                        scores.add(new ScoreEntry(
                                ((Long) data.get("score")).intValue(),
                                (Long) data.get("finishedAtMillis"),
                                (Long) data.get("durationMillis"),
                                (String) data.get("playerName")
                        ));
                    }
                }
                // Reverse because limitToLast gives ascending order
                java.util.Collections.reverse(scores);
                listener.onFetched(scores);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch scores", error.toException());
            }
        });
    }

    public void syncOnLogin(ScoreStore localScoreStore, GameSettingsStore localSettingsStore) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // 1. Push local scores to Firebase
        List<ScoreEntry> localScores = localScoreStore.loadTopScores();
        for (ScoreEntry entry : localScores) {
            pushScore(entry);
        }

        // 2. Push local settings to Firebase
        saveSettings(
                localSettingsStore.getVolumePercent(),
                localSettingsStore.isHapticsEnabled(),
                localSettingsStore.getPlayerName()
        );

        // 3. (Optional) Could also fetch settings from Firebase if they exist for this user
        // but for now we prioritize local migration to cloud.
    }

    public interface OnScoresFetchedListener {
        void onFetched(List<ScoreEntry> scores);
    }
}
