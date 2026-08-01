# FitPal — "The Trail" game design

The motivation layer. A small **restoration/journey game** (Gardenscapes-style *structure*,
not its art) whose engine is powered by **real logging**, not by the clock.

Status: **Phases A–F built.** Follow [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) for all UI.

Code: `domain/{Trail,TrailProgression,Challenges,TrailShop}.kt` ·
`data/repository/{Trail,Challenge}Repository.kt` · `data/local/{entity,dao}/{Trail,Challenge}*` ·
`ui/screen/trail/` · `ui/component/Spotlight.kt` · DB **v23** (`MIGRATION_17_18` … `22_23`).
Entry: the Home streak chip.

---

## 1. The one non-negotiable rule

**One tick = one logged day.** Nothing meaningful accrues from waiting.

Ordinary incremental games run on elapsed time — which here would let the game pay out on its
own, making logging the boring tax you skip. The game would quietly cannibalise the app it's
meant to serve. So: no wall-clock production, ever. Ticks bank up while you're away and are
collected on your next visit, so the "idle collect" feeling survives — but it's banked from
**logged days**.

Corollary: purchases are **never** destroyed by neglect. Neglect hits the *rate* (see §5).
Losing bought progress after one bad week is how you get someone to delete the app.

---

## 2. Shape: Trail → Sites → Projects

- The **Trail** is a sequence of **Sites** — neglected places you restore (home garden, old
  greenhouse, dried-up well, overgrown orchard…).
- Each Site holds **10–12 Projects**: *clear the overgrowth · rebuild the well · plant the
  hedgerow · hang the lanterns*. Each costs **Growth** and, once bought, **permanently adds
  production**.
- Complete every Project → the Site is **restored** (visual payoff + permanent reward) → the
  path opens to the next Site.
- **Region 1 is handcrafted** (~6–8 Sites with real character). After that, regions are
  **procedurally themed** (biome + prop palette + species) so the Trail never dead-ends.

Why this shape: there is *always* a small named goal ("two more and the greenhouse is done")
and a bigger one behind it. That's what the passive garden never gave.

---

## 3. Economy

| | Earned by | Spent on |
|---|---|---|
| **💧 Water** | Logging a day (+1, +1 more if on-goal), cap 14 | Running a tick (1 💧). The reserve is the safety net. |
| **🌿 Growth** | Produced per tick by everything you've restored | Projects + upgrades — the main loop |
| **⭐ Points** | **Claiming challenges** (plus manual tick, weekly bonus) | Keystone projects, cases, cosmetics, seeds |

**Per-tick production**

```
growth = (sum of project production) × vitality × (on-goal ? 1.25 : 1.0)
```

If 💧 is 0 the tick can't run — no production that day. (Logging always earns more 💧 than a
tick costs, so a consistent user never starves; a lapsed one stalls.)

**Tuned curve — Site 1** (base production 10; each project adds +4; cost = 15 × 1.25ⁿ):

| Project | Cost | Production after | Days to afford |
|---|---|---|---|
| 1 | 15 | 14 | 1.5 |
| 3 | 23 | 22 | 1.3 |
| 5 | 37 | 30 | 1.4 |
| 7 | 57 | 38 | 1.7 |
| 10 | 112 | 50 | 2.4 |

≈ **16–17 ticks for the Site** (~2.5 weeks at one log/day) with **something completing every
1.3–2.4 days**. At full Vitality it's ~8 days; at the floor it's ~33. Consistency is the
difference, and it's legible.

Each later Site scales costs ~3× and base production ~2.5×, holding the ~2–3 week cadence
while the numbers grow.

**⭐ values:** daily challenge 10 · weekly 40 · monthly 150 · weekly on-track bonus 20 ·
manual tick 5. Keystone projects cost 60–100 ⭐ in Region 1; cases 80 / 250 ⭐.

---

## 4. Challenges

Three live slots: **daily · weekly · monthly**. One slot is **always "stay within your calorie
goal."** Points are banked **only when you tap Claim** — the deliberate friction that makes you
open the screen. Claimable until the end of the following period, so a late evening doesn't
rob you.

**Hybrid verification** (settled earlier):

- **Concrete challenges → deterministic code.** Measurable predicates against logged data:
  fiber ≥ X, protein ≥ target, ≤ goal calories, logged N meals, no skipped day this week,
  watered manually, first meal before 10am. Rock-solid, un-abusable.
- **Creative challenges → AI generates *and* judges.** For the interesting, non-repetitive
  ones: *"start today with a lighter meal"*, *"you eat a lot of meat — make one day this month
  vegan."* The judge only ever reads the day's logged data (the user never converses with it,
  so it can't be talked into a yes). Prompt it to be **strict**; occasional hallucination is
  accepted.

**Keystone projects** are ⭐-gated: the centrepiece of each Site, a big visual moment and a
permanent bonus. You can move on without them — the Site just stays visibly unfinished and
produces less. Strong pull, no hard wall.

---

## 5. Neglect: the wilderness takes it back

**Vitality** is a production multiplier and the whole of the decay system:

- Logged day: **+0.1×**, cap **2.0×**
- Missed day: **−0.4×**, floor **0.5×**

Visually, low Vitality means **overgrowth creeps back**: the scene dims, vines return over what
you built, production drops. Log again and it retreats. Real degradation, fully reversible,
nothing bought is ever lost — thematically perfect for a restoration game.

---

## 6. Nutrition link (light)

Logging fuels ticks; **on-goal days give ×1.25 production and better rolls** on seeds/cases.
Consistency drives the journey; quality accelerates it.

---

## 7. What the existing garden becomes

Nothing is discarded:

- Today's plant → **Site One, the home garden**
- Watering → **the tick** (manual tick still pays ⭐)
- Blooms → the **collection**, now species gathered *along the trail*
- Weekly on-track bonus → pays **Growth + ⭐**
- The 9 species in `PlantCatalog` → **Region 1's** catalogue

---

## 8. Screen & visuals

Entry: **the streak chip on Home**, which gains a **badge** when something is waiting
("3 ticks · 2 claims") so a hidden screen still gets visited.

`GradientBackdrop(GARDEN)` + `GlassTopBar(site name)`, then `SegmentedPills`:
**Site · Challenges · Shop**.

**Site**
1. **Diorama** (hero) — Canvas scene: horizon, path, 10–12 **prop slots**. Buying a project
   animates its prop in. Neglect overlays vines + desaturates.
2. **Collect** — *"3 days of growth waiting"* + the hero number (Inter ExtraBold, tabular).
3. **Resource row** — 💧 · 🌿 · ⭐ · ×vitality as `GlassCapsule`s.
4. **Projects** — `glassSoft` rows: name, cost, buy. Keystones marked gold.

**Challenges** — three cards, progress + **Claim**.
**Shop** — cases, cosmetics, seeds; the collection grid.

Rendering: a **lit diorama**, not cartoon art — minimal glowing vector shapes on dark, matching
the app's aesthetic (trying to imitate Gardenscapes' illustration here would look worse *and*
cost more). Props are a **shared library** reused across Sites with different arrangements and
palettes — that's what keeps this bounded.

---

## 9. Data model sketch

- `game_state` (single row): region, siteIndex, growth, points, vitality, lastTickDate,
  bankedTicks, legacyMultiplier
- `site_progress`: siteId + completed project ids
- `challenges`: id, period, kind (CONCRETE/AI), spec JSON, assignedDate, progress, completedAt,
  claimedAt
- Reuse `garden_state` water + `collected_plants`
- New tables → **DB v12** + `MIGRATION_11_12` registered in `di/DatabaseModule.kt`

---

## 10. Build order

- **A — engine** ✅: ticks, vitality, growth, projects, economy, data layer
- **B — diorama** ✅: Canvas scene, 19-prop library, depth layout, overgrowth, animations
- **C — challenges** ✅: concrete checks, AI generation + strict judging, claim flow
- **D — shop** ✅: cases, curio collection, scene themes

All four phases built. The loop is closed: log → tick → collect → build → challenges → ⭐ → shop.

- **E — map, gating & tutorial** ✅: trail map, one-mechanic-at-a-time unlocks, coach-marks.
- **F — build styles & a scene you can touch** ✅: three chosen designs per project, tap-to-find.

### Phase F notes

**Every build is a choice, and the choice is visible.** Each `PropKind` has **three named
variants** (`PropVariants` in `domain/Trail.kt`) — e.g. a well is Fieldstone / Timber frame /
Whitewashed. Tapping an affordable project opens `BuildDialog`, which draws all three with the
real `drawProp` code (same function the diorama uses, so the preview can never lie), and the pick
is persisted in `trail_projects.variantIndex` (**DB v23**). It's permanent on purpose: a choice
you can undo isn't a choice.

A variant changes **material tint *and* one structural feature** — the roof, the canopy, the rail.
Recolours alone read as a skin; a different silhouette reads as a different build.

**Finding the thing you fixed.** The old complaint was "I can't see the well being fixed", and it
was fair: the scene was 200dp of small shapes with no way to connect a list row to a prop. Now:

- the diorama is **260dp** and **tappable** — tap near a prop and it's ringed, named on the canvas,
  and the rest of the scene dims to 35% so the eye lands on the right thing
- **tapping a built row** does the same from the other direction, and the caption under the scene
  names the style and its blurb
- built rows carry a **thumbnail** drawn with the same `drawProp`, so the list stops being text
- building something **auto-highlights it** — you close the dialog and immediately watch your
  choice grow into the place it now lives

`propPaletteFor(themeId, keystone)` is the single source of prop colours, shared by the scene, the
previews and the thumbnails, so a bought theme changes all three at once.

### Phase E notes

**Nothing is available at the start any more.** Features unlock from real progress
(`TrailProgression`), not a separate counter, so the state can't drift from what was actually
done:

| Feature | Unlocks at |
|---|---|
| Collect, projects | from the start |
| **Map** | 1 project built |
| **Tasks** | 3 projects built |
| **Keystones** | with Tasks (they cost ⭐, which only challenges supply — showing them sooner is noise) |
| **Shop** | 1 challenge claimed |

Tabs literally don't exist until their feature unlocks, and a `NextUnlockHint` says what's coming.

**Coach-marks** (`ui/component/Spotlight.kt`) dim the screen, punch a rounded hole around one
element (`BlendMode.Clear` on an offscreen layer), ring it with a pulse and explain it. Elements
opt in with `Modifier.spotlightTarget(state, id)`. Steps are ordered and shown once each, tracked
by a bitmask in `trail_state.tutorialSeen` (**DB v22**) — and each fires only when it's *relevant*
(collecting when there's something to collect; vitality the first time it actually drops), never
as an upfront wall of text.

**The map** (`TrailMap.kt`) is a winding road with a node per site: filled and haloed behind you,
a pulsing gold node with a completion arc where you are, unlit markers ahead, and the walked
section of road drawn brighter.

**First-run bug found and fixed while wiring this:** `evaluate()` used to return early on the very
first run, so today never ticked — a new player saw the welcome and then an empty site with
nothing to collect until the next day. It now sets the clock to yesterday *and* continues, so an
already-logged today pays out immediately.

### Phase A notes

- **Region 1 is authored** — 6 sites, 64 projects, each with 2 ⭐-gated keystones. Costs are
  generated from `baseCost × 1.25ⁿ` per site, so tuning is one number per site, not 64.
- **Sites 7+ generate procedurally** already (`TrailCatalog.siteAt`), so the trail can't
  dead-end even before Phase D.
- **The old Garden has been retired** (DB v21). `GardenScreen`, `GardenViewModel`,
  `GardenRepository`, `domain/Garden.kt`, `GardenDao` and its entities are deleted, along with the
  `Screen.Garden` route. Its two tables (`garden_state`, `collected_plants`) are **deliberately
  left in the database** — Room ignores tables it doesn't declare, so the old bloom history
  survives rather than being destroyed by a feature removal. `MIGRATION_20_21` is empty and
  exists only to acknowledge the schema-hash change.
- The Trail starts a **fresh wallet** (3 💧, 0 ⭐) rather than importing the garden's balance,
  to avoid double-counting while both systems exist.

### Phase B notes

- **`PropKind`** (19 kinds) lives in `domain/Trail.kt`; every project maps to one. Drawing is
  `ui/screen/trail/TrailProps.kt` — plain Canvas shapes, no assets. Adding a site means picking
  from the library, not drawing anything new.
- **`TrailScene.kt`** owns the landscape: two hill layers, the winding trail path, and **three
  depth bands** (back/mid/front) with atmospheric fade, so it reads as a place rather than a row
  of icons. Slot positions are deterministic per index — a site always looks the same.
- **Motion:** props grow in over 700ms when built (`animateFloatAsState` starts *at* its target,
  so opening the screen doesn't replay every past build); everything drifts on a shared 7s sway;
  keystone glows breathe on a 3.2s pulse.
- **Overgrowth** is driven by vitality: the palette desaturates toward grey-green, the path and
  hills dim, and creepers grow in from the edges — reaching further as vitality drops. Purely a
  visual layer over intact props, matching the "nothing built is ever destroyed" rule.
- Unbuilt projects show as a **faint ghost ring** so you can see the shape of what's still to come.

### Phase C notes

- **Four live slots**, not three: `daily_goal` is the permanent calorie-goal anchor, `daily_extra`
  rotates from a concrete pool, and `weekly` / `monthly` try a **creative AI** challenge first,
  falling back to concrete if the model is unavailable. Dailies stay concrete on purpose — an AI
  call per day is the flakiest, least valuable place to spend one.
- **11 concrete types** (`ConcreteType`) evaluated from a single `PeriodFacts` snapshot, so a
  period's facts are gathered once no matter how many challenges reference them. Thresholds scale
  off the user's own targets where it makes sense (protein/fibre).
- **Assignment is idempotent** — `(periodKey, slot)` primary key + `INSERT OR IGNORE`, so calling
  `ensureAssigned()` on every screen open can never reroll a challenge that's in play.
- **Claim re-verifies** concrete challenges at claim time, so the button can't pay out on stale
  progress.
- The AI judge is fed **only logged data** and told explicitly that missing evidence means NO;
  a decline stores its one-line reason, shown as *"Not yet — …"*.
- Home's streak-chip badge now lights for **growth waiting OR a claimable challenge**.
- Known duplication: `goalContext()` now exists in three repositories (Garden, Trail, Challenge).
  Worth extracting if a fourth appears.

### Phase D notes

**Seeds became curios.** The original plan had cases dropping *seeds* that biased your next
plant's species — but the Trail replaced plant-growing with site-restoring, so seeds had nothing
left to bias. The "collect and complete" motivator is rehomed as **curios**: 12 findable trinkets
across four rarities, shown as a grid with a completion %. Same hook, coherent with the journey.

- **Two ⭐ sinks that feel different:** scene **themes** are a sure thing you can see before
  buying; **cases** are the gamble. Both compete with keystone projects for the same ⭐, which is
  the interesting choice.
- **Cases always pay out** — an unowned theme, a curio, or a growth bundle scaled to your current
  production. A duplicate curio isn't a dud: it converts to growth so a roll is never wasted.
- **Themes are code-drawn palettes** (leaf / glow / keystone / path) applied to the diorama, so
  cosmetics needed no art. Buying equips immediately; the shop shows a live swatch of each.
- Curios are **purely collectible** — no gameplay effect, deliberately. Power creep from a
  loot box would undermine the "logging is the engine" rule.
