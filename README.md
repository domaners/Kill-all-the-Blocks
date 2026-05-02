# Kill All the Blocks

Kill All the Blocks is a native Android puzzle game inspired by block-placement games. Three random Tetris-style pieces appear beside an 8x8 board. Select a piece, tap a grid position to place it, and clear complete rows or columns to grow your score.

The app stores the top 10 completed-game scores with completion date/time and game duration using Android `SharedPreferences`, so the leaderboard persists after the app closes.

## Build

This repository is an Android Gradle project:

```sh
./gradlew assembleDebug
```

If a Gradle wrapper is not available in your checkout, use Android Studio or a local Gradle installation with Android Gradle Plugin support.
