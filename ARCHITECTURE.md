# Dok Editor Architecture

Dok Editor is a professional multi-track video editor for gaming content creators on Android, inspired by DaVinci Resolve's modular architecture.

## System Topology

```
Workspace
   │
   ├─► Input Devices: Touch, Keyboard, Mouse
   ▼
Input Abstraction Layer
   ▼
Command System (Action Dispatcher)
   │
   ├─► Timeline Engine
   ├─► Viewer Engine
   └─► [Extension Point] Nodes Engine (Fusion-style node graph compositing - Future V2)
   ▼
Core Engine
   │
   ├─► Video Engine (Sequential frame-accurate decoding, compositing)
   ├─► Audio Engine (Streaming PCM decoder, unity-gain-center pan mixer, soft-knee limiter)
   ├─► Color Engine (Parametric color grading: brightness, contrast, saturation, temperature, vignette, 3D LUT .cube)
   ├─► [Extension Point] Fusion Engine (Node graph compositing - Future V2)
   └─► [Extension Point] AI Engine (Gaming auto-highlight / smart cut - Future V2)
   ▼
Render Engine (TimelineRenderPlan)
   │
   ├─► Shared Preview Pipeline (Surface / GL / AudioTrack synced to shared clock)
   └─► Shared Deliver / Export Pipeline (MediaCodec H.264/AAC foreground service + MediaStore)
```

## Input Abstraction & Command System

- **Unified Command Dispatcher**: All user interactions (touch gestures, buttons, physical keyboard shortcuts, mouse clicks/scroll/drag) map to strongly-typed `EditorCommand` intents.
- **Identical Behavior**: Pressing `Space` on an external keyboard or tapping the preview Play button dispatches the exact same `EditorCommand.TogglePlayPause`. Pressing `Ctrl+B` or tapping "Split" dispatches `EditorCommand.SplitClipAtPlayhead`.

## Core Engine & Render Pipeline

- **Single Source of Truth (`TimelineRenderPlan`)**: Preview rendering and final export rendering share the exact same compositing and mixing pipeline. Divergence between preview and export is eliminated by construction.
- **Audio Pipeline**: Real streaming PCM decoding with absolute-sample-position resampling, unity-gain-center pan law, dB gain stages, fade envelopes, and an 80-sample soft-knee peak limiter. No synthetic sine-wave stand-ins.
- **Video Pipeline**: Sequential frame-accurate `MediaCodec` decoding with surface recycling, orientation matrix handling, and GL parametric shaders. No non-deterministic `MediaMetadataRetriever.getFrameAtTime`.

## Future Extensions (V2 Design)

- **Effects Architecture**: `Effect` is defined as a sealed interface hierarchy:
  - `ParametricEffect`: Zoom, Shake, RGB Split, Glitch, Flash, Vignette, ColorGrading, Lut3D.
  - `NodeGraphEffect` (Future V2): Nodes Engine extension point allowing arbitrary DAG-based Fusion nodes without modifying or breaking any V1 parametric effects.
- **AI Engine** (Future V2): Dedicated hook into clip analysis for automated kill-feed / highlight detection.
