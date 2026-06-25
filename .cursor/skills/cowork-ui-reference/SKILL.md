---
name: cowork-ui-reference
description: Pixel-faithful Cowork mobile UI from /home/pixel/Desktop/Cowork-image-references/. Use when building or changing Proot-Cowork Compose UI, tabs, theme, or settings.
---

# Cowork UI Reference Spec

Match the reference mocks exactly. Source images: `Cowork-image-references/img_*.jpg`.

## Design tokens (mandatory)

| Token | Hex | Usage |
|-------|-----|--------|
| bg | `#0A0A0B` | Screen background |
| surface | `#141416` | Cards, composer |
| surfaceElevated | `#1C1C1F` | Nav bar, inputs |
| border | `#2A2A2E` | Card borders |
| mint | `#5EEAD4` | Active tab, accents, online dot |
| mintMuted | `#5EEAD4` @ 18% | Badges, chart bars |
| ubuntuOrange | `#E95420` | Logo circle |
| textPrimary | `#F8FAFC` | Titles |
| textSecondary | `#94A3B8` | Subtitles |
| textMuted | `#64748B` | Hints, timestamps |
| speakBg | `#FFFFFF` | Speak button |
| speakFg | `#0A0A0B` | Speak label |
| pending | `#FBBF24` | Schedule pending pill |
| done | `#4ADE80` | Done pill |
| failed | `#F87171` | Failed pill |

## Layout shell (every tab)

1. **Top bar** — hamburger | orange dot + `ubuntu` + teal status dot (center) | camera, refresh, power
2. **Desktop window** — 16:9 rounded frame, border, no title bar; state-based content or live X11
3. **Tab body** — scrollable content below desktop

16:9 rounded frame with **no title bar or timestamps**. Content only:
- **No container** — mint `+` import icon (tap to import)
- **Importing** — minimal progress indicator
- **Booting** — spinner + “Booting”
- **Off** — “Off” when stopped
- **Running** — live X11 clipped inside the frame
3. **Tab body** — scrollable content below desktop
4. **Tab-specific footer** — Chat: large composer; Terminal: `~ $ Enter command…` pill
5. **Bottom nav** — 6 tabs; active = mint icon + label + 2dp mint bar above icon

## Chat tab (img_f41e9e890fdc)

- Hero: 56dp rounded-square mint tint, robot icon, **Cowork agents ready**, hint text
- Quick chips: dark pills `#1C1C1F`, 20dp radius, muted text
- Composer: **large** box ~min 110dp, 24dp radius, placeholder **Ask anything**
- Composer footer: `+` circle | **Swarm** or **Fast** pill (bolt/flash + chevron) | **Speak** white pill (mic + label)

## Agents tab (img_2f635b5e1e5a)

- Card: **Active Agents** + `6 Online` mint badge
- Bar chart: 6 teal bars, labels Pln Res Exe Cod Val Sl
- Agent rows: 40dp icon tile, name + dot, role, `N tasks` right

## Files tab (img_cf8e891c3567)

- Breadcrumb: `home › coworker` (coworker in mint)
- Rows: yellow-outline folder tile / blue-outline file tile, name, size/date right
- Actions: New Folder | Upload | Select (text buttons)

## Schedule tab (img_65d0a984ce6d)

- **What should I schedule?** + dropdown field + mint **Schedule** button
- Task cards with status pills (Pending/Done/Failed)

## Skills tab (img_711dd7eae8d4)

- **ACTIVE (n)** / **AVAILABLE (n)** uppercase headers
- Skill cards with tags in mint pills, uses count

## Terminal tab (img_19fca41c3e63)

- Smaller desktop frame
- Monospace log, teal prompts
- Bottom pill input `~ $ Enter command…`

## Settings (img_c3205492d7b4)

- Section headers: mint icon + UPPERCASE label
- API card with rounded inputs
- Ubuntu card: check + Imported badge + trash; health 2×2 grid

## Motion

- Tab switch: horizontal slide + fade, spring damping 0.88
- Desktop height: animate between tab presets
- Nav selection: scale 1.05 + color tween 220ms

## Do not

- Generic Material3 defaults without token overrides
- Thin single-line chat bar (reference uses tall composer)
- Purple gradients or Inter-only generic AI slop
- Hiding desktop preview on any tab
