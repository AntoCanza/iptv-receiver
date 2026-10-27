# Xtream-compatible provider integration

Provider APIs vary; there is no assumed universal category depth or server-side pagination. Use this adapter as the compatibility boundary.

## Requests

Authenticated catalog requests use `/player_api.php` with URL-encoded `username` and `password` query parameters. Provider-specific differences belong in the adapter, not the UI.

| Operation | Action | Additional parameter |
| --- | --- | --- |
| Account validation | none | none |
| Live categories | `get_live_categories` | none |
| Live channels | `get_live_streams` | `category_id` for a category; omitted for all channels |
| Movie categories | `get_vod_categories` | none |
| Movies | `get_vod_streams` | `category_id` for a category; omitted for all movies |
| Movie details | `get_vod_info` | `vod_id` |
| Series categories | `get_series_categories` | none |
| Series list | `get_series` | `category_id` for a category; omitted for all series |
| Series details and episodes | `get_series_info` | `series_id` |
| Channel programme guide | `get_simple_data_table` | `stream_id` |

Login requires an authenticated, active account. A nonzero expiration timestamp is checked when present. String and numeric IDs are normalized to strings. Unknown fields are ignored; missing artwork remains optional.

## Metadata used by the interface

Catalog records retain optional year/release-date year, rating, genres, cast/director, added timestamp (or series last-modified timestamp), artwork and content details. Year/rating/genre/date values support compact title cards, local search, genre filters and sorting without per-title detail requests. Cast/director values enrich credits when opening a title; they do not add unrelated titles to default searches. Unknown ratings/dates remain absent and sort after known values; the default order remains the provider's order. Five-point ratings are converted to a ten-point scale when a usable ten-point rating is absent.

Movie and series details enrich available listing metadata rather than clearing fields missing from the detail response. Optional runtimes, countries, cast/director, backdrop images and web trailer links are used by the detail screen. Series detail artwork, separate season names/overviews, and episode descriptions/images/runtimes are supported. Runtime fields accept positive seconds, `HH:MM:SS`, or explicit runtime minutes. Null/sentinel/malformed metadata is omitted. Artwork arrays retain the same URL checks, transport rules and viewport gating as catalog posters. Trailer links reject local schemes and embedded credentials, and open externally only after a user tap.

Account responses optionally supply `active_cons`, `max_connections` and `is_trial`. Account settings labels the active connection count as a sign-in snapshot; it is not a live monitor. Server settings uses the configured base address/connection type plus optional validated `server_info.timezone` and `timestamp_now`. The server clock is a sign-in snapshot, shown in the reported zone, behind More details alongside supported live formats. Other raw server fields are discarded. Missing expiry/limits are not presented as unlimited. Rich metadata remains in memory; the account-scoped SQLite history stores only playback identifiers/titles/extensions and playback-start timestamps; separate progress rows store positions and watched state. See [storage behavior](CACHE_AND_PLAYBACK.md#durable-movie-and-episode-progress).

The channel guide maps `epg_listings` into programmes with validated start/stop epoch timestamps, decoded UTF-8 titles/descriptions and an optional strictly parsed provider-local start clock. It accepts plain text as well as Base64 text, omits invalid entries, sorts by start time and removes duplicate time ranges. The interface displays times in the device's zone and selected interface language.

Live listings map `tv_archive` and a positive, bounded `tv_archive_duration` into archive retention days. Replay requires an ended programme with `has_archive`, a valid provider-local start and a start within the advertised retention window. Missing fields never enable replay. Archive resolution uses `/timeshift/{username}/{password}/{duration_minutes}/{yyyy-MM-dd:HH-mm}/{stream_id}.ts`, with credentials encoded as separate path segments and duration rounded up to minutes. The original provider-local clock avoids shifting the requested broadcast when device and provider zones differ. Archive media is finite playback, clipped to the scheduled programme duration because provider TS timelines can report incorrect total lengths. Provider rejection or decoder errors retain the normal retry/error interface.

Guide loading and archive resolution pass through the parental repository. It authorizes the channel and verifies replay against a programme actually returned for that account/channel, rechecking policy at playback time. Historical channel records do not retain archive capabilities; the guide refreshes channel metadata before offering replay. Guide text, archive URLs and technical stream information remain outside durable history.

Authentication failures reported as `auth: 0` or HTTP 401/403 display a username/password rejection. Inactive and expired accounts retain their specific errors. Unexplained login/network/response failures use a neutral sign-in message that asks the user to check the server address, credentials and internet connection; these failures do not establish that the provider is offline or that the password is wrong. Catalog access errors remain separate from authentication rejection.

## Stream resolution

Common paths are:

```text
/live/{username}/{password}/{stream_id}.{allowed_live_format}
/movie/{username}/{password}/{stream_id}.{container_extension}
/series/{username}/{password}/{episode_id}.{container_extension}
```

Credentials are encoded as separate path segments. Never use the series ID in place of the episode ID. Use provider-supplied container extensions for movies/episodes instead of assuming MP4. Live playback prefers advertised HLS (`m3u8`), then MPEG-TS (`ts`), then another advertised format; absent formats fall back to `ts`. Format names are trimmed and normalized before selection. Progressive playback can sniff the actual container when a provider returns a different container; a `.m3u8` endpoint that returns MP4 cannot be parsed as an HLS playlist.

The app constructs common Xtream paths, including the validated `/timeshift/` archive path described above. Provider-specific direct URLs, custom stream headers, DRM licensing, alternative catch-up protocols, Stalker/MAG portals and M3U import are not implemented. Validate these needs before promising compatibility with another provider.

## Transport behavior

- HTTP and HTTPS base addresses are accepted for account and catalog requests, preserving the supplied scheme, port and base path. Base addresses containing credentials, query parameters or fragments are rejected.
- TLS certificate verification uses the platform defaults.
- Authenticated API redirects are rejected to avoid forwarding credentials to another origin.
- No HTTP logging interceptor is installed.
- Connection/read/call timeouts are bounded; coroutine cancellation cancels the call.
- Ordinary JSON and category-scoped catalog responses are limited to 20 MiB. Complete item catalogs are streamed one record at a time with a 128 MiB transfer limit; only mapped item metadata is retained, rather than the full raw JSON tree. Live TV, Movies and Series fetch their complete catalogs on the first root search or all-content selection and reuse them for that screen's lifetime. Closing the screen cancels outstanding requests. Providers exceeding the bound still need paging or incremental import work.
- JSON/parser/network errors are translated into safe messages without the response body, URL or exception cause.
- The player accepts HTTP and HTTPS manifests/segments and cross-protocol delivery redirects automatically. Artwork also accepts both schemes, trying HTTPS before an HTTP fallback. No per-connection HTTP checkbox or saved option is required. HTTP traffic, including credentials on HTTP login requests, is unencrypted.

## Private compatibility check

A supplied test connection was checked privately on 2026-10-25. HTTPS account authentication succeeded. Category responses contained 48 Live TV categories, 346 movie categories and 19 series categories; no non-root category parents were reported. The account advertised HLS (`m3u8`) and MPEG-TS (`ts`).

On-device testing on a Pixel 9 Pro XL confirmed login, category browsing, live broadcast playback, movie playback and series episode playback. Both live formats redirected to HTTP delivery hosts that did not accept HTTPS; the current app supports these redirects automatically. UI language switching was checked for all ten supported languages. See [verification](VERIFICATION.md) for the versions covered by device checks.

These are observations of one service at one time, not public fixtures or a guarantee of playback. No account values, server hostname, raw responses, titles or stream URLs are stored in this repository.

Additional provider/device checks should inspect only the metadata needed for playback formats and respect the account's connection limit. Never run these checks against a real account from public CI.
