# Events

**Status: implemented; human in-game QA complete. Not yet part of a release (unreleased after `v0.1.0-alpha.10`).**

## 1. Purpose

The **Events** tab shows GameZone's upcoming scheduled events (Team Deathmatch, Survival,
HungerGames, ...) inside GZ Companion: the next event highlighted with a countdown, then the coming
days grouped as "Idag", "Imorgon", "Lördag 17 oktober", ...

The data comes from GameZoneEngine's public events API, which is generated from the **same schedule
source as the in-game hologram**. Companion never scrapes Minecraft, reads the hologram, or
hard-codes event times.

## 2. Source

`GET http://162.120.2.221:25569/api/v1/events/schedule` - public, read-only, no API key.
`?days=30` exists but is not used: the default 7-day window covers "today, tomorrow and the coming
week", which is what the tab shows.

Real response (2026-10-15, 23:28 Stockholm, shortened):

```json
{"status":"success","timeZone":"Europe/Stockholm","data":[
  {"date":"2026-10-15","eventName":"Team Deathmatch","startTime":"20:00"},
  {"date":"2026-10-15","eventName":"Survival","startTime":"21:00"},
  {"date":"2026-10-16","eventName":"Survival","startTime":"20:00"}, ...]}
```

Observed headers: `HTTP/1.1 200`, `Content-Type: application/json; charset=utf-8`,
`Cache-Control: no-store`, `Access-Control-Allow-Origin: *`; about 1 KB for 14 events (two per day).
Note that the response **includes today's events even after they started** - Companion filters
them out (section 5).

## 3. Architecture (`se.jimmyeliasson.gzcompanion.events`)

Mirrors Bounty Board's proven structure; DTO and UI model are separate:

| Class | Role |
|---|---|
| `EventScheduleDto` | The API response as delivered: status, timeZone, raw `data[]` strings. |
| `EventScheduleJsonParser` | JSON -> DTO. Rejects the whole response unless `status == "success"`, `timeZone` is a real IANA zone, and `data` is an array. |
| `EventSchedule` / `EventScheduleEntry` | The parsed model: every usable event as a `ZonedDateTime` in the schedule zone, sorted by real start time (stable, so API order is kept for equal times). |
| `EventScheduleSource` / `GameZoneEventScheduleSource` | One read-only GET; returns a sealed `EventFetchResult` (Success / Unavailable / Incompatible), never throws. |
| `EventScheduleManager` | Cache + fetch state machine; `EventScheduleSnapshot` + `EventScheduleStatus`. |
| `EventScheduleView` | Pure presentation for one instant: upcoming filter, next event, day groups, Swedish headings, countdown text. |
| `ui.tabs.EventsTabComponent` / `ui.layout.EventsLayout` | Rendering only - no HTTP or JSON. |

Networking uses the shared `GameZoneLiveDataRuntime` (no new executor or HTTP client; enforced by
`ThreadingInfrastructureRulesTest`). Nothing is created or fetched until Events is opened.

### Transport

Same policy as Bounty Board: allowlisted host only (`162.120.2.221`), no redirects (a 3xx is a
failure), an 8 s request timeout on top of the shared 10 s connect timeout, and a 200 000-character
response ceiling (checked on `Content-Length` and on the body). The request pins HTTP/1.1 so the
shared client never sends an `h2c` upgrade to the small API server. The endpoint is plain HTTP on a
fixed IP, as GameZone publishes it; the data is public and display-only, and the request carries no
credentials, cookies or player identity - only the shared static User-Agent.

## 4. Cache and refresh

- First open of Events starts one background fetch.
- The latest successful schedule is cached in memory for the session.
- While Events is open, `ensureFresh()` refetches at most once per **5 minutes** (`REFRESH_TTL`);
  calling it every frame is a cheap no-op in between. No background polling when the tab is closed.
- **Uppdatera** (footer) and **Försök igen** (error state) bypass the TTL but not a **10 s**
  cooldown, so clicking repeatedly can never spam the endpoint.
- At most one fetch is in flight; extra requests are coalesced, never queued.
- Passed events drop out and countdowns tick every frame from the cached schedule - no refetch is
  needed for that.

## 5. Display and time rules

- `date + startTime` is interpreted as local time in the response's `timeZone` (Europe/Stockholm)
  with `ZonedDateTime.of`, so DST follows the zone rules - no fixed `+01:00`/`+02:00`. A time in the
  spring-forward gap moves forward by the gap; an ambiguous autumn time takes summer time.
- An event is **upcoming until its start instant**; once started it is never shown as upcoming.
- Day headings and clock times use the **schedule's** date and time (as on the in-game hologram),
  whatever the player's system zone is. If the player's current UTC offset differs from
  Stockholm's, the footer adds "Tider i svensk tid".
- Headings: "Idag", "Imorgon", then e.g. "Lördag 17 oktober" (fixed Swedish name tables, so a
  runtime without locale data can never fall back to English).
- Countdown ("relative") text: "Börjar nu"; "Om 42 min"; "Om 2 h 15 min" (today, or within 3 hours);
  "Imorgon 20:00"; a weekday within the week ("Lördag 21:00"); else "22 okt 20:00". Minutes are
  rounded up, never "Om 0 min". Countdowns are computed between real instants, so they stay
  correct across DST changes (00:30 -> 03:30 on 2026-10-25 is "Om 4 h").

## 6. Failure behaviour

Nothing here can crash the Companion UI; every failure is a status:

| Situation | With a cached schedule | Without one |
|---|---|---|
| Timeout, HTTP error, unreachable, redirect, oversized | Keeps showing it, badge **CACHAD**, footer "Visar senast hämtade schema" (yellow) | "KUNDE INTE HÄMTA EVENTSCHEMAT" + **Försök igen** |
| Malformed JSON, `status != "success"`, missing/unknown time zone, missing `data`, no usable entries | same | "EVENTSCHEMAT KUNDE INTE LÄSAS" + **Försök igen** |
| Valid but empty `data`, or every event already passed | "INGA KOMMANDE EVENTS" | same |

Individual entries with a missing/invalid name, date or time are skipped (never guessed); the rest
still load.

## 7. Layout

Header (title, freshness badge LIVE / UPPDATERAR / CACHAD / OTILLGÄNGLIG) -> highlighted
"NÄSTA EVENT" card (countdown, name, "Imorgon • 20:00") -> scrollable list grouped by day (next
event highlighted) -> footer (status text + Uppdatera). Verified by `EventsLayoutTest` from the
320x240 minimum through COMPACT, NORMAL and LARGE; the list scrolls with the mouse wheel when it
does not fit. Long event names are ellipsized.

## 8. Fair play

Read-only display of public schedule data. No POST/PUT/DELETE, no credentials, no world/entity/
hologram reading, no chat or commands - enforced by `EventsFairPlayTest`.
