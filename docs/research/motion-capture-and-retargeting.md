# Motion capture, dance analysis, and character retargeting

Research snapshot: 2026-10-24. "Dance sensor" is treated here as dance/movement capture: camera/video pose estimation, optional multi-camera triangulation, motion cleanup, and retargeting to editable character rigs. No single component covers this whole workflow.

## Recommended layered pipeline

1. **Capture source**: phone camera/video for lightweight single-view capture; optional two-or-more synchronized cameras for reconstructing 3D movement from different views.
2. **Pose estimation**: retrieve body landmarks with confidence scores over time. Face/hand landmarks are separate capability and accuracy gates.
3. **Temporal filtering and cleanup**: correct dropped frames, jitter, occlusion, left/right swaps, foot sliding, impossible joint angles and sudden discontinuities.
4. **3D reconstruction**: for single-camera input, label depth and occlusion ambiguity and allow manual correction; for calibrated multi-camera capture, triangulate and optimize across views.
5. **Skeleton mapping**: map source joints to a canonical Kuro skeleton and account for proportions, coordinate axes, rest pose and bone lengths.
6. **Retargeting**: create editable keyframes for the selected character rig, not only a flattened video. Keep original capture, confidence, source frame/time, mapping and manual edits.
7. **Review**: side-by-side video and rig preview, scrub/timeline, per-joint correction, keyframe adjustment, and undo.
8. **Vault**: save the processed motion as a reusable asset with provenance and compatible rig profiles.

## Candidates and boundaries

### MediaPipe Pose Landmarker — mobile starting point
Official Android documentation provides a task package and pose models; the pose landmark model outputs 33 approximate body landmarks for image/video and includes 3D world-coordinate landmarks. This makes it a practical prototype for pose overlays and first-pass single-camera movement, but it is not equivalent to full optical motion capture, robust occlusion handling, perfect feet contact or final animation retargeting.

- Guide: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- Android guide: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android
- Gate: separately verify software license and the selected downloadable model's terms; test on actual Android hardware and user-provided clips.

### FreeMoCap — desktop multi-camera reference / bridge
FreeMoCap is a free, open-source markerless mocap application intended to capture synchronized video from two or more consumer cameras and reconstruct a 3D skeleton; its project materials describe offline use after model downloads and Blender export. The current project is alpha and AGPL-3.0-or-later. Treat it as a desktop reference, an optional externally installed workflow, and a candidate source of interchange data—not as a library to embed automatically into Kuro.

- Project: https://github.com/freemocap/freemocap
- Docs: https://docs.freemocap.org/freemocap/docs/intro/
- Gate: test output formats and exact components/licenses; confirm the selected version works on target OS/GPU before recommending it.

### MMPose — configurable desktop research pipeline
MMPose is an open-source pose-estimation toolbox covering 2D pose, whole-body keypoints, hand/face tasks and a range of model configurations. It is useful for offline experiments and fallback pipelines but brings Python/ML dependencies and model-specific data/weight-license checks. It should not be forced into the Android runtime.

- Project: https://github.com/open-mmlab/mmpose
- Gate: pin the version, inspect the exact license and checkpoint/dataset terms, and benchmark target workflows.

### OpenPose — exclude from default integration
The commonly published OpenPose license is restricted to non-commercial research. Do not bundle it into a redistributable product unless a suitable separate license is obtained.

- Project: https://github.com/CMU-Perceptual-Computing-Lab/openpose

### Blender retargeting — optional authoring bridge
Blender provides a mature rigging/animation environment. A separate retarget add-on can help map motion between different rigs; add-ons have their own code license and Blender-version requirements. Keep this as an optional desktop workflow rather than a required Android feature.

- Blender: https://github.com/blender/blender
- Example retarget extension: https://github.com/KBSBAUDRICE/Retarget
- Gate: review the specific add-on license/version and avoid shipping it as a dependency until integration is proven.

## Kuro's canonical motion representation

Do not bind saved movement permanently to one vendor's 33/whole-body keypoint scheme. Define a versioned intermediate motion document, then adapt each capture source to it.

Suggested fields:
- motion asset ID, schema version, title and provenance
- source video file hash, source clip in/out time, frame rate, capture device, camera calibration metadata
- source and target coordinate systems/units
- tracked skeleton profile and landmark definitions
- per-frame timestamps, joints/transforms, confidence, missing/occluded state
- camera pose/calibration and triangulation residual when available
- canonical skeleton mapping, rest pose and rig identifier
- keyframes, interpolation/tangent data, root motion and optional contacts
- cleanup operations and manual overrides with revision history
- consent/authorization record for identifiable user-provided footage
- source-model and dataset licenses, version, checksum and local retention policy
- export references (glTF animation, BVH, FBX where a separately reviewed exporter exists, or application-specific interchange)

## Tests that matter for dance and expressive animation

- fast turns, crossed limbs, spins, floor work, jumps, mirrored clips and occlusion
- leg/foot contact and foot sliding over time
- dropped frames, variable frame rate and audio/video sync
- left/right consistency and camera-axis changes
- differently proportioned character rigs and non-human stylized anatomy
- hand/finger/face motion as separate optional data
- frame-by-frame correction without corrupting source capture
- privacy: visible camera state, local processing where feasible, clear delete/export controls
- target-device FPS, memory, temperature and battery under sustained capture.

## Scope distinction

Pose landmarks are observations, not truth. A single RGB camera cannot reliably reconstruct all hidden joints or exact 3D depth in every pose. Kuro must preserve uncertainty, allow editing, and avoid promising professional marker-based capture quality from a phone-only clip. The goal is a usable assisted animation workflow, with optional multi-camera capture when higher 3D fidelity is required.
