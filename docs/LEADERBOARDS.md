# Leaderboards

## 1. Purpose

Leaderboards is a read-only view of GameZone's own public leaderboard statistics - the same
Spelare/Settlements/Företag/Servern data any visitor can already see at
[gamezonemc.se/leaderboards](https://www.gamezonemc.se/leaderboards) - shown natively inside GZ
Companion's own UI, in a Minecraft-appropriate TOP 10 format, without needing to alt-tab to a
browser. It adds no gameplay mechanic and changes no server state; it is purely a convenience window
onto information that was already public.

## 2. Public source

**Authoritative source:** `https://www.gamezonemc.se/leaderboards` and its per-board pages, e.g.
`https://www.gamezonemc.se/leaderboards/player_coins`.

This was investigated by hand on 2026-09-13, browsing the real site and inspecting its actual
network traffic and rendered HTML (never probing anything hidden, authenticated, or admin-only -
see §8 Fair play). Findings, in order:

1. The site is a Next.js (App Router) application. Loading `/leaderboards` triggers no separate
   client-side `fetch()`/XHR to any leaderboard-data endpoint that a plain HTTP client could reuse -
   the visible table data is delivered as part of the server-rendered HTML response itself.
2. The page ALSO embeds a large, doubly-escaped JSON blob (React Server Components' internal
   "flight" streaming payload, used for client-side hydration) that happens to contain the same
   data in a clean, typed shape (`{"key":"player_coins","displayName":"Rikaste spelare",...}`).
   **This was deliberately NOT used as this feature's data source.** It is not a documented, public,
   or stable contract - it is Next.js's own internal wire format for its client runtime, has no
   content-type or URL of its own, and can change with any Next.js version bump independent of any
   visible change to the site a human would notice. Depending on it would be less resilient, not
   more, than reading the same rendered HTML a human sees.
3. No separate public JSON/REST API exists for this data. Per this feature's own design brief
   ("if the website itself only exposes rendered HTML, implement a narrow public-web adapter rather
   than pretending an API exists"), the rendered HTML is therefore the genuine, intended-for-humans
   public contract, and is what `GameZoneLeaderboardSource`/`LeaderboardHtmlParser` read.
4. `robots.txt` does not exist on the site (404) - no automated-access restriction is stated for any
   path.

## 3. Discovered public data contract

Three URL shapes, discovered by following the page's own "Visa hela tabellen" links and group tabs:

| Purpose | URL | Notes |
|---|---|---|
| Per-board full table (22 ranked boards) | `https://www.gamezonemc.se/leaderboards/<board-id>` | Page 1 always contains ranks 1-25 (25 per page), so top 10 never requires a second page fetch. `<board-id>` is GameZone's OWN slug, e.g. `player_coins`, `settlement_treasury`, `company_wealth`. |
| Server-wide stats (5 single-value boards) | `https://www.gamezonemc.se/leaderboards?tab=server` | The `?tab=` group query parameter server-renders that group's panel directly (verified: `?tab=players`/`?tab=settlements`/`?tab=companies`/`?tab=server` each SSR their own group's HTML; the default `/leaderboards` alone only SSRs Spelare). There is no per-board page for these 5 - each is a single current value, not a ranked list. |
| Overview page | `https://www.gamezonemc.se/leaderboards` | Human-facing overview with top-5 previews per board; not used by Companion directly (its previews are capped at 5, short of the required top 10). |

Markup shapes actually parsed (see `LeaderboardHtmlParser`), current as of 2026-09-13:

```html
<!-- Per-board full table row -->
<div class="...tableRow"><span class="...tableRank ...rank1">#1</span>
  <span class="...tableIdentity"><img .../><a class="...entityLink" href="...">Sylon</a></span>
  <strong class="...tableValue">32 389 617 coins</strong>
</div>

<!-- Server-stat single-value card -->
<article class="...boardCard ...serverBoardCard">
  <h3>Coin-ekonomi</h3>
  ...<div class="...identity">...<strong>GameZone</strong>...</div>
  <div class="...value"><strong>880 319 413 coins</strong></div>
</article>
```

GameZone's site uses CSS Modules, so the hash prefix in every class name (e.g. `page-module__k8x8IG__`)
changes on every GameZone deployment even when nothing else does. Every class match in
`LeaderboardHtmlParser` is therefore a **substring** check (`class contains "tableRow"`), never an
exact match - this is the parser's main resilience mechanism against GameZone's own build churn.

## 4. Groups

| Group | Boards | Query param used |
|---|---|---|
| SPELARE | 10 | `?tab=players` (also the page's own default) |
| SETTLEMENTS | 7 | `?tab=settlements` |
| FÖRETAG | 5 | `?tab=companies` |
| SERVERN | 5 | `?tab=server` |

**27 boards total** - a snapshot of what GameZone published on 2026-09-13, not eternal truth (see
`GameZoneLeaderboardRegistryTest`, whose count assertions must be updated together with the registry
if GameZone adds, removes, or renames a board).

## 5. All discovered board definitions

All identifiers, titles, descriptions, and value labels live in exactly ONE place,
`GameZoneLeaderboardRegistry` - never scattered across UI code, so a future board only ever needs one
new registry entry.

**Spelare** (`player_*`): Rikaste spelare (Coins), Högsta level (Level), Mest producerat (Items),
Mest spelad tid (Speltid, e.g. "11 d 13 h"), Flest kills (Kills), Flest vunna dueller
(Duellvinster), Flest värvningar (Värvningar), Monsterjägare (Bounties, primary; secondary = total
Coins earned from bounties - see §6), Flest deaths (Deaths), Högst K/D (K/D ratio).

**Settlements** (`settlement_*`): Rikaste settlement (Coins), Flest invånare (Invånare), Högst nivå
(Nivå), Mest skatt insamlad (Coins), Flest krigsvinster (Vinster), Flest krigsförluster (Förluster),
Bäst ticket-differens (Tickets).

**Företag** (`company_*`): Rikaste företag (Coins), Mest försäljning (Coins), Flest transaktioner
(Transaktioner), Högsta licensnivå (Licens), Största företag (Medlemmar).

**Servern** (`server_*`, single aggregate value each, no per-board page - see §3): Coin-ekonomi
(Coins), Aktiva settlements (Settlements), Aktiva företag (Företag), Aktiva idag (Spelare), Aktiva
denna vecka (Spelare). These 5 ids are Companion's own invention (chosen to match the internal key
names GameZone's own hydration data happens to use for the same concepts, but not sourced from any
public URL, since none exists for them).

## 6. Board-specific values

The domain model (`LeaderboardEntry`) never forces every board into one numeric primitive - GameZone's
own semantics and formatting (including non-breaking-space thousands grouping, e.g. `32 389 617`)
are preserved byte-for-byte in `primaryValue`/`secondaryValue`. Only `primaryLabel`/`secondaryLabel`
come from Companion's own registry, not scraped text. Monsterjägare is the one board that publishes
a combined `"15 bounties • 5200664 Coins intjänat"` string in a single cell - `LeaderboardHtmlParser`
splits on the `" • "` separator generically (not board-id-specific), so any other board using the
same convention gets the same treatment automatically.

## 7. Top 10 decision

Companion always shows **positions 1 through 10** when at least 10 legitimate records exist, and
only the actual records when fewer exist - it never fabricates a missing rank, and never shows more
than 10 just because GameZone's own page supports up to 25/page. This is why the per-board full-table
page (25/page) is used instead of the overview page's mini-cards (capped at 5) - page 1 of the full
table always contains at least the required top 10 in a single request.

## 8. Fair play

Leaderboards is read-only public information, identical to what any visitor to gamezonemc.se already
sees. It contacts only `https://www.gamezonemc.se/leaderboards...` - the same public pages a human
would browse. It was built without:

- authentication bypasses of any kind (no login, no session, no cookies)
- probing private/admin endpoints (only the public pages a normal visitor's browser reaches)
- reverse-engineering credentials or tokens (none exist for this public data)
- automating Minecraft commands or chat (`LeaderboardFairPlayTest` structurally enforces this by
  scanning the package's own source for forbidden APIs)
- packet inspection or GameZone player-session/cookie use

## 9. Privacy

Every request Companion sends is byte-for-byte identical regardless of who runs it or what they're
doing - it never includes the local Minecraft username, UUID, settlement, coordinates, inventory,
coins, local settings, or any telemetry. The request carries nothing but a plain
`GZ-Companion/<version>` User-Agent, exactly like the existing updater's `GitHubReleaseSource`.

"DU" highlighting (marking the local player's own row in a **player** leaderboard's already-downloaded
top 10) is a **local-only string comparison** against the Minecraft client's own already-known
username (`MinecraftBridge.getPlayerName()`) - the username is never sent anywhere as part of this.
Non-player boards (settlements, companies, the server itself) never attempt this: Companion never
guesses which settlement or company belongs to the local player. There is no hidden-player discovery,
no tracking/history database, and no background surveillance - data is fetched only while the
Leaderboards tab is open and a board is actually selected.

## 10. Network / cache policy

- Fetches happen **only** when Leaderboards is opened and a board is actually selected - never
  eagerly, never in the background, never on mod startup (unlike the updater's `UpdateManager`).
- `LeaderboardManager` caches each board's last successful result independently, keyed by board id.
  Switching between already-cached boards is instant (no refetch).
- Automatic refresh (opening a board, or cycling back to it) never refetches more than once per
  **60 seconds** per board, matching GameZone's own stated update cadence.
- The player may click "Uppdatera" to force a refresh sooner, but that itself is cooled down to
  **12 seconds** between clicks, so rapid clicking can't spam GameZone either.
- Two overlapping triggers for the same board (e.g. rapid selector cycling landing back on a board
  whose fetch is still in flight) are coalesced into a single request, never two concurrent ones.
- All network I/O runs on a dedicated background thread - never the render thread or the Minecraft
  tick thread.
- HTTPS only, to the single allowlisted host `www.gamezonemc.se`; the underlying `HttpClient` never
  follows redirects automatically (`Redirect.NEVER`) - if GameZone's server ever responds with a
  redirect, that is treated as a failure, never silently followed to a different host. A response
  size ceiling (a few MB) protects against an unexpectedly huge response.

## 11. Graceful degradation / source-change behavior

`LeaderboardStatus` distinguishes: `IDLE`, `LOADING`, `LOADED`, `STALE`, `UNAVAILABLE`, `ERROR`,
`INCOMPATIBLE`. A failure on one board never affects any other board's independent cache. If a
refresh fails but a previous successful fetch exists, the old data is kept and shown as
`SENAST HÄMTADE <time> sedan` - **never** relabeled `LIVE`. If GameZone restructures its page enough
that `LeaderboardHtmlParser` can no longer find its expected markup at all, that specific board (and
only that board) reports `INCOMPATIBLE` rather than crashing the tab or showing garbage; every other
board keeps working normally. `Leaderboards` itself is always considered an **available** module on
Home (like Online) - GameZone's website being temporarily unreachable is a live-data/network concern,
never a "this feature isn't implemented" concern.

## 12. Player heads

GameZone's own site uses `mc-heads.net` avatar images for player rows. Companion's V1 deliberately
does **not** depend on this or any other external image host - Leaderboards renders clean text/rank
rows only, with no additional network calls beyond the leaderboard page itself.
