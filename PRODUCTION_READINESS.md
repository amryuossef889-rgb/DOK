# DOK Production Readiness

This gate is evidence-based. Passing JVM or Robolectric tests is not proof that Android hardware media pipelines are production-ready.

## Verified foundations
- Kotlin/Android/Compose project structure and Gradle build.
- Versioned project JSON, migration and atomic save.
- Multi-track timeline editing primitives.
- Shared render-plan abstraction for preview/export.
- H.264/AAC MediaCodec export path.
- Streaming audio/video decoder implementations.
- Parametric effects and .cube LUT parsing.
- Portrait/landscape editor UI.
- Keyboard/mouse command architecture.
- Keyframe evaluation, roll/slip/slide editing primitives, workspace state, media/node/audio-effect model foundations and unified input mapping.

## Release blockers
1. Real-device validation across supported Android API levels and hardware codecs.
2. Instrumented tests for decoder, encoder, muxer, AudioTrack and GPU paths.
3. Preview/export pixel and audio parity fixtures.
4. Complete GPU compositing for every declared transform/effect/transition/blend operation.
5. Persistent media pool, relink UX, proxy generation and cache invalidation.
6. Professional trim UI: ripple, roll, slip, slide, overwrite, insert and replace.
7. Keyframe editor and graph editor UI with persistence.
8. Color workspace, scopes and deterministic color management.
9. Professional audio mixer, EQ, dynamics, reverb, noise reduction and automation.
10. Executable node graph compositor, not only graph data structures.
11. Subtitle/caption import/export and text animation.
12. Render queue with cancellation, retry and resumable background jobs.
13. Autosave, recovery, project snapshots and corruption recovery.
14. Accessibility, crash diagnostics, performance/ANR testing and privacy-safe telemetry.
15. Release signing, R8 configuration, baseline profiles and store compliance.
16. Portrait, landscape, tablet, foldable, external-display, keyboard, mouse and stylus validation.

## Completion rule
A feature is complete only when its full path works on a supported Android device and survives edit -> save -> reopen -> preview -> export.
