# Kuro Canvas

**A persistent creative-world workspace for manga, manhua, manhwa/webtoons, comics, illustration, animation, and integrated 2D/3D production.**

Kuro Canvas is not just a canvas, an image generator, or a chatbot. The canvas is one view of a project whose story, world, characters, timeline, assets, scenes, panels, styles, and production state remain structured and editable.

## Core principles

- **Native creation, not flattened generation.** Kuro operates real editable engines and project objects.
- **AI is optional; creative tools are not.** Drawing, editing, saving, import/export and project management must work without a model.
- **The project is the source of truth.** Conversation history alone is never canon.
- **Continuity is temporal.** Character, relationship, organization, location, prop and event states resolve by story time and branch.
- **Inspectability and control.** Kuro proposes typed operations; the runtime validates permissions and dependencies, previews changes when needed, and supports undo/rollback.
- **Local-first and user-owned.** Projects work offline, recover after interruption, and don't require users to bring their own model API keys.
- **Reuse before rebuild.** Mature libraries and interchange formats are preferred when platform support, quality, and licensing fit.

## Kuro

Kuro is the persistent creative companion and production operator. A model can help interpret intent and propose structured tool calls, but native Kuro Canvas engines do the creation: document/layers/brushes, character/body/hair/clothing/pose, scene/panel/camera/lighting, 2D/3D, timeline, color, lettering, assets and publishing.

## Start here

- [Open-source component catalogue](docs/research/open-source-component-catalog.md)
- [Motion capture and dance retargeting](docs/research/motion-capture-and-retargeting.md)
- [Kuro runtime and native creation](docs/architecture/0001-kuro-runtime-and-native-creation.md)
- [Dedicated local model and voice strategy](docs/architecture/0002-dedicated-model-and-voice-strategy.md)
- [Mobile workspace blueprint](docs/design/mobile-workspace-blueprint.md)
- [Foundation milestones](docs/implementation/foundation-milestones.md)

## Current status

The repository now has a typed native project-operation broker, revision/audit/undo support for storyboard panels, a cross-platform JSON project codec, and app-private autosave/load wiring for Android and desktop. Storage writes use a temporary file and atomic replacement where the filesystem supports it. The current project model persists storyboard panels and its audit log; canvas strokes, full asset Vault, story/character/timeline engines, 3D, motion capture, and local-model inference remain unfinished. UI status must never pretend an engine is integrated before it actually is.

## Dependency and asset licensing

A public repository does not automatically grant redistribution rights. Review each code dependency, model weight, tokenizer, voice, font, brush, texture, sample project and dataset independently. Record exact versions, sources, license notices, modifications and provenance. Do not adopt components whose platform support or licensing is incompatible with Kuro Canvas.
