# BrawlStarsAI — Autonomous Agent Architecture

## Goal

Evolve the current Android prototype into an on-device gameplay assistant with a closed perception → state estimation → planning → action → feedback loop. Maximum autonomy means the agent can make tactical decisions and execute approved in-game inputs without requiring confirmation for every action. The user remains in control through an always-available stop control and explicit Android permissions.

This document describes the target architecture. It does not claim that autonomous gameplay is implemented today.

## Current baseline (verified in the repository)

- ScreenCaptureService: MediaProjection capture and frame delivery.
- GameVisionAnalyzer: color-run heuristics for candidate health-bar-like regions.
- ML Kit OCR: extracts visible text.
- GameDecisionEngine: keyword-based mode/result hints.
- FloatingPanelService: overlay UI; its AI toggle currently changes UI state only.
- No trained detector, persistent world-state tracker, tactical planner, or game-input executor exists yet.

## Target architecture

    MediaProjection
         |
         v
    FrameSource -> FramePreprocessor
                         |
                         v
                  PerceptionPipeline
             /-----------|------------\
            v            v             v
      ObjectDetector   OCR Engine   HUD/Map Parser
             \-----------|------------/
                         v
                   StateEstimator
                         |
                         v
                   WorldStateStore
                         |
                         v
                   TacticalPlanner
                         |
                         v
                   ActionController
                         |
                         v
                 AndroidInputAdapter
                         |
                         v
                     Game screen
                         |
                    feedback loop

## Modules and contracts

| Module | Responsibility | Proposed contract |
|---|---|---|
| FrameSource | Own screen capture lifecycle and frame timestamps | Frame(timestamp, bitmap, rotation) |
| FramePreprocessor | Resize, crop HUD/arena, normalize | PreparedFrame |
| PerceptionPipeline | Run object, text, and HUD detectors | PerceptionSnapshot |
| ObjectDetector | Detect player, allies, enemies, projectiles, obstacles, bushes, objectives | Detection(label, box, confidence, trackId?) |
| HudParser | Read HP, ammo, super, timer, score, objective health | HudState |
| StateEstimator | Fuse detections over time and estimate motion | WorldState |
| WorldStateStore | Keep bounded recent history and last-known positions | StateHistory |
| TacticalPlanner | Choose the next tactical goal and action sequence | Plan(action, rationale, confidence, expiry) |
| ActionController | Validate, rate-limit, cancel, and dispatch actions | ActionResult |
| AndroidInputAdapter | Translate abstract actions to Android input | InputResult |
| AgentOrchestrator | Schedule the loop and handle lifecycle/errors | AgentState |
| AgentMonitor | Display state, confidence, last decision, stop control | UI state stream |

Keep Android APIs out of the planner and perception domain. The planner must be testable with recorded frames and synthetic world states.

## Decision loop

1. Capture a frame and attach a monotonic timestamp.
2. Run perception at a bounded rate; prioritize fast HUD/object updates over expensive secondary analysis.
3. Associate detections across frames and estimate velocity/uncertainty.
4. Update the world state and expire stale observations.
5. Select a mode-specific objective and score candidate actions.
6. Reject plans with stale state, low confidence, invalid geometry, or expired deadlines.
7. Execute one short action at a time; do not queue long blind sequences.
8. Observe the next frame and compare expected vs. actual outcome.
9. Re-plan continuously.

The planner should return a safe no-op/observe action when the scene is uncertain. Confidence must be represented numerically and propagated; a color heuristic must never be promoted to a confirmed player detection.

## Model strategy

- Start with a detector trained or fine-tuned on correctly labeled Brawl Stars screenshots. Define classes and annotation rules before training.
- Export to a mobile runtime such as TensorFlow Lite; benchmark model size, latency, and thermal load on the target phone.
- Use separate lightweight HUD/OCR parsing where it is more reliable than object detection.
- Track objects between detector runs to reduce inference cost.
- Version the model and labels together. Reject incompatible model metadata at startup.
- Keep all gameplay image processing on-device by default.

## Input and autonomy boundary

- Input execution is a separate adapter, not part of the planner.
- Android input requires a user-enabled, clearly disclosed mechanism and can be revoked in Android settings.
- Do not attempt to bypass Android security, game protections, anti-cheat, or other apps' permission boundaries.
- Provide a persistent stop/pause control, a foreground status indicator, and automatic stop on service loss, capture loss, app backgrounding where appropriate, or repeated action errors.
- Use short, cancellable actions and a configurable action-rate limit.
- Never execute an action from an untrusted or stale frame.

## Reliability and observability

- Every frame, state estimate, plan, and action result carries a timestamp.
- Record compact diagnostic events locally; avoid retaining raw screenshots by default.
- Expose: capture FPS, inference latency, detector confidence, dropped frames, state age, current objective, selected action, and stop reason.
- Add replay tests from labeled screenshots and recorded sequences.
- Add planner unit tests for low HP, ambiguous detections, stale state, objective urgency, and missing capture.
- Add an integration test that verifies the stop control cancels pending actions immediately.

## Suggested package layout

    com.brawlstarsai/
      capture/       FrameSource, ScreenCaptureService
      perception/    FramePreprocessor, PerceptionPipeline, ObjectDetector, HudParser
      model/         Detection, HudState, PerceptionSnapshot, WorldState
      tracking/      StateEstimator, TrackManager, WorldStateStore
      planning/      TacticalPlanner, ModeStrategy, Plan, Action
      control/       AgentOrchestrator, ActionController, ActionValidator
      input/         AndroidInputAdapter, input permission/service integration
      monitoring/    AgentMonitor, diagnostics
      ui/            MainActivity, FloatingPanelService
      legacy/        Existing heuristic analyzers during migration

## Implementation phases

### Phase 0 — Architecture and contracts
- Keep current app working.
- Introduce domain data classes and interfaces.
- Separate capture, perception, planning, and input packages.
- Make the overlay report real agent state rather than a cosmetic ON/OFF flag.

### Phase 1 — Reliable perception
- Build a labeled screenshot dataset.
- Train/evaluate player, ally, enemy, projectile, obstacle, bush, and objective detection.
- Parse health/ammo/super/timer and validate on multiple maps and modes.
- Add temporal tracking and confidence calibration.

### Phase 2 — State estimation
- Fuse detections and HUD data into a timestamped world state.
- Track movement and uncertainty.
- Add replay-based tests and stale-data handling.

### Phase 3 — Tactical planner
- Implement separate strategies for each supported game mode.
- Score positioning, objective progress, threat, health, ammo, cooldowns, and team context.
- Start in recommendation/simulation mode and compare plans against recorded matches.

### Phase 4 — Controlled action execution
- Add the Android input adapter only after perception and planning are validated.
- Require explicit user opt-in and show a persistent pause/stop control.
- Begin with short, low-risk actions; add action verification and immediate cancellation.

### Phase 5 — On-device optimization
- Benchmark on the target device, tune inference cadence, quantize models if accuracy permits, and add thermal/memory safeguards.

## Acceptance criteria for autonomous operation

The system is not considered autonomous merely because the overlay says ON. It must:
- identify relevant entities with measured precision/recall on a held-out screenshot set;
- maintain stable tracks across consecutive frames;
- produce mode-aware plans with confidence and explanations;
- execute only fresh, validated actions through an explicit Android input adapter;
- verify action outcomes from subsequent frames;
- stop immediately when paused, capture is lost, or state confidence falls below configured thresholds.