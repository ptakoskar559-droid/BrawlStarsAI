# BrawlStarsAI

Android-first, human-in-the-loop assistant prototype for Brawl Stars.

## Current status — 0.1.0

This repository contains an Android Studio project using Kotlin and Jetpack Compose.

The first screen provides:
- a manual game-state observation field,
- a placeholder analysis step,
- a command preparation and explicit approval flow,
- a basic in-memory decision log.

## Important limitations

This is a UI scaffold, not a functioning game-playing AI yet. It does not capture the game screen, identify game entities, control touch input, or train a model. No game actions are automated.

## Build

Open the repository in Android Studio, allow Gradle sync, then run the app configuration on an Android device or emulator.

Requirements:
- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.7.3

## Planned milestones

1. Persistent local experience log.
2. User-approved screen capture using Android MediaProjection.
3. Visual recognition and structured game-state extraction.
4. Tactical recommendation engine with confidence and explanation.
5. Command approval workflow and safe, user-controlled execution options.
6. Evaluation on recorded gameplay and iterative model improvement.
