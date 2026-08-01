# T10 — Ship readiness (index)

| Field | Value |
|-------|-------|
| **ID** | T10 |
| **Type** | epic (3 parts) |
| **Priority** | high |
| **Difficulty** | hard |
| **Status** | plan split — approve parts before impl |
| **Plan index** | this file |

## Todo source (proposed epic — not yet in `docs/todo/todo.md`)

```yaml
- id: T10
  title: Ship readiness — freeze matrix, overlay+pin, regression + Play compliance
  type: feature
  priority: high
  difficulty: hard
  status: pending
  why: |
    App feature surface mostly done (T1–T9). Before release: honest freeze backends,
    accurate RAM Free stats, real overlay BOOST, freeze pin list, full regression,
    Google Play policy pass.
  really_needed: yes
  impact: freeze/*, overlay, whitelist, mem stats, manifest, privacy, compliance docs
  followups: release skill / Play submit
  plan: docs/plan/T10-ship-readiness.md
```

---

## Goal (epic)

Make ApexCore **release-honest and release-safe**: working freeze modes, trustworthy stats, safe pin list, real overlay BOOST, regression green, Play-ready compliance.

---

## Parts (break-down)

| Part | File | Scope | Depends |
|------|------|-------|---------|
| **T10a** | [`T10a-freeze-matrix-ram-stats.md`](T10a-freeze-matrix-ram-stats.md) | Freeze matrix Standard/Shizuku/Root + RAM Free / BOOST **stats validation** | — |
| **T10b** | [`T10b-overlay-pin-apps.md`](T10b-overlay-pin-apps.md) | Overlay **BOOST** button + **pin apps** (freeze whitelist) | T10a preferred |
| **T10c** | [`T10c-regression-play-compliance.md`](T10c-regression-play-compliance.md) | **Full feature regression** + **Play compliance** | T10a + T10b |

```
T10a ──► T10b ──► T10c ──► release
 freeze     overlay     regression
 + stats    + pin       + Play
```

---

## Part 1 — T10a (summary)

**Freeze matrix + RAM Free stats validation**

- Fix Standard/`standard` neutered ForceStop (Fallback → killBackground or honest block)
- Prove Shizuku + Root force-stop via **adb**
- MemAvailable Δ only for freed UI; RAM Free matches `/proc/meminfo`
- Limited-mode copy when not elevated
- Out: overlay, pin, Play checklist

→ Full plan: **[T10a-freeze-matrix-ram-stats.md](T10a-freeze-matrix-ram-stats.md)**

---

## Part 2 — T10b (summary)

**Overlay + pin apps**

- Overlay CTA: real **BOOST** → `freezeAll` (exclude game + pins)
- `WhitelistStore` + `FreezeFilter` pin check
- Minimal pin UI; all freeze entry points honor pins
- Out: backend matrix, Play compliance epic

→ Full plan: **[T10b-overlay-pin-apps.md](T10b-overlay-pin-apps.md)**

---

## Part 3 — T10c (summary)

**First full feature regression + Play compliance**

- End-to-end matrix: Home/Games/Overlay/RAM Free/backends/pin
- Privacy honesty (a11y stub), receiver `exported=false`, permission hygiene
- `docs/review/compliance-T10.md` PASS gate
- Out: Play Console upload (release skill after)

→ Full plan: **[T10c-regression-play-compliance.md](T10c-regression-play-compliance.md)**

---

## Cross-cutting CRITICAL map

| Finding | Owner part |
|---------|------------|
| CRITICAL-1 Standard freeze neutered | **T10a** |
| MAJOR stats honesty / RAM Free truth | **T10a** |
| CRITICAL-2 Overlay fake button | **T10b** |
| CRITICAL-4 No whitelist | **T10b** |
| CRITICAL-3 A11y stub vs privacy | **T10c** |
| MAJOR-6 Exported FreezeReceiver | **T10c** |
| MAJOR-7 Play permissions / declarations | **T10c** |

---

## Epic decisions (defaults)

| # | Decision | Lock |
|---|----------|------|
| 1 | Split | Three plans T10a → T10b → T10c |
| 2 | Standard fallback | killBackground + limited-mode (T10a) |
| 3 | Freed UI metric | MemAvailable Δ (T10a) |
| 4 | Overlay CTA | Real BOOST (T10b) |
| 5 | Whitelist | Elevated; pin never freeze (T10b) |
| 6 | Accessibility | Not ship-ready; privacy honest (T10c) |
| 7 | FreezeReceiver | exported=false (T10c) |
| 8 | T9 fill re-arch | Only if adb proves broken (T10a residual) |

---

## Out of scope (epic YAGNI)

- Boot auto-freeze, tags, `pm disable`/`hide`
- Full a11y Force Stop automation
- Per-game overlay settings / FPS target / governor
- Net / battery / temp monitoring
- Fake multi-GB marketing claims
- Play Console upload (post-T10c release)

---

## Suggested todo YAML (3 slices)

After plan approval, write either one epic T10 or three todos T10a/T10b/T10c pointing at their plan files. Prefer **three todos** for clean dev-cycle branches.

---

## Open questions (epic)

1. Default pins empty vs messengers? → T10b  
2. Pin UI placement? → T10b  
3. Privacy public URL host? → T10c  
4. Fallback A vs block-without-elevation? → T10a  
5. Fold T9 iter-4 PENDING into T10a?  

---

## Approval gate

Approve **per part** or whole epic:

- [ ] T10a plan  
- [ ] T10b plan  
- [ ] T10c plan  
- [ ] Write todos to `docs/todo/todo.md`  
- [ ] Start impl (T10a first)  

---

## References

- `docs/freeze-architecture.md`, `docs/freeze-api.md`  
- `docs/plan/T9-ram-filler.md`, `docs/plan/T8-manual-game-addition.md`  
- `docs/privacy-policy.md`  
- Play guide: abhay-kb `Google_Play_Store_Policy_Compliance_Guide.md`  
- Parts: T10a / T10b / T10c files in this directory  
)
