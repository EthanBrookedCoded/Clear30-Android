# Clear30 Design Language

Reference for what "good" looks like in this app. Distilled from the screens we consider best: the new Intro screens (`IntroScreenVariant`, `IntroScreenBasic`), `Support2`, `Profile` (progress tab), and `Achievements`.

Use this doc as a checklist when redesigning a screen that feels flat or off-brand. Every screen in the app should read as if it came from the same family.

---

## 1. The Four Principles

1. **Depth through blur, not clutter.** A soft gradient wash in the background gives every screen a premium base. We don't decorate — we diffuse.
2. **Color = meaning.** Each feature has a dedicated gradient. Red means craving/urgency. Blue means sleep. Gold means journals. Purple means Claire (AI). Never reuse a feature's gradient for an unrelated action.
3. **One card system, many variants.** Everything clickable is built from `CardStyle`. Variation comes from gradient/outline parameters, not from inventing new card shapes.
4. **Rhythmic spacing.** Spacing comes from `GlobalData.shared.cardSpacing` and its multiples. Never hardcode pixel values.

If a screen violates any of these, it will look like "the ass screens" — even if the individual elements are fine.

---

## 2. The Signature: Blurred Gradient Circles Background

This is the single most recognizable pattern in the app. Any hero/landing/intro surface should use it.

Source: `App/Clear30/Views/New User/Intro/IntroScreenVariants.swift:139`

```swift
func introGradientBackground(geometry: GeometryProxy) -> some View {
    ZStack {
        Color.clear30Background

        ZStack {
            Circle()
                .fill(Color.clear30Green)
                .frame(width: geometry.size.width * 2)
                .offset(
                    x: geometry.size.width / 2,
                    y: -geometry.size.height * 0.4
                )

            Circle()
                .fill(Color.clear30Blue)
                .frame(width: geometry.size.width * 2)
                .offset(
                    x: -geometry.size.width / 2,
                    y: geometry.size.height * 0.3
                )
        }
        .opacity(0.3)
        .blur(radius: 75)
    }
    .ignoresSafeArea()
}
```

### Why it works
- Circles are **2× screen width** — they spill off-canvas so you only see a soft falloff, never an edge.
- Offsets put green top-right and blue bottom-left — asymmetry is what keeps it from feeling like a stock gradient.
- `opacity: 0.3` + `blur(radius: 75)` is the magic combination. Higher opacity or lower blur reads as gaudy. Lower opacity disappears.
- Always layered **on top of** `Color.clear30Background`, not in place of it.

### When to use it
- Onboarding / intro / paywall screens — always.
- Empty states in existing-user tabs when the screen is mostly hero content.
- Do **not** use on dense list/feed screens — the blur fights the content. Those screens use `Color.clear30Background` flat.

### Variants that are OK
- Swap `clear30Green` / `clear30Blue` for another feature's two-tone palette (e.g. `redColor1` + `redColor2` for a craving-themed intro). Keep the opacity and blur values.
- Adjust offset fractions to bias the wash toward one side of the screen.

---

## 3. Design Tokens (`GlobalData.shared`)

All tokens live in `App/Clear30/Clear30App.swift:18`. **Never hardcode.**

### Spacing

| Token | Value | Use |
|---|---|---|
| `cardSpacing` | 14 | Gap between sibling cards inside a section |
| `cardSpacing * 2` | 28 | Gap between sections |
| `cardSpacing / 2` | 7 | Internal spacing inside a card (icon↔text) |
| `cardSpacing / 3` | ~4.6 | Tight label stacking |
| `horizontalPadding` | 25 | Screen-edge padding — every scroll view / VStack at the top level |
| `headingTopPadding` | 10 | Above the first screen heading |
| `scrollShadowFix` | 20 | Extra bottom padding in scroll views so card shadows aren't clipped |
| `buttonVerticalPadding` | 25 | Primary CTA tap target |
| `buttonHorizontalPadding` | 16.67 | Primary CTA side padding |

### Radii & motion

| Token | Value | Use |
|---|---|---|
| `cornerRadius` | 21 | All cards, all buttons. No exceptions. |
| `defaultAnimation` | `.default.speed(1.5)` | Most state transitions |
| `springAnimation` | `.spring(response: 0.3, dampingFraction: 0.4)` | Interactive taps, playful reveals |
| `defaultTransition` | scale w/ spring 0.175 | View insertion/removal |

### Haptics (don't forget these — they're half the polish)

- `GlobalData.shared.lightImpact()` — tapping a secondary card
- `GlobalData.shared.mediumImpact()` — primary button press
- `GlobalData.shared.successHeavy()` — achievement unlocked, milestone hit
- `GlobalData.shared.error()` — destructive confirm, failed action

---

## 4. Color System

### Base palette (Assets.xcassets)

| Color | Role |
|---|---|
| `clear30Background` | Screen background. Always the base layer. |
| `clear30Button` | Default card fill (light gray in light mode, dark in dark). |
| `clear30Text` | Default foreground. |
| `clear30Shadow` | The single shadow color — used everywhere, 7pt radius. |
| `clear30Blue` / `clear30Green` | Brand duo — used in the signature gradient. |
| `clear30Yellow` | Accent for flags, warnings, streaks. |
| `clear30OpacityGray` / `clear30OpacityGrayFlattened` | Disabled/inactive card fills. |

### Feature gradients

Each is a `LinearGradient` on `GlobalData.shared`. **One gradient per feature domain.** Think of these as categorical tags, not decoration.

| Gradient | Colors | Meaning |
|---|---|---|
| `clear30Gradient` | Blue → Green | Brand / neutral hero / app identity |
| `clear30GradientBright` | #26CD6A → #00BCA5 | Success / progress / positive milestones |
| `redGradient` | #f65555 → #fb5151 | Craving, urgency, "I need help now" |
| `sleepGradient` | #14435E → #1C5E80 | Sleep, rest, night content |
| `journalsGradient` | #f0d042 → #efcc34 | Journals / reflection / writing |
| `claireGradient` | #5C70EF → #8969FF | Claire (AI chat) |
| `communityGradient` | #a32eb8 → #b93fcf | Community / social / peer |
| `symptomCardGradient` | #FF8C59 → #FFA372 | Symptoms / body / physical |
| `supplementsGradient` | #F4BA66 → #FFC685 | Supplements |
| `meditationGradient` | meditation1 → meditation2 | Meditations |
| `redditGradient` / `youtubeGradient` / `instagramGradient` | brand duo | External content by source |

### Gradient vs. plain cards

A card either sits **on a gradient** or **on `clear30Button`** — pick one and commit.
- **Gradient cards**: white text, subtle/white iconography. Used for hero CTAs and the "primary action" of a section.
- **Plain cards**: `clear30Text` foreground, feature-colored icon. Used for list items and secondary actions.

### Opacity scale

Only use these values for text and tint opacities:

| Opacity | Role |
|---|---|
| `1.0` | Primary text, primary icon |
| `0.5` | Secondary text, helper labels, section dividers |
| `0.25` | Tertiary metadata, footnotes, "terms" copy |
| `0.12` (rare) | Disabled / inert state |

Per project rules, avoid intermediate values like `0.2`, `0.65`, `0.8`.

---

## 5. Typography

Font family: **Lexend** (custom). Defined in `Views/Components/Defaults/TextSizes.swift`.

| Style | Size | Weight | Use |
|---|---|---|---|
| `GiganticText` | 50 | medium | Milestone counters, paywall hero number |
| `HugeText` | 40 | bold | Day counter, streak display |
| `Heading1` | 32 | semibold | Screen title |
| `Heading2` | 25 | medium | Subtitle / sub-hero |
| `Heading3` | 22 | medium | Section header inside a screen |
| `DefaultText` | 19 | regular | Body copy, paragraph |
| `SmallText` | 15.5 | regular | Card titles, most card copy |
| `TinyText` | 12 | regular | Metadata, captions, card subtitles |
| `MiniText` | ~10 | regular | Badges, legal microcopy |

### Rules
- One `Heading1` per screen, at the top.
- Inside a card, the title is `SmallText` (not `DefaultText`) with optional `TinyText` subtitle at 0.5 opacity.
- Never mix two headings of the same level side-by-side — pick a primary and demote the other.

---

## 6. The Card System

Source: `App/Clear30/Views/Components/Defaults/Cards.swift:611`

Everything interactive wraps `content.modifier(CardStyle(...))`. Don't roll your own background + corner radius + shadow stack — use this modifier.

### Core parameters

| Param | Default | Notes |
|---|---|---|
| `color` | `.clear30Button` | Background when no gradient |
| `shadowColor` | `.clear30Shadow` | Set to `.clear` to kill the shadow |
| `cornerRadius` | `GlobalData.shared.cornerRadius` (21) | Almost never override |
| `gradient` | `nil` | Provide a `LinearGradient` to fill — foreground flips to white |
| `outlineGradient` | `nil` | Gradient border (great for earned/rare states) |
| `outlineWidth` | 3 | 1.5 for subtle, 3 for emphatic |
| `outlineOpacity` | 1 | Use 0.5 for a ghost outline |
| `glowGradient` | `nil` | Blurred halo behind the card — use sparingly, reveals/achievements only |
| `padding` | `true` | Set `false` when the content wrapper already pads |

### Four canonical variants

**A. Plain list card** — default neutral card for feed/list items.
```swift
HStack { /* icon + text */ }
    .modifier(CardStyle())
```

**B. Hero gradient card** — primary action in a section (Cravings, Sleep, Claire).
```swift
HStack { /* white icon + heading + body */ }
    .modifier(CardStyle(gradient: GlobalData.shared.redGradient))
```

**C. Outlined ghost card** — earned/rare states, optional-action cards.
```swift
content
    .modifier(CardStyle(
        color: .clear,
        shadowColor: .clear,
        outlineGradient: GlobalData.shared.clear30Gradient,
        outlineWidth: 1.5,
        outlineOpacity: 0.5
    ))
```

**D. Glow card** — achievement reveals, "new" states. The glow gradient sits behind as a blurred halo.
```swift
content.modifier(CardStyle(
    gradient: GlobalData.shared.clear30GradientBright,
    glowGradient: GlobalData.shared.clear30GradientBright
))
```

### Interaction rules
- Every tappable card gets `.modifier(DefaultButtonStyle(shadow: false))` on its `Button` wrapper so press-state scaling is consistent.
- Fire `GlobalData.shared.mediumImpact()` on primary card taps and `lightImpact()` on secondary taps.

---

## 7. Layout Patterns

### Screen shell

```
ZStack {
    introGradientBackground(geometry: geo)          // or Color.clear30Background for dense screens
    ScrollView {
        VStack(spacing: GlobalData.shared.cardSpacing * 2) {   // section rhythm
            // Heading1
            // Section 1 (VStack spacing: cardSpacing)
            // Section 2
            // ...
        }
        .padding(.horizontal, GlobalData.shared.horizontalPadding)
        .padding(.top, GlobalData.shared.headingTopPadding)
        .padding(.bottom, GlobalData.shared.scrollShadowFix)
    }
}
```

### Section anatomy

1. **Label row** — `Heading3` title on the left, optional `TinyText` link on the right, both at full opacity.
2. **Content** — either a VStack of cards at `cardSpacing`, or a 2-column grid of square cards.
3. **Divider** — 1pt line at `opacity(0.5)` between sections only when the visual grouping isn't already obvious.

### Good hero sections (how Support2 and Profile do it)

- Full-width gradient card at the top of the section.
- Large (44–54pt) icon inside a **white circle** on the left for contrast.
- Title in `SmallText` (white, weight .medium), subtitle in `TinyText` (white at 0.5 opacity).
- Primary action is the whole card — no inline buttons.

### Grids (Achievements, meditation library)

- 2 columns, equal width via `GridItem(.flexible(), spacing: GlobalData.shared.cardSpacing)`.
- Square aspect (`.aspectRatio(1, contentMode: .fit)`).
- Rarity/category tag in the top-right corner as a mini badge.
- Bottom label on a translucent strip — `SmallText`, centered, truncates with `.lineLimit(2)`.

---

## 8. Iconography

- SF Symbols everywhere. Custom images only for people/profile photos and brand logos.
- Icon sizing: **24pt** inside a standard card, **32pt** in a hero row, **44pt+** in a centered hero ZStack.
- On gradient cards: icons are white, optionally with `.shadow(color: .white.opacity(0.5), radius: 5)` for glow.
- On plain cards: icon color matches the feature gradient's primary color (e.g. `redColor1` for a craving link).
- Always use `.symbolRenderingMode(.hierarchical)` or `.palette` when the symbol has layers — never flat where the symbol was designed for depth.

---

## 9. Good vs. Ass Checklist

Run this on any screen you're redesigning. If you check all boxes, it'll fit the family.

### Background
- [ ] Either the blurred-gradient-circles background (hero screens) or flat `Color.clear30Background` (dense screens). Not a plain white/gray.

### Spacing
- [ ] Horizontal screen padding is `GlobalData.shared.horizontalPadding`.
- [ ] Section gap is `cardSpacing * 2`, card gap is `cardSpacing`. No raw numbers in `VStack(spacing:)` / `.padding(...)`.

### Cards
- [ ] All tappable surfaces wrap content in `.modifier(CardStyle(...))` — no ad-hoc `RoundedRectangle` + shadow stacks.
- [ ] Corner radius is `GlobalData.shared.cornerRadius` (21). Not 12, not 16, not 8.
- [ ] Shadow is `clear30Shadow` with the CardStyle default radius (7). No iOS-default shadows.

### Color
- [ ] Each feature uses its assigned gradient (see §4). No freelancing.
- [ ] Gradient cards use white text; plain cards use `clear30Text`.
- [ ] Opacities are only `1`, `0.75`, `0.5`, `0.25`, or `0.12`.

### Typography
- [ ] Lexend throughout (via the `Heading1` / `SmallText` / `TinyText` / etc. components, not raw `Text` with `.font(...)`).
- [ ] One `Heading1` at top. Section headers are `Heading3`. Card bodies are `SmallText`.
- [ ] Secondary labels at 0.5 opacity, tertiary at 0.25.

### Motion
- [ ] Animations use `GlobalData.shared.defaultAnimation` or `springAnimation`.
- [ ] Primary taps fire `mediumImpact()`; secondary taps fire `lightImpact()`.

### The vibe test
- [ ] Screenshot it next to Support2, Profile, and the Intro screen. Does it feel like the same app? If it looks flatter, grayer, or more generic than those three — something in the list above is wrong.

---

## 10. Reference Files

| Topic | File |
|---|---|
| All design tokens | `App/Clear30/Clear30App.swift:18` |
| Signature gradient background | `App/Clear30/Views/New User/Intro/IntroScreenVariants.swift:139` |
| CardStyle modifier | `App/Clear30/Views/Components/Defaults/Cards.swift:611` |
| Typography components | `App/Clear30/Views/Components/Defaults/TextSizes.swift` |
| Button styles | `App/Clear30/Views/Components/Defaults/Buttons.swift` |
| Exemplar: intro | `App/Clear30/Views/New User/Intro/IntroScreenVariants.swift` |
| Exemplar: support | `App/Clear30/Views/Existing User/Support/Support2.swift` |
| Exemplar: progress | `App/Clear30/Views/Existing User/Profile/Profile.swift` |
| Exemplar: achievements | `App/Clear30/Views/Existing User/Profile/Achievements/` |
