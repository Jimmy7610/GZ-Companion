# GZ Companion Design System

Authoritative visual specifications for GZ Companion, derived from [GZ-COMPANION-UI-REFERENCE.png](GZ-COMPANION-UI-REFERENCE.png).

---

## 1. Core Principles & Rendering Model

### Critical Minecraft 26.1.2 ARGB Color Model
- **Explicit 32-bit ARGB**: In Minecraft 26.1.2, all color parameters passed into GUI rendering methods (`extractor.fill`, `extractor.text`, badges) must include an explicit Alpha byte (`0xAARRGGBB`).
- **Opaque Color Tokens**: All opaque colors (typography, borders, icons, status indicators) must use `0xFF` alpha (e.g., `0xFFF8FAFC`, `0xFF10B981`). An integer without alpha (`0x00RRGGBB`) renders completely invisible.
- **Glass / Translucent Tokens**: Surfaces deliberately declare intended transparency (e.g. `0xF0` for dialog panel, `0xCC` for cards, `0x80` for recessed wells).

### Icon System
- Due to font limitations in standard Minecraft clients, emojis (such as 👤, 📶, 🏰, 🎯) render inconsistently or as missing glyphs.
- GZ Companion uses a clean, dependable glyph/icon system with crisp ASCII/Latin-1 indicators (`#`, `?`, `+`, `=`, `^`, `%`, `$`, `/`, `*`) and Minecraft-supported formatting codes.

---

## 2. Color Palette & Semantic Tokens

### Backgrounds & Glass Surfaces (ARGB)
| Token | Hex / ARGB | Description / Usage |
| :--- | :--- | :--- |
| `COLOR_BACKDROP` | `0xB3070A0E` | 70% dark backdrop overlay dimming the game world |
| `COLOR_PANEL_BG` | `0xF00D141C` | 94% dark navy dialog container canvas |
| `COLOR_CARD_BG` | `0xCC131F2B` | 80% dark slate card container surface |
| `COLOR_CARD_HOVER` | `0xE61E2E3D` | 90% lighter slate on hover |
| `COLOR_CARD_INNER` | `0x800A1017` | 50% dark recessed inner wells (avatar, badges) |
| `COLOR_NAV_ACTIVE` | `0x4D10B981` | 30% emerald active navigation tab background |
| `COLOR_NAV_HOVER` | `0x331E293B` | 20% slate tab hover background |

### Accents & Identity Colors (Opaque ARGB)
| Token | Hex / ARGB | Usage |
| :--- | :--- | :--- |
| `COLOR_EMERALD` | `0xFF10B981` | Brand accent, primary action button, active tab markers |
| `COLOR_MINT` | `0xFF34D399` | Player name highlight, glowing text, high-visibility badges |
| `COLOR_EMERALD_DARK` | `0xFF059669` | Primary button hover / pressed state |
| `COLOR_BORDER_SUBTLE` | `0x33475569` | Card outlines and subtle section dividers |
| `COLOR_BORDER_MODAL` | `0x66475569` | Outer modal frame border |
| `COLOR_BORDER_EMERALD` | `0x9910B981` | Active item glowing border |

### Typography Tokens (Opaque ARGB)
| Token | Hex / ARGB | Usage |
| :--- | :--- | :--- |
| `COLOR_TEXT_PRIMARY` | `0xFFF8FAFC` | Headings, card titles, high contrast copy |
| `COLOR_TEXT_SECONDARY` | `0xFF94A3B8` | Body text, subtitles, field labels |
| `COLOR_TEXT_MUTED` | `0xFF64748B` | Footers, placeholders, inactive tabs |
| `COLOR_TEXT_ACCENT` | `0xFF34D399` | Mint highlighted values |
| `COLOR_TEXT_ON_EMERALD` | `0xFF04170E` | Dark contrast text on emerald buttons |

### Status Indicators (Opaque ARGB)
| Status | Hex / ARGB | Usage |
| :--- | :--- | :--- |
| `COLOR_STATUS_GREEN` | `0xFF22C55E` | Online, active, compatible, verified |
| `COLOR_STATUS_YELLOW` | `0xFFF59E0B` | Warning, stale, unverified server data |
| `COLOR_STATUS_RED` | `0xFFEF4444` | Incompatible, disconnected, error |
| `COLOR_STATUS_GREY` | `0xFF64748B` | Coming soon, placeholder, inactive |

---

## 3. Responsive Layout & Geometry

- **Modal Dimensions**: Responsive clamp `Math.min(width - 32, 540)` width by `Math.min(height - 32, 330)` height.
- **Centering**: Centered precisely on screen with ample visible game world backdrop.
- **Sidebar**: Fixed 108px left rail with consistent 18px tab buttons.
- **Content Area**: Flexible right panel with dense, balanced 6px-8px spacing grid.

---

## 4. Reusable UI Components
- `GZTheme.drawCard(...)`: Consistent bordered containers.
- `GZTheme.drawBadge(...)`: Status pills with 4x4 crisp status dots.
- `GZTheme.drawButton(...)`: Standardized primary and secondary buttons with calculated font centering.
- `GZTheme.drawStatusDot(...)`: Opaque pixel indicator.