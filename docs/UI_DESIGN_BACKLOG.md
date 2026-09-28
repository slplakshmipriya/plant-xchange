# Jira Backlog — Garden Swap UI Design Match

**Source:** hosted interactive prototype `garden-swap-app-ui-design`
(2026-09-27; Figma-style, not a native Figma file)
**Status:** Draft — ticket keys are proposed and stable.
**Branch:** `feature/ui-design` (from `main`; never merge `debug/bypass-auth`
into this branch or `main`)

## Design tokens (locked, extracted from the prototype)

| Token   | Light     | Dark      | Usage                          |
|---------|-----------|-----------|--------------------------------|
| bg      | `#F3F5EF` | `#101512` | window background              |
| surface | `#FFFFFF` | `#18201A` | cards, sheets                  |
| ink     | `#172018` | `#EDF5EE` | primary text                   |
| muted   | `#5F6D61` | `#A8B4AA` | secondary text                 |
| line    | `#D8DED4` | `#354139` | dividers, borders              |
| leaf    | `#175F3A` | `#7FC996` | primary actions, brand green   |
| focus   | `#2A7C50` | `#94DDA9` | accents, links                 |
| nav     | `#163722` | `#091C11` | bottom navigation background   |
| nav-ink | `#F3F8F1` | `#F3F8F1` | bottom navigation icons/text   |
| acid    | `#D8EE72` | `#BFDC5E` | highlights, credit accents     |
| yellow  | `#F0C44C` | `#E9BF45` | warnings, urgency              |
| orange  | `#F08A54` | `#E16F38` | pick-your-own accent           |
| red     | `#EB8376` | `#BD4A3C` | errors, destructive            |

- **Type:** DM Sans (body/UI), Libre Franklin (display/headings)
- **Shape:** 18dp cards, 16dp dialogs, full-pill buttons/chips, circular avatars
- **Elevation:** soft green-tinted shadow (`0 20dp 48dp rgba(26,48,31,.12)`)
- **Prototype views:** Explore, Messages, My swaps, Plant care, Profile
  (bottom navigation, dark green)

## Conventions

- Epics: `UID-E1` … `UID-E9`
- Tickets: `UID-xxx` (Android UI). Priority P0–P3, story points (Fibonacci).
- "Done" = matches the prototype at a glance on a phone screen, dark theme
  verified, no new Crashlytics issues, per-ticket commit on `feature/ui-design`.
- P0 = blocks the design-match milestone. P1 = sequenced after P0.

## Dependencies

```
UID-E1 (foundation) → UID-E2 … UID-E8 (screens, parallelizable)
UID-E2 (explore cards) → UID-E3 (detail reuses listing card)
UID-E9 (QA) after all screens
```

---

# EPIC UID-E1 — Design system foundation

**Objective:** Tokens, type, and components every screen builds on.
Nothing user-facing on its own.

## UID-001 — Color system (light + dark) [P0, 3]

- Add `res/values/colors.xml` (light) and `res/values-night/colors.xml`
  (dark) with the locked tokens above.
- `Theme.GardenSwap` → `Theme.AppCompat.DayNight.NoActionBar`;
  `colorPrimary`=leaf, `colorAccent`=focus, `windowBackground`=bg,
  `textColorPrimary`=ink, `textColorSecondary`=muted.
- Acceptance: every existing screen renders in light and dark without
  hardcoded colors; no visual regression on the debug menu.

## UID-002 — Typography [P0, 3]

- Add `res/font/dm_sans.xml` + `res/font/libre_franklin.xml` via the
  Google Fonts provider (minSdk 23 compatible).
- `TextAppearance.GardenSwap.Display/Headline/Title/Body/Label/Caption`
  styles mapping to the prototype's sizes and letter-spacing.
- Acceptance: headings render in Libre Franklin, body in DM Sans;
  type scale applied on at least one screen.

## UID-003 — Component library [P0, 5]

- Extend `Ui.java` (or new `Design.java`): primary pill button (leaf),
  secondary outlined button, 18dp card background, filter chip,
  circular avatar placeholder, verification badge restyle, bottom-sheet
  modal helper, toast styling.
- Acceptance: a kitchen-sink preview reachable from the debug menu
  renders every component in both themes.

## UID-004 — Bottom navigation [P0, 5]

- Five-tab dark-green bottom nav (Explore, Messages, My swaps,
  Plant care, Profile) per the prototype; each tab routes to the
  corresponding activity (new Explore/Profile/My-swaps hosts as needed).
- Acceptance: nav visible on every top-level screen, selected tab
  highlighted in nav-ink on nav green, back stack sane.

---

# EPIC UID-E2 — Explore

## UID-010 — Explore home screen [P0, 8]

- Rework the post-login home into the prototype's Explore view: hero
  header, four "way" cards (Seedlings, Pick-your-own, Harvest, Plant
  care), nearby listing cards, credit balance panel, want-list match
  panel.
- Acceptance: matches the prototype's Explore view layout and spacing;
  each card deep-links to its flow.

## UID-011 — Listing card + filter chips [P0, 5]

- Reusable listing card: photo, kind tag, title, credit cost, distance,
  urgency cue. Filter chips row (All, Seedlings, Pick, Harvest, Care)
  with working filters on mock data.
- Depends on UID-003. Acceptance: cards identical between Explore and
  any list reusing them.

---

# EPIC UID-E3 — Listing flows

## UID-012 — Listing detail restyle [P1, 5]

- Photo header, variety/quantity/spray sections, visit-rules panel,
  owner row, claim CTA in prototype styling.
- Acceptance: all current detail content present, prototype visual
  language.

## UID-013 — Claim bottom sheet + toast [P1, 3]

- Claim flow as a bottom sheet (quantity, pickup window, notes,
  confirm) with success toast per prototype.
- Acceptance: full claim journey works on mock data.

## UID-014 — Create listing restyle [P1, 5]

- Multi-field form restyled: photo picker, variety, quantity/unit,
  credit cost, spray disclosure, visit rules.
- Acceptance: validation behavior unchanged, prototype styling.

---

# EPIC UID-E4 — Exchange & wallet

## UID-015 — Wallet restyle [P1, 5]

- Credit balance card (acid highlight), ledger entry list, expiry
  cues per prototype.
- Acceptance: mock ledger renders with correct credit arithmetic.

## UID-016 — Confirm exchange restyle [P1, 3]

- Two-party confirmation screen in prototype styling.
- Acceptance: confirm/decline paths work on mock data.

---

# EPIC UID-E5 — Sitters & plant care

## UID-017 — Sitter list + profile restyle [P1, 5]

- Plant-care view: sitter cards (avatar, name, skills, rate),
  profile with skills, reviews, request CTA.
- Acceptance: matches prototype's Plant care view.

## UID-018 — Review flow restyle [P2, 3]

- Post-sit review form (rating + text) in prototype styling.
- Acceptance: gated states (pre-completion) still enforced.

---

# EPIC UID-E6 — Messaging

## UID-019 — Threads + chat restyle [P1, 5]

- Thread list rows and chat bubbles in prototype styling;
  coordinate-leak warning keeps its prominence.
- Acceptance: send/receive works on mock data; warning unmissable.

---

# EPIC UID-E7 — Onboarding & trust

## UID-020 — Auth screen restyle [P1, 3]

- Phone + Continue-with-Google in prototype styling (leaf primary
  button, muted divider).
- Acceptance: both sign-in paths work; no behavior change.

## UID-021 — Profile form + home ZIP restyle [P2, 3]

- Prototype styling for the remaining onboarding steps.
- Acceptance: validation behavior unchanged.

## UID-022 — IDV restyle [P2, 3]

- Verification explainer, badge states, and Continue flow in
  prototype styling.
- Acceptance: stub provider journey unchanged.

---

# EPIC UID-E8 — Profile & swaps

## UID-023 — Profile + My swaps restyle [P1, 5]

- Profile view (avatar, verification badge, stats) and My swaps
  list (active/completed) per prototype.
- Acceptance: matches prototype's Profile and My swaps views.

---

# EPIC UID-E9 — Design QA

## UID-024 — Design QA pass [P0, 5]

- Per-screen side-by-side check against the prototype (light + dark),
  touch-target ≥ 48dp, contrast ratios, rotation sanity.
- Depends on all screen tickets. Acceptance: zero P0 visual diffs,
  QA checklist signed off in the ticket.
