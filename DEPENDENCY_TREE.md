# Dependency Tree — Garden Swap MVP

**Source:** `JIRA_BACKLOG.md` (MVP epics 1–10)
**Rule:** A ticket starts when its dependencies' *contracts* are frozen, not
when they're fully implemented. Every `AND-*` ticket depends on the API
contract, never on the backend implementation — FE builds against dummy/mock
JSON from day one.

## New enabling ticket (proposed)

- **API-004** [P0, 3] OpenAPI contract spec for all MVP endpoints
  - AC: `openapi.yaml` in repo covering every MVP route (auth, users, listings,
    feed, want-lists, trees, slots, ledger, sitters, bookings, reviews, chat,
    notifications); contract tests generated from spec run in CI against the
    backend; FE uses spec to generate/verify its mock layer.
  - This is the single ticket that unlocks FE↔BE parallelism. Without it, FE
    mocks drift and integration becomes the critical path.

---

## Wave plan (each wave = one dependency layer; everything inside a wave runs in parallel)

### Wave 0 — no dependencies
| Ticket | Notes |
|---|---|
| API-001 Cloud Run + Neon | Infra root for all backend |
| AND-001 Android scaffold | Infra root for all FE; independent of API-001 |

### Wave 1 — depends on Wave 0
| Ticket | Depends on | Notes |
|---|---|---|
| API-002 authN/authZ | API-001 | Gates every later API ticket |
| API-003 middleware (logging/rate-limit/errors) | API-001 | |
| API-004 OpenAPI contract spec | — (needs API-001 context only) | **Unlocks FE parallelism** |
| AND-002 Firebase wiring | AND-001 | Auth/Analytics/Crashlytics/FCM |
| SEC-001 secrets management | API-001 | Needed before Stripe keys exist |
| SEC-002 vuln scanning in CI | API-001, AND-001 | |

### Wave 2 — domains start; FE runs on mocks
| Ticket | Depends on | Notes |
|---|---|---|
| API-010 user profiles | API-002 | |
| API-011 phone verification | API-002 | |
| API-012 ID verification | API-002 | |
| API-020 listing CRUD + lifecycle | API-002 (+API-003 nice-to-have) | Core domain root |
| API-022 photo upload | API-002 | Parallel with API-020 |
| API-081 notification service | API-002, AND-002 (FCM only for real delivery; buildable without) | |
| AND-010 onboarding | AND-002 + API-004 contract | Mock: API-010/011 |
| AND-011 IDV UI | AND-002 + API-004 contract | Mock: API-012 |
| SEC-010 PII minimization | API-010, API-004 | Rule, enforced in later API tickets |

### Wave 3 — features fan out
| Ticket | Depends on | Notes |
|---|---|---|
| API-021 freshness-ranked feed | API-020 | |
| API-030 want-list + match engine | API-020, API-081 | |
| API-040 harvest listing rules | API-020 | Thin delta |
| API-050 tree listings + ripe alerts | API-020, API-081 | |
| API-060 credit ledger + balances | API-002, API-020 | Contract for completion events defined here |
| API-070 sitter profiles + booking | API-002, API-012 | |
| API-080 messaging | API-002, API-020 | |
| AND-020 create listing | AND-002 + contract | Mock: API-020/022 |
| AND-021 listing detail + claim | AND-002 + contract | Mock: API-021 |
| AND-030 want-list UI | AND-002 + contract | Mock: API-030 |
| AND-040 harvest UI deltas | AND-020 + contract | Mock: API-040 |
| AND-050 tree detail + slots | AND-002 + contract | Mock: API-050/051 |
| AND-060 wallet UI | AND-002 + contract | Mock: API-060/061 |
| AND-070 sitter discovery + booking | AND-002 + contract | Mock: API-070 |
| AND-080 chat UI + notif prefs | AND-002 + contract | Mock: API-080/081 |
| ANL-020 listing funnel events | API-004 (event names) | Implement alongside AND-020/021 |
| ANL-100 event taxonomy draft | — | Spec only; implementation in Wave 5 |

### Wave 4 — second-order features (depend on Wave 3 domains)
| Ticket | Depends on | Notes |
|---|---|---|
| API-031 no-show tracking | API-060 (completion events) | |
| API-051 slot claiming | API-050, API-060 (credit charge) | DB-constraint atomicity |
| API-061 issuance/expiry/caps | API-060 | |
| API-062 dispute reversal | API-060 | |
| API-071 Stripe Connect | API-070, SEC-001 | |
| API-072 verified reviews | API-070 | |
| AND-031 expiry nudges | AND-021 + contract | Mock: API-020 |
| AND-071 visit check-ins | AND-070 + contract | Mock: API-070 |
| SEC-050 solo-pickup gating | API-050, API-051, API-012 | Server-enforced |
| SEC-060 ledger integrity | API-060 | |
| SEC-070 payments + PII hardening | API-070, API-071 | |
| ANL-030 matching effectiveness | API-030, AND-030 | |
| ANL-060 economy health dashboard | API-060, API-061 | |

### Wave 5 — hardening & launch (no new features)
| Ticket | Depends on | Notes |
|---|---|---|
| SEC-100 pre-launch security review | All P0 API + AND work | Threat model + test |
| SEC-101 abuse & fraud controls | API-060, API-080 | |
| SEC-102 data retention & deletion | API-010, API-080, API-022 | |
| ANL-100 taxonomy freeze + dashboards | All ANL-* + features | |
| ANL-101 crash-free + perf gates | Everything | |
| AND-100 release pipeline | AND-001, all AND work | |

---

## Critical paths (longest dependency chains → launch)

1. **Core exchange path:** API-001 → API-002 → API-020 → API-060 → API-061 →
   SEC-100 → ANL-101 → launch
2. **Payments path:** API-001 → API-002 → API-012 → API-070 → API-071 →
   SEC-070 → SEC-100 → launch
3. **FE path:** AND-001 → AND-002 → (AND-020 … AND-080 on mocks) → AND-100 →
   launch — *not* on the critical path as long as API-004 lands in Wave 1.

The binding constraint is the backend chain (1); FE must never wait for it.

## Parallelism summary

- **Wave 0–1:** 2 tracks (infra) → 6 tickets across BE/FE/SEC, all parallel.
- **Wave 2:** 9 tickets parallel — identity, listings, notifications, and the
  first two FE screens all build simultaneously.
- **Wave 3:** 18 tickets parallel — the entire feature surface (6 backend
  domains + 8 FE screens) builds in one wave because FE works from the
  API-004 contract with dummy data.
- **Wave 4:** 13 tickets parallel — payments, credits completion, reviews,
  security enforcement.
- **Wave 5:** 6 hardening tickets, mostly sequential review work.

## Integration strategy (keeps the parallel tracks honest)

1. **Contract-first:** API-004 is the law. FE mocks are generated from
   `openapi.yaml`; backend runs spec-derived contract tests in CI.
2. **Integration checkpoints** at the end of Waves 3 and 4: FE swaps mocks
   for staging backend; drift = P0 bug against the drifting side.
3. **Completion-event contract** (defined in API-060) is the seam between
   pillars and the ledger — freeze it in Wave 3 before API-031/051/061 build
   on it in Wave 4.
4. **Risk:** backend Wave-3 slip pushes FE integration but *not* FE
   development (mocks hold). Mitigation: contract tests fail loudly; no
   hand-edited mocks — regenerate from spec.

## Sequencing recommendation

- Staff Wave 0–1 first (1 BE, 1 FE, or 1 full-stack + contract author).
- From Wave 2 on, FE and BE are fully decoupled streams; the only shared
  ceremony is the Wave 3/4 integration checkpoints.
- P1 tickets (AND-031, ANL-101) slot into Wave 4/5 without moving the
  critical path.
