# Architecture 0001: Kuro runtime and native creation

Status: proposed baseline, subject to a proof-of-fit prototype.

## One rule

**The model proposes; Kuro's runtime validates; native engines create.** The model must not paint directly into a flattened bitmap or silently mutate project truth. Its output is a plan made of typed operations.

## Runtime flow

User input (text, voice, touch, stylus, imported media)
-> session manager
-> context compiler
-> intent/task router
-> model adapter (optional)
-> typed Kuro plan
-> permission + schema + dependency validation
-> preview / approval when required
-> operation broker
-> native creative engines
-> validator
-> project revision + event log
-> live preview and explanation.

If no model is loaded, manual editing, drawing, saving, importing and exporting continue to work.

## Layer boundaries

### 1. User interface
Compose Multiplatform is the first prototype target for Android and desktop. UI state is a projection of project and job state, not the source of truth. Layout adapts to touch, stylus, keyboard, pointer, foldables, tablets and desktop windows.

### 2. Application services
Project lifecycle, commands, jobs, imports/exports, autosave, undo, search, Vault, model lifecycle, voice sessions, notifications and user preferences.

### 3. Domain core
Story/world/character/organization/event/relationship/timeline/canon/style/scene/panel/lettering/asset/production objects. This layer is independent of UI, model provider and rendering backend.

### 4. Native creative engines
Document/layers/brushes; vector and raster paths; transform/masks/compositing; character/body/hair/clothing/pose/expression; scene/panel/camera/light; 3D scene/rig/animation; color; text/lettering; media render/export.

### 5. Adapters
Skia, libmypaint, Filament, glTF, OpenRaster, OpenTimelineIO, image/video codecs, speech engines, model inference, and optional external authoring tools.

### 6. Storage
SQLite metadata, content-addressed assets, versioned manifests, revision/event journal, caches and thumbnails. Large binary payloads are not copied into the database on every edit.

## Project model

Important entities have immutable IDs and versioned state. Suggested root domains:

- Project, Workspace, Story, Arc, Chapter, Scene, Panel, Dialogue
- World, Location, Organization, Character, Relationship, Event, Timeline, Branch
- Style, StyleVersion, StyleRule, ColorPalette
- Asset, AssetVariant, AssetDependency, AssetProvenance, VaultCollection
- ProductionTask, ExportPreset, ResearchRecord, SourceEvidence
- KuroSession, KuroJob, Operation, OperationResult, AuditEvent

Each story object can have time-scoped state. Store event/state transitions and resolve the state for a requested story moment. Do not overwrite historical facts when a character changes age, clothing, hair, location, alignment or alive/dead status.

Canon states should distinguish Unknown, Observation, Hypothesis, Idea, Proposal, Draft, Approved, Canon, Deprecated and Contradicted. Conversation text alone never becomes canon. The user can explicitly approve a proposal; contradictions create a visible question rather than an invented answer.

## Typed operation model

Each operation must include:
- operation ID and schema version
- project ID and target object IDs
- declared read/write scope
- arguments and preconditions
- initiating actor (user, Kuro, engine, importer, plugin)
- reason/source and dependency IDs
- permission required
- reversibility strategy
- preview renderer where applicable
- expected postconditions and validation policy

Execution lifecycle:
1. Parse operation; reject malformed or unknown schema.
2. Resolve targets and current versions.
3. Validate permissions and preconditions.
4. Show preview for consequential/destructive/ambiguous changes.
5. Apply changes in a transaction or reversible command.
6. Run invariants and engine validators.
7. Commit revision and audit event only after validation.
8. Update UI; preserve rollback/undo information.
9. If any step fails, leave the previous valid state intact.

The model never receives raw database write permissions or arbitrary shell access. Tool results are returned as structured data, not instructions to trust.

## Context compiler

Do not place the entire story history in the prompt. Construct a bounded context from:
1. current task and explicit user intent
2. current chapter/scene/panel selection
3. relevant live state from the timeline resolver
4. relevant project graph neighbors (people, organizations, events, relationships, location)
5. approved canon and recent changes
6. required asset/style references and provenance
7. unresolved contradictions or unknowns
8. compact summaries linked to source object IDs and versions.

Retrieval priority is explicit IDs and temporal/graph queries first, keyword/full-text lookup second, semantic retrieval when an embedding model is configured. Every retrieved fact should retain a pointer to its object/revision/source. Summaries are cacheable derived data and must be regenerable from the project.

## Long-running jobs

Job state: queued, planning, awaiting-approval, running, paused, cancelling, completed, failed, cancelled. Jobs include progress, last checkpoint, dependencies, inputs, outputs, errors, model/runtime, usage when relevant, and operation log. The user can inspect, interrupt, pause or cancel. A long job may not continue mutating the project after cancellation without an explicit new action.

## Transaction and undo semantics

All meaningful changes go through the command/operation layer. Use before/after states or inverse commands for undo; snapshots alone are not enough for every large drawing operation. Drawing strokes can be batched, but remain editable and journaled. Imported files are copied into a staging area, validated, then committed atomically.

## Project storage

- Versioned manifest containing project metadata and format version.
- SQLite for graph, indexes, revisions, jobs, provenance and settings scoped to project.
- Content-addressed assets with hashes, MIME/type detection, dimensions, license metadata and dependency links.
- Write-ahead journal, periodic checkpoints, atomic rename/swap, crash recovery and migration tests.
- Native Kuro project is authoritative; interchange formats are adapters.
- Imports treat archives and plugins as untrusted input.

## Invariants

- No unapproved proposal silently becomes canon.
- No stale/historical state is used when a current state is requested.
- No AI job can write outside the authorized project scope.
- No destructive change without undo/recovery plan.
- Every retained asset has provenance and a stable identity.
- No generated art is represented as editable native work unless real native objects were actually created.
- If a required fact is unknown, mark it unknown instead of guessing.
- Every export can record the project revision and export settings that produced it.
