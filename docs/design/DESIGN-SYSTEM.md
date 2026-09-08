# GZ Companion Design System

Authoritative visual specifications for GZ Companion, derived from [GZ-COMPANION-UI-REFERENCE.png](GZ-COMPANION-UI-REFERENCE.png).

---

## 1. Core Principles
- **Modern & Translucent**: Dark frosted-glass surfaces with high legibility and depth.
- **Emerald Accent**: Vibrant Swedish emerald / mint green as the primary identity and action color.
- **Structured Hierarchy**: Clean card-based layouts with clear information architecture.
- **Swedish-First**: Clear, natural, and accessible Swedish text throughout the UI.
- **Zero Vanilla Clutter**: Custom styled components without requiring external resource packs.

---

## 2. Color Palette & Semantic Tokens

### Backgrounds & Glass Surfaces
| Token | Hex / RGBA | Minecraft Int ARGB | Description / Usage |
| :--- | :--- | :--- | :--- |
| `SURFACE_OVERLAY` | `#B3070A0E` (70% dark) | `0xB3070A0E` | Full-screen backdrop tint behind the modal dialog |
| `PANEL_BG` | `#D90F171E` (85% navy dark) | `0xD90F171E` | Main container and dialog canvas |
| `CARD_BG` | `#9915222E` (60% dark slate) | `0x9915222E` | Card surface for content sections |
| `CARD_BG_HOVER` | `#B31E3142` (70% lighter slate) | `0xB31E3142` | Card surface on mouse hover |
| `NAV_ACTIVE_BG` | `#3310B981` (20% emerald) | `0x3310B981` | Active navigation pill background |
| `NAV_HOVER_BG` | `#261E293B` (15% slate) | `0x261E293B` | Inactive navigation item hover background |

### Accent & Identity Colors
| Token | Hex | Int RGB | Usage |
| :--- | :--- | :--- | :--- |
| `ACCENT_EMERALD` | `#10B981` | `0x10B981` | Brand leaf icon, active titles, primary buttons |
| `ACCENT_MINT` | `#34D399` | `0x34D399` | Player name highlight, active icons, glowing text |
| `ACCENT_GREEN_DARK` | `#059669` | `0x059669` | Primary button hover/press state |
| `ACCENT_BORDER` | `#059669` (50%) | `0x80059669` | Active item glowing border |

### Borders & Dividers
| Token | Hex / ARGB | Usage |
| :--- | :--- | :--- |
| `BORDER_SUBTLE` | `0x33334155` | Card outlines and subtle section dividers |
| `BORDER_MODAL` | `0x66475569` | Outer modal frame border |
| `BORDER_FOCUS` | `0xCC10B981` | Focused / active selection border |

### Typography Colors
| Token | Hex | Int RGB | Usage |
| :--- | :--- | :--- | :--- |
| `TEXT_PRIMARY` | `#F8FAFC` | `0xF8FAFC` | Main headings, modal titles, player name |
| `TEXT_SECONDARY` | `#94A3B8` | `0x94A3B8` | Subtitles, descriptions, metadata labels |
| `TEXT_MUTED` | `#64748B` | `0x64748B` | Footnotes, version numbers, disabled items |
| `TEXT_ACCENT` | `#34D399` | `0x34D399` | Highlighted values, positive status text |

### Status Indicators
| Status | Hex | Usage |
| :--- | :--- | :--- |
| `STATUS_ONLINE` / `VERIFIED` | `#22C55E` (Green) | Connected, verified, fully compatible, available |
| `STATUS_WARNING` / `STALE` | `#F59E0B` (Yellow) | Needs verification, stale rule data |
| `STATUS_INCOMPATIBLE` | `#EF4444` (Red) | Incompatible version, broken connection |
| `STATUS_COMING_SOON` / `UNAVAILABLE` | `#64748B` (Muted Grey) | Placeholder modules, future features |

---

## 3. Layout Grid & Spacing Scale
- **Modal Dimensions**: 92% of screen width (max 620px), 88% of screen height (max 400px), centered.
- **Sidebar Width**: 135px fixed left panel.
- **Content Area**: Flexible remaining width.
- **Padding Scale**:
  - `PAD_XS`: 3px
  - `PAD_SM`: 6px
  - `PAD_MD`: 10px
  - `PAD_LG`: 14px
  - `PAD_XL`: 20px
- **Corner Radii**: 4px – 6px rounded card bevels.

---

## 4. Components

### A. Navigation Sidebar
- 9 items with distinct icons:
  1. `Hem` (`¦` / House)
  2. `Guide` (`??` / Book)
  3. `Crafting` (`??` / Cube)
  4. `Kistor` (`??` / Chest)
  5. `Settlement` (`??` / Castle)
  6. `Byggplaner` (`??` / Blueprint)
  7. `MarketWatch` (`??` / Chart)
  8. `Kommandon` (`?` / Terminal)
  9. `Inställningar` (`?` / Gear)
- Active item rendered with emerald background highlight and emerald border.
- Sidebar footer with community signature.

### B. Header
- Top Left: Emerald leaf logo + "GZ COMPANION" + "Unofficial community project • Alpha".
- Top Right: Close button (`?`) / ESC shortcut indicator.

### C. Home Dashboard Cards
1. **Welcome Card**: Dynamically displays player head / name (`Välkommen, [PlayerName]`), intro text, gradient backdrop.
2. **Server Status Card**: Displays server profile (`GameZoneMC`), connection indicator (`? GameZone ansluten` / `? Inte ansluten`), server hostname (`play.gamezonemc.se`).
3. **Version & Compatibility Bar**: 4 badges for Minecraft, GZ Companion, Rule Pack, and Compatibility.
4. **Next Objective ("Nästa uppgift")**: Action title, summary, checklist, and quick-action buttons.
5. **Module Status ("Modulstatus")**: Live status dot list for all companion sections.

### D. Buttons
- **Primary Accent Button**: Solid `#10B981` background, `#0B1318` dark text, bold hover state (`#34D399`).
- **Secondary Glass Button**: Translucent `#1E293B` background with subtle border and icon.

### E. Global Footer
- Left: Shield icon + "Client-side • Fair play • Inga cheat-funktioner"
- Right: "GZ Companion 0.1.0-alpha • By the community, for the community."
