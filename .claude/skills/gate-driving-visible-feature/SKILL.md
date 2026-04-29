---
name: gate-driving-visible-feature
description: Use when adding any UI surface that is visible to the driver and could distract while moving — video playback, complex animation, fine typography, scrolling lists, dense controls. Triggers on "add YouTube", "play video", "show notifications feed", "scrolling apps drawer". This skill is the SSOT for femto-car-launcher's driving-lockout policy; other places (CLAUDE.md, agents, skills) reference it.
argument-hint: "[feature-name]"
allowed-tools:
  - Read
  - Edit
  - Grep
paths:
  - app/src/main/java/io/github/seijikohara/femto/ui/**/*.kt
---

# Gating a driving-visible feature

This skill is the **driving-lockout policy SSOT** for the project.
Other documents (`CLAUDE.md`, agents, skills) defer here for the
"why" and "how"; do not maintain a parallel policy elsewhere.

## Rationale

The launcher targets **multiple markets**. While the vehicle is in
motion, complex / distracting UI is replaced with a minimal,
glanceable surface. Driver-distraction is regulated everywhere the
launcher ships, and the **strictest applicable rule wins**.

**Distribution policies (binding wherever the app ships):**

- Google Play **Driver Distraction policy** is the most binding
  practical constraint and can reject the app at submission.
- Apple App Store Review applies whenever the launcher integrates
  with CarPlay-adjacent flows.

**Regional regulation (examples; not exhaustive):**

- EU and UN-ECE region: UN-ECE R10 and national driver-distraction
  regulations.
- US: NHTSA driver-distraction guidelines (voluntary at the
  federal level, enforced through store policy and state laws).
- Japan: Road Traffic Act §71-5-5 (the "no fixation" rule) and
  supporting MLIT guidance.
- China, South Korea, Australia, and other target markets each
  publish equivalent driver-distraction rules. Audit the specific
  rule of every market the launcher ships in before that release.

When a regional rule is invoked against a concrete release, cite
the actual statute or paragraph. Do not paraphrase regional rules
as case law in this document or in product copy.

The lockout flag itself is not yet implemented. Until it is, gate
new driver-distracting features behind a **stub** that defaults to
"locked" (the safe state). When the real signal lands, only that
stub needs to change.

## What triggers the gate (the SSOT criteria)

A feature **must** be gated if any of:

- Video playback (full or PiP).
- Animations longer than 250 ms or that loop.
- Text smaller than `FemtoDimens.MinBodyTextSize` (18 sp).
- Scrolling lists with more than ~6 items above the fold.
- Inputs requiring more than one tap to commit (forms, multi-step).
- Any surface that hides the map / music PiP overlays.

When the answer is uncertain, gate the feature.

## Procedure

1. **Use the lockout stub.** Until the real signal exists, treat
   `DrivingState.IS_LOCKED` as `true`:

   ```kotlin
   @Composable
   fun MyFeatureScreen() {
       val drivingLocked = rememberDrivingLockState() // true for now
       if (drivingLocked) {
           DrivingLockedPlaceholder()
       } else {
           MyFeatureContent()
       }
   }
   ```

   `DrivingLockedPlaceholder()` shows a brief, glanceable message
   ("Available when stopped") with a single oversized icon — no
   video, no fine type.

2. **Mark the gate site** with a stable TODO marker so the eventual
   wiring can find every site at once:

   ```kotlin
   // TODO(driving-lockout): wire to real DrivingState signal.
   ```

   `grep -rn 'TODO(driving-lockout)' app/src/main/java` should
   surface every such site.

3. **Test both states** with `@PreviewLightDark`:
   - Locked preview shows the placeholder.
   - Unlocked preview shows the real content for design review.

4. **Document** in the commit body which feature was gated.

## Hard prohibitions

- No "force unlock" toggle, even for testing. Gate any debug
  override behind a build variant or a hidden debug-menu entry; the
  product surface must never advertise a way to bypass the lock.
- No user-facing "I am the passenger" override. Store policies and
  the regulatory frameworks of all major target markets explicitly
  prohibit this.
- The default state of the stub is **locked**. Inverting the
  default to accelerate a release is a policy violation.
- Gating only part of a driver-distracting surface (e.g. hiding the
  video element but leaving a complex scroll feed). The whole
  feature is either locked or unlocked.
