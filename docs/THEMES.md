# Themes ("Utseende")

**Status: implemented; human in-game QA complete (Minecraft unchanged, Tydlig incl. Å/Ä/Ö, alignment, long Swedish text, minimum/normal sizes, instant switching, persistence and reset all verified). Released in `v0.1.0-alpha.12`.**

## 1. What it is

Inställningar -> **UTSEENDE** offers two themes. A theme only changes how GZ Companion looks -
never what any feature does ("Detta påverkar bara utseendet – inga funktioner ändras.").

| Theme | Look |
|---|---|
| **Minecraft (Standard)** | The original Minecraft-styled look, pixel-for-pixel unchanged from earlier versions. Default for everyone, including upgraders. |
| **Tydlig** | Readability-first: a smooth sans-serif font (DejaVu Sans), fully opaque panels (no world showing through behind text), clearly brighter secondary/muted text, stronger borders, square corners and no text drop shadows - still the dark GZ Companion identity with its emerald accents. |

Switching takes effect on the next frame - no restart, no screen rebuild.

## 2. Architecture

- `ui.theme.ThemeTokens` - every themed value in one record: surfaces, accents, borders, text and
  status colors, secondary-button and badge colors, text shadows on/off, chamfered vs. square
  corners, and the font. `ThemeTokens.MINECRAFT` holds exactly the pre-theme values (locked by
  `ThemeTokensTest`); `ThemeTokens.TYDLIG` is checked for WCAG AA contrast (>= 4.5:1) on every
  surface.
- `ui.theme.UiTheme` - the choices, with stable persisted ids (`minecraft`, `tydlig`) and
  `fromId` falling back to Minecraft for anything unknown.
- `ui.GZTheme` - the active tokens. Its `COLOR_*` fields keep their names (so every tab reads them
  unchanged) but are no longer compile-time constants: `GZTheme.applyTheme` rewrites them, and
  cards, buttons and badges draw from the tokens.
- `ui.layout.TextUtil` - the single place text meets the font. Every measure/ellipsize/wrap/draw
  applies the active theme's font and shadow policy, so measuring and drawing always agree.
- **No tab branches on the theme**: `ThemeSwitchingTest` fails if any class outside the theme layer
  (and the Settings selector) references `UiTheme`, `ThemeTokens` or `activeTheme()`.
- **Layout is not themed**: typography scales, row heights and spacing are identical in both
  themes, so switching can never move, clip or overlap a control (verified at the 320x240
  minimum, NORMAL and LARGE).

## 3. Font

`assets/gzcompanion/font/tydlig.json`: a TrueType provider for
`font/tydlig/dejavu_sans_subset.ttf` (DejaVu Sans subset to Latin/Swedish, punctuation, arrows,
shapes and the UI symbols - 115 KB) followed by a `reference` to `minecraft:default`, so any glyph
outside the subset still renders in Minecraft's font. DejaVu Sans' license (Bitstream Vera-derived,
DejaVu changes public domain) ships next to it as `license_dejavu.txt` and is listed in
`distribution/licenses/THIRD-PARTY-NOTICES.md`.

## 4. Persistence

`settings.json` key `uiTheme` (`"minecraft"` / `"tydlig"`), no schema bump. A missing key (every
file written before themes existed), an unknown/future value or a wrong type all load as Minecraft
without affecting any other setting. "Återställ inställningar" returns to Minecraft.
`SettingsManager` notifies a theme listener (wired to `GZTheme::applyTheme` by
`CompanionSession`) after load, every change and every reset.
