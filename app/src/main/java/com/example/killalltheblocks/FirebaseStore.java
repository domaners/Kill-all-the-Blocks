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
    private static final String NODES_LEADERBOARD = "leaderboard";

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

        Map<String, Object> scoreData = buildScoreMap(entry, user.getUid());

        database.child(NODES_SCORES).child(key).setValue(scoreData);
        
        // Also save to user's personal scores list
        database.child(NODES_USERS).child(user.getUid()).child(NODES_SCORES).child(key).setValue(true);
    }

    private Map<String, Object> buildScoreMap(ScoreEntry entry, String uid) {
        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("score", entry.getScore());
        scoreData.put("playerName", entry.getPlayerName());
        scoreData.put("finishedAtMillis", entry.getFinishedAtMillis());
        scoreData.put("durationMillis", entry.getDurationMillis());
        scoreData.put("uid", uid);
        return scoreData;
    }

    /**
     * Submits a score to Firebase. It only posts to the global leaderboard if it's a new personal best.
     * It then checks if the score is in the global top 10.
     */
    public void submitScore(final ScoreEntry entry, final OnScoreSubmittedListener listener) {
        final FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onResult(false, -1);
            return;
        }

        final DatabaseReference userRef = database.child(NODES_USERS).child(user.getUid());
        userRef.child("personalBest").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long currentBest = 0;
                boolean exists = snapshot.exists();
                if (exists) {
                    Long val = snapshot.getValue(Long.class);
                    if (val != null) currentBest = val;
                }

                if (entry.getScore() > currentBest || !exists) {
                    // New Personal Best
                    userRef.child("personalBest").setValue(entry.getScore());
                    
                    // Update global leaderboard with this player's highest score
                    database.child(NODES_LEADERBOARD).child(user.getUid()).setValue(buildScoreMap(entry, user.getUid()));
                    
                    pushScore(entry);
                    
                    // Check global rank
                    checkGlobalRank(entry.getScore(), listener);
                } else {
                    // Not a personal best, but we still log the score in their history
                    pushScore(entry);
                    if (listener != null) listener.onResult(false, -1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (listener != null) listener.onResult(false, -1);
            }
        });
    }

    private void checkGlobalRank(final int score, final OnScoreSubmittedListener listener) {
        database.child(NODES_LEADERBOARD).orderByChild("score").limitToLast(10).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Integer> topScores = new ArrayList<>();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    try {
                        Map<?, ?> data = (Map<?, ?>) postSnapshot.getValue();
                        if (data != null) {
                            Object s = data.get("score");
                            if (s instanceof Long) {
                                topScores.add(((Long) s).intValue());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing rank entry", e);
                    }
                }
                // Sort descending
                java.util.Collections.sort(topScores, java.util.Collections.reverseOrder());
                
                int rank = -1;
                for (int i = 0; i < topScores.size(); i++) {
                    if (score >= topScores.get(i)) {
                        rank = i + 1;
                        break;
                    }
                }
                
                if (rank == -1 && topScores.size() < 10) {
                    rank = topScores.size() + 1;
                }
                
                if (listener != null) listener.onResult(true, rank);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (listener != null) listener.onResult(true, -1);
            }
        });
    }

    public void saveSettings(int fxVolume, int musicVolume, boolean haptics, String playerName, Map<String, Object> devConfig) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("fxVolume", fxVolume);
        settings.put("musicVolume", musicVolume);
        settings.put("haptics", haptics);
        settings.put("playerName", playerName);
        if (devConfig != null) {
            settings.put("devConfig", devConfig);
        }

        database.child(NODES_USERS).child(user.getUid()).child(NODES_SETTINGS).setValue(settings);
    }

    public void fetchSettings(final OnSettingsFetchedListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onFetched(null);
            return;
        }

        database.child(NODES_USERS).child(user.getUid()).child(NODES_SETTINGS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    listener.onFetched((Map<String, Object>) snapshot.getValue());
                } else {
                    listener.onFetched(null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onFetched(null);
            }
        });
    }

    public interface OnSettingsFetchedListener {
        void onFetched(Map<String, Object> settings);
    }

    public void fetchGlobalScores(final OnScoresFetchedListener listener) {
        Query topScoresQuery = database.child(NODES_LEADERBOARD).orderByChild("score").limitToLast(10);
        topScoresQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ScoreEntry> scores = parseScores(snapshot);
                listener.onFetched(scores);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch scores", error.toException());
            }
        });
    }

    public void fetchUserScores(final OnScoresFetchedListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onFetched(new ArrayList<>());
            return;
        }

        Query userScoresQuery = database.child(NODES_SCORES)
                .orderByChild("uid")
                .equalTo(user.getUid());

        userScoresQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ScoreEntry> scores = parseScores(snapshot);
                // Sort manually because Firebase can only order by one field without complex indices
                java.util.Collections.sort(scores, (a, b) -> Integer.compare(b.getScore(), a.getScore()));
                if (scores.size() > 10) {
                    scores = scores.subList(0, 10);
                }
                listener.onFetched(scores);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch user scores", error.toException());
            }
        });
    }

    private List<ScoreEntry> parseScores(DataSnapshot snapshot) {
        List<ScoreEntry> scores = new ArrayList<>();
        for (DataSnapshot postSnapshot : snapshot.getChildren()) {
            try {
                Map<?, ?> data = (Map<?, ?>) postSnapshot.getValue();
                if (data != null) {
                    Long score = (Long) data.get("score");
                    Long finishedAt = (Long) data.get("finishedAtMillis");
                    Long duration = (Long) data.get("durationMillis");
                    String name = (String) data.get("playerName");
                    String uid = (String) data.get("uid");

                    if (score != null && finishedAt != null && duration != null) {
                        scores.add(new ScoreEntry(
                                score.intValue(),
                                finishedAt,
                                duration,
                                name != null ? name : "Unknown",
                                uid
                        ));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing score entry", e);
            }
        }
        // Reverse because limitToLast/Firebase order is usually ascending
        java.util.Collections.reverse(scores);
        return scores;
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
        Map<String, Object> devConfigMap = null;
        try {
            org.json.JSONObject json = new org.json.JSONObject(localSettingsStore.getDevConfig());
            devConfigMap = localSettingsStore.jsonToMap(json);
        } catch (Exception ignored) {}
        
        saveSettings(
                localSettingsStore.getFxVolumePercent(),
                localSettingsStore.getMusicVolumePercent(),
                localSettingsStore.isHapticsEnabled(),
                localSettingsStore.getPlayerName(),
                devConfigMap
        );

        // 3. (Optional) Could also fetch settings from Firebase if they exist for this user
        // but for now we prioritize local migration to cloud.
    }

    public interface OnScoresFetchedListener {
        void onFetched(List<ScoreEntry> scores);
    }

    public interface OnScoreSubmittedListener {
        void onResult(boolean isPersonalBest, int globalRank);
    }
}
