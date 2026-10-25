# Kuro Canvas

**An Android-first and desktop creative workspace for manga, manhua, manhwa/webtoons, comics, illustration, animation, and integrated 2D/3D production.**

Kuro Canvas is not an image generator or a chatbot wrapped around a canvas. The project stores structured, editable data; Kuro is intended to operate native creative tools rather than flattening work into generated images.

## Current working foundation

- **Build:** Android debug APK and desktop target are verified by GitHub Actions CI.
- **Local projects:** JSON project loading/autosave with atomic-replacement writes on Android and desktop.
- **Editable canvas:** normalized brush-stroke geometry with color, width and layer references; typed operations with revision checks, audit events and undo.
- **Layers:** layer creation/settings, visibility, locking, opacity, selection and dependency-safe removal are wired into the drawing workspace.
- **Story canon and graph:** typed character/location/organization/object/event records, time-scoped facts, relationship edges and chapter events are stored as project data. Character profile snapshots resolve age, height, hair, clothing, status and other traits by chapter/branch; unknowns and conflicts stay visible.
- **Vault:** local file import, content hash, deduplication, metadata index and an editable license/attribution/provenance record. Imports default to UNREVIEWED; metadata entry does not independently verify rights.
- **Local model:** a downloadable Qwen3 0.6B LiteRT-LM starter model has resumable download, SHA-256 verification, on-device inference and streaming chat. No API key or remote endpoint is required; real-device performance and memory profiling still need validation.
- **Approved native actions:** Kuro can propose adding a storyboard panel, a typed story entity, or one time-scoped canon fact. A strict allowlist parser and the native operation broker validate the proposal; the user must approve before changes are saved.
- **Local-first:** project data and asset imports are stored locally. This prototype does not send prompts to a remote service.

## Product principles

- **Native creation, not flattened generation.** Drawings, characters, scenes and assets must remain editable.
- **AI is optional; creative tools are not.** Drawing, editing, saving, import/export and project management work without a model.
- **The project is the source of truth.** Conversation history alone is never canon.
- **Continuity is temporal.** Character facts can differ by chapter and timeline branch.
- **Inspectability and control.** Typed operations validate project identity/revision, record audit history and support undo where implemented.
- **Reuse before rebuild.** Mature libraries and interchange formats are preferred when platform support, quality and licensing fit.

## Roadmap still not implemented

This is an early working foundation, not the full creative studio. The full asset-management/publishing ecosystem, professional brush engine, dedicated anatomy/hair/clothing/pose engines, advanced timeline simulation, real 3D viewport/rigging, motion capture, lettering/export pipeline, voice, and broad native tool coverage are still incomplete. The local model and its narrow action path are integrated, but the starter model's quality and resource use still require representative device testing. UI status must never pretend an engine is integrated before it actually is.

## Kuro and native engines

Kuro is the optional creative companion and production operator. Today, chat can propose only a storyboard panel, a story entity, or a time-scoped canon fact; the user must approve each proposal, and the broker enforces project revisions and canon invariants. The wider native engines—professional brushes, character/body/hair/clothing/pose, scene/camera/lighting, 3D, animation, lettering and publishing—remain staged work, not implied by the chat interface. The local-model strategy is documented separately.

## Research and design

- [Open-source component catalogue](docs/research/open-source-component-catalog.md)
- [Motion capture and dance retargeting](docs/research/motion-capture-and-retargeting.md)
- [Kuro runtime and native creation](docs/architecture/0001-kuro-runtime-and-native-creation.md)
- [Dedicated local model and voice strategy](docs/architecture/0002-dedicated-model-and-voice-strategy.md)
- [Mobile workspace blueprint](docs/design/mobile-workspace-blueprint.md)
- [Foundation milestones](docs/implementation/foundation-milestones.md)

## Dependency and asset licensing

A public repository does not automatically grant redistribution rights. Review each code dependency, model weight, tokenizer, voice, font, brush, texture, sample project and dataset independently. Record exact versions, sources, license notices, modifications and provenance.
