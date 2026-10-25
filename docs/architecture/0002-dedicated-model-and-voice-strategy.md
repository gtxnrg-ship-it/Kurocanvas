# Architecture 0002: Dedicated local model and voice strategy

Status: initial prototype integrated; production model selection is not final. Android and desktop builds, model tests and APK packaging pass CI. Representative-device inference quality, memory use, latency and thermal behavior still require measurement.

The current prototype pins a Qwen3 0.6B LiteRT-LM artifact with source, Apache-2.0 license reference, size and SHA-256 in the model manifest. The app can download/resume, verify, load and use it for local streaming chat. The allowlisted native-action path currently supports a storyboard panel, a story entity and one time-scoped canon fact; each proposal requires user approval and is validated by the operation broker. This is not yet a general-purpose tool-using agent.

## Product decision

Kuro Canvas must not require users to supply API keys. The default experience should use a dedicated downloadable open-weight language model where the device can run it, with optional user-configured local endpoints only as an advanced extension—not as the default onboarding path.

Kuro is a tool-using creative operator, not an image generator. A language model can reason about intent and return structured tool calls; native engines do the actual work and retain editable project objects. Do not add a text-to-image dependency as a substitute for the drawing, 3D, animation or layout engines.

## Runtime interfaces

The initial KuroLocalModelRuntime interface currently supports load(modelPath), unload(), and streaming generate(systemInstruction, userPrompt). The following remain desired runtime capabilities rather than completed features: device-profile-aware loading, turn cancellation, health/capability reporting, memory estimates, runtime diagnostics, disposal and a canonical event stream distinguishing text, tool proposal, clarification request, progress, refusal/error and final response.

The current action parser is deliberately stricter than generic function calling: only the three implemented action schemas are accepted, and none executes without user approval.

Current prototype runtime: [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), using the pinned Qwen3 0.6B artifact in the app model catalog. This is a starter integration, not a final quality or hardware decision.

Alternative candidates for comparative testing:
- [llama.cpp](https://github.com/ggml-org/llama.cpp): first local inference proof of concept; GGUF quantization, CPU/GPU options, constrained JSON and function/tool calling. Evaluate actual target-device performance and the model's chat template.
- [MLC LLM](https://github.com/mlc-ai/mlc-llm): alternate adapter for compiled deployment and hardware acceleration, especially where Android GPU paths perform better. It adds compilation/model-packaging complexity and must not be assumed faster on every device.
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx): separate speech runtime, not the language model.

Do not bundle a huge model in the APK. Present a free model-download manager with size, memory estimate, checksum/signature, license, source, version, architecture, context window, supported input types and tested-device tier. Download is resumable, user-visible, cancellable, and verified before loading. Ship only models whose weights, tokenizer, code and redistribution license have been checked.

## Hardware tiers

Do not guess capabilities from phone brand alone. Profile memory, CPU/GPU/NPU, available storage, thermal state and OS capability. Offer:
- Small-device profile: compact quantized model, short context, conservative concurrency; all creative tools still work without AI.
- General mobile profile: stronger local model and streaming responses when runtime tests pass.
- Desktop profile: larger local model and more capable planning/context processing when resources permit.
- External endpoint: optional advanced configuration, disabled by default; clearly disclose network and privacy consequences.

Store model config per device profile, not canon or story data. Let the user unload the model to reclaim memory. Do not run model inference continuously in the background without a clear user-initiated job.

## Structured tool use

The model may request an action only through a versioned tool schema. A deterministic broker checks:
- schema correctness and argument bounds
- target project/object IDs
- read/write scopes
- required context and object revision
- destructive or ambiguous action status
- user approvals and permissions
- dependency ordering
- resource budget and cancellation state.

Reject arbitrary code, shell commands, unconstrained filesystem paths and direct SQL from the model. The model cannot self-grant permissions. A valid JSON object is not proof that the requested operation is correct.

Prefer plans with explicit preconditions and postconditions. If the model proposes changes to canon, destructive edits, batch changes, or ambiguous visual decisions, show a preview and request confirmation. For low-risk actions within an existing explicit scope, user settings may permit execution while preserving undo.

## Long conversations and hours-long sessions

The session should feel continuous without sending every previous token to the model:
- persist transcript segments with timestamps and speaker role
- stream partial ASR and TTS independently from the planner
- save explicit user statements and confirmed project changes to structured records
- build session summaries with source links and unresolved questions
- retrieve relevant facts via the context compiler at each task boundary
- checkpoint long-running jobs and release inactive GPU/memory resources
- allow mute, pause, stop, cancel, transcript editing and export
- indicate listening/processing/speaking states without hiding the active canvas
- provide a clear microphone status and a push-to-talk option
- handle interruption: stop speech playback, preserve the current task, listen, then resume only when safe
- do not treat speech recognition text as confirmed canon without user approval.

## Kuro voice identity

Keep Kuro's voice persona separate from the TTS model. Persona controls pronunciation guidance, pace, prosody, voice settings, vocabulary and interaction style. TTS engines are replaceable. Voice model availability and voice licensing are reviewed separately from software code.

Candidates:
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for offline streaming ASR, TTS, VAD, diarization and speaker-related functions.
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) as an alternative cross-platform ASR component.
- Individual TTS model/voice assets must have separately recorded licenses and consent/provenance; do not assume an engine's software license covers model data or voice identities.

## Evaluation before choosing default weights

Build an evaluation set from synthetic project fixtures (never private user stories) and include:
- tool selection and argument correctness
- structured-output validity under constrained decoding
- task completion using only native tools
- story fact retrieval with time/branch resolution
- unknown-answer and contradiction behavior
- overconfident invention rate
- interruption/cancellation and retry behavior
- English/Bangla and relevant comic-dialogue language behavior
- latency, RAM/VRAM, cold start, battery and thermal behavior
- mobile offline operation and failed-download recovery
- prompt-injection resistance from imported documents, web pages and plugin output
- model license, tokenizer/format license, dataset provenance and redistribution rights.

Select based on measured creative-task reliability, not claims of being "uncensored", a parameter count or a generic chat benchmark. Policy labels do not replace native tool permissions or project invariants.

## Security and privacy

- Model files are data, not executable code.
- Verify hashes/signatures against a trusted manifest and keep provenance.
- Do not log prompt contents, transcripts or project names to remote telemetry by default.
- Keep credentials out of prompt history and tool outputs.
- Imported assets, research pages and plugin output are untrusted; they cannot override system policies or operation permissions.
- External inference must be an explicit opt-in and must disclose data leaving the device.
- Apply resource caps, cancellation, safe unload and model file validation.
