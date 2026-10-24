# Open-source component catalogue

Research snapshot: 2026-10-24. This is a practical shortlist, not a claim that one list can cover every future requirement. Each candidate must be pinned to a version and reviewed before dependency adoption. A GitHub repository being public does not automatically mean code, weights, fonts, sample projects, or bundled assets may be redistributed under Kuro Canvas's intended license.

## Adoption rules

- Prefer embeddable, maintained libraries and stable interchange formats over copying whole applications.
- Track code, model weights, tokenizer files, fonts, example projects, textures, brushes, plugins, and datasets as separate license/provenance items.
- Use compatibility prototypes for any renderer, database, inference backend, or UI toolkit whose mobile support/performance has not been measured.
- Put candidates in the build only after a license review and a reproducible test. Record the exact version, commit, origin, license, modifications, and notices in THIRD_PARTY_NOTICES and an SBOM.
- Keep feature packages replaceable behind Kuro-owned interfaces. Do not make one vendor/model/API the source of truth.

## Recommended baseline and alternatives

| Subsystem | Primary candidate | Alternatives / references | Intended role and caveats |
|---|---|---|---|
| Shared Android + desktop UI | [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) | [Qt Declarative / Qt Quick](https://github.com/qt/qtdeclarative), [Flutter](https://github.com/flutter/flutter) | Kotlin UI shared across Android, Windows, macOS, Linux. Prototype stylus input, docking, huge scrolling workspace, text shaping, desktop window behavior, and accessibility before locking the stack. |
| Core language/data contracts | Kotlin Multiplatform | Rust for portable native hot paths if measurement justifies it | Keep project data and operation schemas independent from UI components. Avoid adding Rust just for fashion. |
| 2D rendering | [Skia](https://github.com/google/skia) | [Vello](https://github.com/linebender/vello) | Skia is a mature portable 2D graphics library with permissive BSD licensing. Vello offers a Rust GPU rendering path; assess package maturity, device compatibility, and CPU fallback first. Neither alone is a full painting application. |
| Brush engine | [libmypaint](https://github.com/mypaint/libmypaint) | Krita brush engine as a reference, subject to license and integration review | Reuse a brush-engine component, not a claim of instant parity with Krita. Validate pressure curves, tilt, eraser, stylus latency, Android memory, and GPU/CPU compositing. |
| Reference painting app | [Krita](https://github.com/KDE/krita) | MyPaint, GIMP | Use as a feature and file-compatibility reference; evaluate GPL-3.0 obligations and the coupling of its Qt/C++ application architecture before attempting to embed or fork its internals. |
| 2D animation reference | [OpenToonz](https://github.com/opentoonz/opentoonz) | [Pixelorama](https://github.com/Orama-Interactive/Pixelorama), [LibreSprite](https://github.com/LibreSprite/LibreSprite) | Study mature timeline, onion-skin, exposure-sheet, and export workflows. OpenToonz has mixed per-directory/third-party licensing; check each dependency. Pixelorama is MIT and useful for pixel/2D animation, but is not a complete manga painting engine. |
| 3D runtime | [Filament](https://github.com/google/filament) | Godot, Open3D | Filament provides real-time PBR rendering across Android and desktop targets. It is a renderer, not a modeling/rigging suite. |
| 3D interchange | [glTF 2.0](https://github.com/KhronosGroup/glTF) / GLB | [OpenUSD](https://github.com/PixarAnimationStudios/OpenUSD), FBX import via a separately reviewed adapter | Use glTF/GLB as the main runtime asset format for mesh, materials, skeletons, and animation. Keep source/project data and import/export provenance intact. |
| 3D authoring bridge | [Blender](https://github.com/blender/blender) | Godot tools, other user-installed DCC tools | Prefer an optional external-process bridge and interchange files for advanced modeling/rigging. Do not promise Blender's entire feature set inside the mobile app. Blender is GPL-3.0; redistribution/linkage must be reviewed. |
| Camera-based pose/motion capture | [MediaPipe](https://github.com/google-ai-edge/mediapipe) | [MMPose](https://github.com/open-mmlab/mmpose), [VideoPose3D](https://github.com/facebookresearch/VideoPose3D) | Explore body/hand/face landmarks from user-provided footage and convert them into editable keyframes after review. Use MediaPipe for a mobile-oriented prototype and MMPose for configurable desktop/offline research. Verify model/data license, commercial-use terms, camera consent, and per-model redistribution terms. Do not assume a landmark track equals a production-ready rig animation. |
| Pose license warning | Avoid adopting [OpenPose](https://github.com/CMU-Perceptual-Computing-Lab/openpose) without a different explicit license | Choose permissively licensed alternatives | The commonly published OpenPose license is limited to non-commercial research. Treat it as unsuitable for a general redistributable product unless separately licensed. |
| Timeline interchange | [OpenTimelineIO](https://github.com/AcademySoftwareFoundation/OpenTimelineIO) | Kuro's own semantic story/animation timeline | Use OTIO for editorial interchange; it does not replace the character/world/story timeline or animation scene model. |
| Color management | [Little CMS](https://github.com/mm2/Little-CMS) | [OpenColorIO](https://github.com/AcademySoftwareFoundation/OpenColorIO) | Little CMS is a compact ICC color-management engine; OCIO is useful for VFX/color-pipeline workflows. Define a documented color pipeline and test screen/print exports separately. |
| Image I/O | [OpenImageIO](https://github.com/AcademySoftwareFoundation/OpenImageIO) | [libheif](https://github.com/strukturag/libheif), [LibRaw](https://github.com/LibRaw/LibRaw), platform codecs | Use format-specific adapters. Check build size, platform support, codecs, and dependency licensing/security. |
| Video/audio/export | [FFmpeg](https://github.com/FFmpeg/FFmpeg) | platform media APIs | FFmpeg supports a wide range of audio/video containers and processing. The effective license depends on build flags and linked components; keep the exact build configuration and notices. |
| Layered image interchange | [OpenRaster specification](https://www.openraster.org/) | PNG, PSD, SVG, TIFF, KRA adapters after review | Use ORA for basic layered-raster exchange; it is not a complete native format for vector, 3D, semantic lettering, animation, or story metadata. |
| 3D texture packaging | [KTX-Software](https://github.com/KhronosGroup/KTX-Software) | PNG, WebP, EXR via adapters | Useful compressed GPU texture formats for 3D assets, with target-device support tests. |
| Font and glyph shaping | [HarfBuzz](https://github.com/harfbuzz/harfbuzz) + [FreeType](https://github.com/freetype/freetype) | platform font APIs | Needed for reliable glyph shaping, kerning, OpenType, comic lettering, Japanese vertical text, and mixed-script rendering. Check font licenses independently. |
| Unicode / localization | [ICU](https://github.com/unicode-org/icu) | platform locale APIs | Use for locale-aware segmentation, bidi behavior, normalization, date/number rules, and robust multilingual text. Test Bangla, English, Japanese, Korean, Chinese, Arabic/RTL, and vertical writing. |
| Local project database | [SQLite](https://github.com/sqlite/sqlite) | platform database APIs | Primary local store for project metadata, revisions, indexes, jobs, and audit events. Keep binary/large assets in content-addressed files rather than huge DB blobs by default. SQLite licensing is unusually permissive/public-domain; verify bundled extensions separately. |
| Structured project search | SQLite FTS5 initially | [sqlite-vec](https://github.com/asg017/sqlite-vec) as a later optional adapter | Start with exact IDs, relational queries, tags, and full-text search. sqlite-vec is pre-v1 in its current project docs; do not make it a critical dependency before its API/stability and mobile behavior are tested. Embedding models are separately licensed. |
| Collaboration data types | [Yjs](https://github.com/yjs/yjs) or [Automerge](https://github.com/automerge/automerge) | server sync only if needed | Add CRDTs only when collaboration and offline merge are real requirements. They do not automatically resolve semantic canon conflicts or merge binary art files. Kuro needs domain-specific conflict review. |
| Local language-model runtime | [llama.cpp](https://github.com/ggml-org/llama.cpp) | [MLC LLM](https://github.com/mlc-ai/mlc-llm), ONNX Runtime GenAI | llama.cpp offers GGUF inference, OpenAI-compatible endpoints, function calling, and constrained JSON. MLC LLM is an alternative deployment engine with Android acceleration paths. Benchmark per device; do not select model weights until tool-use tests and license checks pass. |
| Voice / speech pipeline | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) for ASR | sherpa-onnx covers offline streaming ASR, TTS, VAD, diarization and other speech tasks on Android/desktop. Voice/model files have separate licenses. whisper.cpp is a strong cross-platform ASR option but is not the complete voice stack. |
| AI tool protocol | Kuro-owned typed operation API | [Model Context Protocol servers](https://github.com/modelcontextprotocol/servers) for external connectors | Native canvas tools should use deterministic typed APIs under an authorization broker. MCP is useful for optional integrations; its reference servers are examples, not turnkey production security. |
| Plugin isolation | Kuro plugin API + capability manifests | [Wasmtime](https://github.com/bytecodealliance/wasmtime), [Wasm3](https://github.com/wasm3/wasm3) | Start with a narrow, permissioned plugin schema. Evaluate WebAssembly runtimes per platform; apply filesystem/network/CPU/memory limits. A sandbox runtime alone does not make an unsafe plugin safe. |
| Vector/research backend (optional scale-out) | Embedded local search first | [Qdrant](https://github.com/qdrant/qdrant), [pgvector](https://github.com/pgvector/pgvector), [LanceDB](https://github.com/lancedb/lancedb) | Server/large-library options only if measured needs justify them. Do not force a separate server into the first offline Android build. |
| License scanning and SBOM | [ScanCode Toolkit](https://github.com/aboutcode-org/scancode-toolkit) + [OSS Review Toolkit](https://github.com/oss-review-toolkit/ort) | [Syft](https://github.com/anchore/syft), [Grype](https://github.com/anchore/grype) | Automate component license/provenance discovery, SBOM generation, and vulnerability review. Scanner findings still require human review, particularly for model weights, fonts and artwork. |
| Design-system reference | [Penpot](https://github.com/penpot/penpot) | Compose previews, Figma for reference only | Use open design tooling to maintain tokens, components, layout samples and specs. Do not embed a design tool inside Kuro Canvas. |
| PDF export/viewing | Evaluate a dedicated library per exact need | [MuPDF](https://github.com/ArtifexSoftware/mupdf), platform APIs | MuPDF's public code is AGPL-3.0 unless commercially licensed; do not adopt it casually. PDF export and reading are different functions. |
| Archive/project packaging | Standard ZIP/ZIP64 via a small audited dependency | [KArchive](https://github.com/KDE/karchive) if Qt is already used | Use a versioned project manifest and safe path handling. Avoid extracting archive paths outside project directories; validate sizes and checksums before import. |

## Kuro-specific systems that are not solved by libraries

These should be written in the Kuro repository and tested as domain logic, not delegated to an LLM:

1. Project graph and canonical object IDs.
2. Temporal state resolver for story time, flashbacks, branches and historical versions.
3. Canon status and approval transitions.
4. Story/world/character/relationship/organization/event engines.
5. Scene/panel/lettering data models and manga/manhua/manhwa/webtoon/western-comic profiles.
6. Style DNA, scopes, versions, visual consistency auditing and reference provenance.
7. Vault asset identity, variants, dependencies, provenance and reuse-before-recreate lookup.
8. Context compiler, retrieval policy, contradiction detection and memory summarization.
9. Transactional native operation broker, previews, permissions, undo/redo and audit log.
10. Job queue with checkpoints, pause/cancel/resume, validation and failure recovery.
11. Kuro's persistent identity, voice-session handling, activity states and mobile presence.
12. Project format, migrations, autosave, recovery journal, snapshots and reproducible exports.
13. Accessibility, adaptive layouts, low-memory policies and performance budgets.
14. Community asset manifest, license allowlist, attribution and source lineage.

## Initial architecture decisions

- UI: Compose Multiplatform as the first prototype; build a proof-of-fit test for stylus, drawing viewport, docking/adaptive layout, typography and desktop shortcuts before freezing the decision.
- Project truth: SQLite plus content-addressed assets and a versioned project manifest.
- AI: dedicated local-model adapter, initially test llama.cpp and an MLC alternative; the app must work without a model.
- 2D: prototype Skia + brush-engine adapter and preserve ORA interoperability; do not claim it immediately equals a mature painting application's feature set.
- 3D: Filament + glTF/GLB runtime; Blender remains an optional authoring bridge.
- Speech: sherpa-onnx proof of concept for streaming, interruption and offline operation.
- Motion capture: MediaPipe mobile prototype; MMPose desktop pipeline after accuracy/weight licensing review.
- Interchange: OpenRaster, glTF/GLB, OpenTimelineIO, standard image/media formats.
- Licensing: ScanCode + ORT in CI before third-party distribution.

## Decision gates before adopting a component

Every dependency needs: platform support, version pin, active maintenance snapshot, license and notices, transitive dependencies, model/data licenses where relevant, package footprint, startup/memory/performance results, security review, supported import/export formats, failure behavior, accessibility impact, and a named replacement path.
