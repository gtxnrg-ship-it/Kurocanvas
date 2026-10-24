# Mobile workspace blueprint

Status: first interaction design. This describes behavior and layout contracts before styling is implemented.

## Visual direction

Professional, dark creative studio; restrained galaxy atmosphere; clear hierarchy and no toy-like clutter. Use charcoal/deep blue-black surfaces, high-contrast text, muted dividers, and semantic accents:
- violet: Kuro / AI activity
- cyan: current creative operation or selection
- green: saved / healthy
- amber: waiting for user / attention
- red: error / blocked operation

Particles are ambient and may be disabled; reduce or suppress motion during drawing, focus mode, low-power mode and accessibility settings. Every state is communicated by icon/text as well as color.

## Main mobile home: three modes, one workspace

### Create
- top bar: project title, chapter/scene context, save/recovery state, undo, redo, overflow
- workspace: active canvas, text editor, scene editor, or 3D viewport
- bottom tool rail: current mode and context-specific tools
- floating Kuro presence: small persistent avatar/orb and a clear tap target
- compact project switcher and global search

### Story
- chapter/scene outline
- prose, dialogue and panel plan as semantic content
- canon and continuity indicators
- linked character/location/event chips opening the canonical object inspector
- timeline/branch picker and unresolved questions

### Vault
- searchable asset grid/list
- thumbnails plus asset type, style, variants, dependencies, provenance/license and use count
- filter by character, outfit, prop, environment, 3D, brush, template, texture, pose, expression
- empty state explains how to save the first asset; never imply the Vault has content it does not have.

A bottom navigation row can expose Create, Story, Vault, and More. On narrow phones, use drill-in screens and bottom sheets rather than squeezing desktop panels into the viewport. Tablets/foldables can use a two- or three-pane layout; desktop expands to dockable panels.

## Live Kuro workspace

When the user taps Kuro, expand a side/bottom sheet over the current workspace, never replace it with a detached chatbot app. The canvas remains visible whenever possible.

Suggested panel:
- top: Kuro avatar/name and session status
- transcript area with user/assistant turns and streaming text
- compact task card showing objective, current step, completed steps, remaining steps
- live activity stream: "Resolving character state", "Finding asset in Vault", "Setting camera", "Placing dialogue", "Validating continuity"
- preview/diff card with View changes, Apply, Undo and Details
- composer supporting text, push-to-talk, voice mode, attach reference, and cancel
- bottom controls: microphone state, mute, stop generation/operation, pause job, end session

Activity labels must describe real engine operations. Never display "drawing" when only a model is generating prose or a fake animation is playing.

## Real-time creation preview

When Kuro performs a task, users can choose:
1. **Watch** — see a non-blocking progress overlay and preview updates.
2. **Inspect** — pause at a checkpoint and inspect objects/layers/pose/camera/lettering.
3. **Review first** — Kuro makes a proposal and waits before applying.
4. **Background** — allowed only for a resumable job with clear permissions and progress history.

Use a step timeline: Plan -> Resolve -> Build -> Validate -> Preview -> Apply. Each status should map to a real job state. Show dependencies and warnings. Allow interruption at safe checkpoints; stop should cancel queued/ongoing work and preserve last valid state.

## Hours-long voice sessions

- Push-to-talk is always available; hands-free session is optional and clearly indicated.
- A visible mic dot and textual "Listening" label appear while audio is captured.
- Live transcript can be corrected, and users can choose whether the corrected sentence is used for the current request.
- Streaming speech can be interrupted immediately.
- After a configurable idle interval, offer a session checkpoint rather than pretending the context remains in the model window.
- Keep unsent thoughts, active job, last valid project state and unresolved questions separate.
- Voice or transcript does not become canon without the same approval rules as typed input.
- Provide captions, voice speed, volume, haptic and reduced-motion options.
- Allow one-tap mute, pause, stop, delete local transcript, and end session.
- No always-on listening by default; require explicit opt-in for hands-free mode and make it easy to revoke.

## Desktop adaptation

Preserve the same concepts but allow:
- collapsible left navigation
- central canvas/scene
- contextual right inspector
- bottom timeline/animation panel
- dockable Story, Vault, Layers, Style Lab, Research, Job Queue and asset dependency panels
- keyboard shortcuts and command palette
- multiple windows or monitors
- saveable workspace layouts.

Do not force a phone UI to become a miniature desktop. Mobile and desktop share domain actions and design tokens, not necessarily identical panel layouts.

## Accessibility and responsiveness

- Touch target sizes suitable for fingers and stylus; no hover-only actions.
- Keyboard and screen-reader semantics; focus order survives opening/closing Kuro.
- High contrast, scalable text, reduced motion, color-independent statuses.
- Respect system back navigation and Android gesture areas.
- Keep drawing path latency low by separating canvas input/rendering from chat animations and database writes.
- At low memory, unload inactive model/rendering resources and show model readiness honestly.
- Auto-save with visible status; never block a stroke while a large model loads.

## First UI vertical slice acceptance criteria

The first prototype should let a user:
1. Open a blank project and see the adaptive workspace.
2. Start a new canvas, draw at least one editable stroke, undo/redo it and see pressure/size/opacity controls.
3. Open Kuro without losing the canvas context.
4. Type and speak a request; see streaming transcript/status where speech is available.
5. Watch a task proposal, pause or cancel it, inspect a preview, and apply/undo a harmless native edit.
6. Save, close, reopen and recover the same project.
7. Navigate the same project on an Android phone layout and a desktop layout.
