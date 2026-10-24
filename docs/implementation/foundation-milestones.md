# Foundation milestones

The goal is a running vertical slice early—not a massive pile of unconnected packages. This sequence reuses mature tools, establishes contracts, and turns each integration into an executable test.

## M0 — Repository hygiene and dependency gates
- Document architecture, formats, and UI decisions.
- Add contribution, security, privacy, license, and asset-provenance policies.
- Add automated license/SBOM scanning (ScanCode + ORT evaluation) and CI metadata.
- Store model/asset provenance separately from software dependencies.
- Outcome: new components are not added invisibly.

## M1 — Android + desktop shell
- Scaffold Compose Multiplatform after verifying current stable plugin versions and supported targets.
- Implement design tokens, adaptive navigation, project picker, Create/Story/Vault areas, Kuro sheet, job status and inspectors.
- Stub Kuro visually, but do not fake tool activity or completed features.
- Outcome: both platform layouts compile and render through the same domain contracts.

## M2 — Project/data foundation
- Define versioned manifest, stable object IDs, SQLite schema, content-addressed assets, revision and audit log.
- Add migrations, auto-save, snapshots, recovery journal, safe import staging and export.
- Outcome: create/save/close/reopen/recover a project with integrity checks.

## M3 — Real 2D vertical slice
- Integrate a Skia-backed viewport and evaluate libmypaint for brush behavior.
- Implement stylus/touch input, editable strokes, layers, selection, zoom/pan, pressure/size/opacity, undo/redo.
- Import/export OpenRaster; save native stroke data and previews.
- Outcome: real editable art, not a static mock image.

## M4 — Native operations and Kuro runtime
- Define canonical operation schemas and broker.
- Add a deterministic example tool that makes a safe edit to native project data.
- Add validation, preview, approval, undo, transaction log and cancellation.
- Outcome: a tool call visibly changes real project objects and can be undone/replayed.

## M5 — Story graph and context
- Add Story, Arc, Chapter, Scene, Panel, Character, Relationship, Organization, Event, Timeline, Branch, Style and Asset objects.
- Implement canon state transitions, temporal state resolver and contradiction/unknown handling.
- Add text/full-text search first; embeddings and vector search remain optional.
- Outcome: a chapter/scene can retrieve relevant entities and correctly resolve a historical state without treating dialogue as canon automatically.

## M6 — Local model adapter
- Test llama.cpp on desktop and representative Android devices; test MLC LLM as an alternative, not a mandatory dependency.
- Add downloadable signed/hashed model manifest, license display, device memory estimates and cancel/resume.
- Add tool-use benchmark and schema-constrained outputs.
- Outcome: model can plan and call a whitelisted native tool; model may not directly write database/files.

## M7 — Speech and long-session behavior
- Prototype sherpa-onnx streaming ASR, VAD and TTS; compare whisper.cpp ASR where useful.
- Add push-to-talk, captions/transcript correction, interruption, mute/stop, session checkpointing and offline support.
- Outcome: sustained voice sessions do not lose task/project state and never imply speech recognition is guaranteed canon.

## M8 — Vault and assets
- Add character identity, outfits/variants, reusable asset IDs, relationships/dependencies, hashes, previews, tags, provenance and license status.
- Search the Vault before creating a duplicate. Save new approved reusable items automatically where configured.
- Outcome: one character can have many reusable clothing variations without becoming many unrelated identities.

## M9 — 3D, animation and camera pose
- Add Filament runtime and glTF/GLB import, previews, materials, camera and animation playback.
- Add optional Blender bridge for authoring workflows.
- Test MediaPipe for mobile pose capture and MMPose for desktop/offline workflows; retarget results to editable rig/keyframes.
- Outcome: imported/retargeted movement can be reviewed, edited and stored as project data; model and dataset licenses are recorded.

## M10 — Lettering, publishing and exchange
- Integrate HarfBuzz/FreeType/ICU as needed for complex scripts.
- Add semantic dialogue and bubble objects, reading order and manga/manhua/manhwa/webtoon/western profiles.
- Add OpenRaster, glTF, OpenTimelineIO, image/video export; evaluate PDF/print export libraries by license and format needs.
- Outcome: reproducible exports can be traced to a project revision and preset.

## M11 — Reliability and performance
- Stress tests for large graphs, thousands of assets, large vertical canvases, long transcripts and long jobs.
- Android low-memory/low-storage/thermal tests; offline restart and corrupted import tests.
- Fuzz file parsers, project migrations, archives and plugin boundaries.
- Test model failure, cancellation races, audio interruption, rollback and provider unload.
- Outcome: project remains recoverable and editable after routine failures.

## Definition of done for every component

A component is not complete just because its screen looks correct or a library has been added. It needs a data contract, UI states, persistence, permissions, dependencies, cancellation/failure recovery, undo/rollback where relevant, accessibility, performance tests, license review, and integration with Kuro's native operation broker.

## First end-to-end acceptance test

A blank project is created on Android; a user draws an editable stroke; it is saved, closed and reopened; a local Kuro model is loaded (or absent and the tools still function); a text/voice request proposes a native scene or simple canvas operation; the user previews and applies it; project revision/audit records the operation; undo returns to the previous valid state; the same project opens on desktop.
