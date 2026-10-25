# Xtream-compatible provider integration

Provider APIs vary; there is no assumed universal category depth or server-side pagination. Use this adapter as the compatibility boundary.

## Requests

Authenticated catalog requests use `/player_api.php` with URL-encoded `username` and `password` query parameters. Provider-specific differences belong in the adapter, not the UI.

| Operation | Action | Additional parameter |
| --- | --- | --- |
| Account validation | none | none |
| Live categories | `get_live_categories` | none |
| Live channels | `get_live_streams` | `category_id` |
| Movie categories | `get_vod_categories` | none |
| Movies | `get_vod_streams` | `category_id` |
| Movie details | `get_vod_info` | `vod_id` |
| Series categories | `get_series_categories` | none |
| Series list | `get_series` | `category_id` |
| Series details and episodes | `get_series_info` | `series_id` |

Login requires an authenticated, active account. A nonzero expiration timestamp is checked when present. String and numeric IDs are normalized to strings. Unknown fields are ignored; missing artwork remains optional.

## Stream resolution

Common paths are:

```text
/live/{username}/{password}/{stream_id}.{allowed_live_format}
/movie/{username}/{password}/{stream_id}.{container_extension}
/series/{username}/{password}/{episode_id}.{container_extension}
```

Credentials are encoded as separate path segments. Never use the series ID in place of the episode ID. Use provider-supplied container extensions for movies/episodes instead of assuming MP4. The first player prefers HLS when the account allows it, otherwise MPEG-TS.

The baseline constructs common Xtream paths. Provider-specific direct URLs, custom stream headers, DRM licensing, catch-up paths, Stalker/MAG portals and M3U import are not implemented. Validate these needs before promising compatibility with another provider.

## Transport behavior

- HTTPS is required for account and catalog requests.
- TLS certificate verification uses the platform defaults.
- Authenticated API redirects are rejected to avoid forwarding credentials to another origin.
- No HTTP logging interceptor is installed.
- Connection/read/call timeouts are bounded; coroutine cancellation cancels the call.
- Catalog responses are limited to 20 MiB; large catalogs require paging or incremental import work.
- JSON/parser/network errors are translated into safe messages without the response body, URL or exception cause.
- The player permits same-protocol redirects for delivery hosts but rejects cross-protocol redirects.

## Private compatibility check

A supplied test connection was checked privately on 2026-10-25. HTTPS account authentication succeeded. Category responses contained 48 Live TV categories, 346 movie categories and 19 series categories; no non-root category parents were reported. The account advertised HLS (`m3u8`) and MPEG-TS (`ts`).

These are observations of one service at one time, not public fixtures or a guarantee of playback. No account values, server hostname, raw responses, titles or stream URLs are stored in this repository. Streaming has not yet been verified on a physical device.

The next private verification should select one live channel, one movie and one episode; inspect only the metadata needed for their formats, then verify playback without exceeding the account's connection limit. Never run this against a real account from public CI.
