# Video aspect ratios and playback metadata

Investigation date: October 27, 2026. Source baseline and installed development app: 0.1.10, versionCode 11. The repository pins Media3 1.11.1.

At this investigation baseline, the application could support automatic aspect ratios, zoom, stretch and explicit ratio overrides without changing the provider stream. Its existing player already read the stream's display dimensions automatically; the interface offered fullscreen and subtitles but no aspect-ratio picker or technical information panel. Provider catalog metadata and technical playback metadata are separate sources, and neither is complete for every item.

Version 0.1.11 subsequently implements display controls, subtitle/audio selection, stream information, a channel programme guide and advertised catch-up playback. See the [verification record](VERIFICATION.md) and [playback behavior](CACHE_AND_PLAYBACK.md) for the implementation and current device evidence; the observations below describe the initial 0.1.10 investigation.

## Aspect ratio controls

The current [playback screen](../app/src/main/kotlin/io/github/antocanza/iptvreceiver/ui/PlaybackScreen.kt) creates a standard `PlayerView` without changing its resize mode. Its default is Fit, confirmed on the phone. The player preserves the reported video proportions inside the available screen area. Fullscreen changes orientation and the available area; it does not force the video to 16:9.

| Proposed control | Display behavior | Implementation direction |
| --- | --- | --- |
| Auto / Fit | Preserve the stream's display aspect ratio and show the whole image, with bars where needed | `RESIZE_MODE_FIT` |
| Zoom | Preserve proportions while filling the available area, cropping overflowing image content | `RESIZE_MODE_ZOOM` |
| Stretch | Fill the available area even when its proportions differ from the video | `RESIZE_MODE_FILL` |
| Force 16:9, 4:3 or 21:9 | Override the reported display proportions, useful for incorrectly signaled streams | Override the content aspect ratio and keep the selected value when video-size events arrive |

Media3 exposes these resize modes and a protected `PlayerView.onContentAspectRatioChanged` hook for the content frame. A small custom view can apply the selected override there. Setting an outer Compose container to 16:9 alone only changes the available area; Fit can still retain the original video ratio inside it. [PlayerView reference](https://developer.android.com/reference/androidx/media3/ui/PlayerView), [AspectRatioFrameLayout reference](https://developer.android.com/reference/androidx/media3/ui/AspectRatioFrameLayout).

The display ratio uses both picture dimensions and pixel shape:

```text
display aspect ratio = video width × pixel width/height ratio ÷ video height
```

For example, 720×576 with a pixel ratio of 64/45 displays as 16:9. Width divided by height alone would misclassify it. Prefer the player's rendered `VideoSize` for display calculations; Media3 handles rotation internally. Its pixel ratio defaults to 1 when unknown. [VideoSize reference](https://developer.android.com/reference/androidx/media3/common/VideoSize).

Cinema material also uses ratios such as 2.39:1 and 2.40:1. The 21:9 preset is approximately 2.33:1, so Auto should retain the actual reported ratio. Bars encoded into a 16:9 picture remain part of that picture: aspect-ratio metadata does not identify the inner image. Removing those bars requires a user crop/zoom choice or separate image analysis.

Display resizing does not transcode the source, increase its detail, or change network bitrate. Stream-quality selection is a separate feature and requires alternative renditions from the provider.

## Metadata sources

| Source | Information it can describe | Current application behavior |
| --- | --- | --- |
| Provider catalog and title details | Titles, artwork, descriptions, year, rating, genres, credits, runtime, season and episode information | Mapped into catalog/detail models; the fields differ by response |
| Provider technical detail objects | Optional video/audio codec, dimensions, aspect ratios, frame rate, bitrate, audio channel layout and language tags | Technical `video`, `audio` and `bitrate` fields are currently discarded |
| Prepared media tracks | Actual available video, audio and text formats, selected tracks and decoder support | Media3 uses them internally; the app has no technical information view |
| Playback timeline and manifest | Duration, live/dynamic status, seekability, live window and available HLS renditions | Used by playback; no dedicated information panel |
| Embedded metadata | Optional static tags or timed events | No application listener or presentation for these events |

The [content model](../core/model/src/main/kotlin/io/github/antocanza/iptvreceiver/model/Catalog.kt) holds editorial metadata. Episodes retain descriptions, images and duration, with no technical track model. The [provider mapper](../core/provider/src/main/kotlin/io/github/antocanza/iptvreceiver/provider/XtreamCatalogRepository.kt) reads optional movie/episode `info` objects but maps only the implemented fields. The [playback engine](../app/src/main/kotlin/io/github/antocanza/iptvreceiver/playback/ExoPlaybackEngine.kt) creates an ExoPlayer, so technical track data is available during preparation and playback without adding provider requests.

Video formats can expose codec/MIME type, profile and level through the codec string, dimensions, pixel ratio, rotation, frame rate, declared bitrates, color information and bit depth. Audio formats can expose codec, sample rate, channel count/layout information, language and role/default flags. Text formats can expose encoding, language and accessibility information. Unknown values must remain unknown; compressed audio's PCM encoding field does not establish its source bit depth. [Media3 Format reference](https://developer.android.com/reference/androidx/media3/common/Format).

## Phone observations

The connected Pixel 9 Pro XL was tested with the installed application's production playback engine and saved provider connection. Temporary instrumentation created one player at a time, using a diagnostic callback instead of the app's watch-history callback. It did not replace the application APK or change persistent display settings. Eight playback sessions produced decoded video: four live sessions, two movies and two episodes. Each successful sample was observed for approximately five seconds after initial video frames.

| Sample | Video observed | Display aspect ratio | Audio observed |
| --- | --- | --- | --- |
| Four Live TV samples | H.264, 1280×720; frame rate and declared bitrate unknown | 16:9, pixel ratio 1 | AAC-LC stereo; 44.1 or 48 kHz; Italian or unspecified language |
| Movie A | H.264, 3840×2160, approximately 23.976 fps, 11.21 Mb/s average video bitrate | 16:9, pixel ratio 1 | AAC-LC stereo, 48 kHz, Italian, approximately 199 kb/s |
| Movie B | H.264, 1920×802, approximately 23.976 fps, 2.78 Mb/s | Approximately 2.395:1, pixel ratio approximately 1.0004115 | AAC-LC stereo, 48 kHz, unspecified language, approximately 97 kb/s |
| Episode A | H.264, 1920×800, approximately 23.976 fps, 2.89 Mb/s | 2.40:1, pixel ratio 1 | AAC-LC stereo, 48 kHz, Italian, approximately 98 kb/s |
| Episode B | H.264, 704×400, 25 fps, 1.01 Mb/s | 1.76:1, pixel ratio 1 | AAC-LC stereo, 48 kHz, unspecified language, approximately 49 kb/s |

These bitrates are fields reported by Media3, not network-throughput measurements. BT.709 and 8-bit video information appeared in some formats; other formats left color values unknown. The selected audio codec was AAC-LC in every successful sample. Surround sound, multiple selectable audio languages, HDR, exact 21:9, and 4:3 source playback were not established by this sample.

All successful samples exposed one video format and one audio track. Live tracks also included a CEA-608 declaration, and one included a DVB subtitle track. This establishes track availability reported by the extractor, not the presence or correctness of displayed subtitle cues; an assumed CEA-608 declaration can exist without transmitted captions. Neither sampled movie nor either successfully played episode exposed a subtitle track.

Each live HLS manifest exposed one variant, no separate audio/subtitle renditions, a changing window of approximately 50–60 seconds, no end marker and no programme-date-time anchor. Media3 reported live, dynamic and seekable. That duration describes the current live window, not the programme's full length or seven days of catch-up. The VOD samples reported finite durations and seekability.

MP4 video formats exposed static metadata entry types including `Mp4TimestampData`, `TextInformationFrame` and `ThumbnailMetadata`; audio formats included `Mp4AlternateGroupData`, timestamps and text entries. No timed `onMetadata` event arrived during the short successful observations. This does not establish that an entire channel or movie lacks timed metadata.

Runtime Fit, Zoom and Fill mode setters were accepted by the player view and reset to Fit in each successful session. This checks API availability; custom ratio overrides, visual crop boundaries, subtitles under zoom, rotation persistence and mode selection in the normal app still require implementation and device checks.

Additional attempts included one live parsing error (3002), one episode decoding error (4003), and one episode without video within the 45-second startup bound. The decoding failure's API described MPEG-4 Part 2 video at 624×352 and MP3 audio. It demonstrates that a technical description can exist even when that item fails to decode on this device. An initial diagnostic attempt used a model getter absent from the installed build; the corrected follow-up completed successfully and is excluded from provider/playback failures.

## Live TV and on demand differences

The sampled live listing contained channel identity/icon/category fields, `epg_channel_id`, `tv_archive` and `tv_archive_duration`, with no equivalent nested video/audio description. One channel advertised catch-up enabled and seven archive days. A separate `get_short_epg` request returned four programme entries containing title, description, language, start/end and timestamp fields. The current repository does not request this guide or map catch-up fields. Catch-up advertisement does not verify that archive playback works; that needs separate stream resolution and testing.

Both sampled movie detail responses had editorial metadata and a top-level `bitrate` field but no `video` or `audio` object. The playing MP4 nevertheless exposed detailed technical formats. A field's presence alone does not establish that its value is useful or current.

Episode detail responses were less uniform. Three of four sampled series supplied nested video/audio objects for the selected episode, including FFprobe-style codec names, dimensions, sample/display aspect ratios, frame rate, audio sample rate, channels/layout and optional bitrate/color fields. Another episode supplied only artwork in its `info` object. These differences occur within the same provider, so parsing must allow both rich objects and missing fields. Provider values can be stale or disagree with the delivered stream; prepared track information should take precedence during playback.

## Suggested implementation

1. Add a player display-mode menu with Auto/Fit, Zoom, Stretch and an optional explicit aspect-ratio override. Keep the selection in the retained playback ViewModel so rotation and reconnects preserve it. Keep scale mode and forced ratio conceptually separate.
2. Add a Stream information panel that observes `onTracksChanged` and `onVideoSizeChanged`, initializes from current player values, and displays video/audio/text formats separately. Use ExoPlayer input-format analytics for changes within an adaptive group. Show unknown values explicitly and distinguish source channels from the device's audio output. [Track inspection and selection](https://developer.android.com/media/media3/exoplayer/track-selection).
3. Keep playback technical state in the Android player layer. If pre-playback information is useful, add optional platform-independent technical models and map provider video/audio objects behind `CatalogRepository`, with synthetic parsing fixtures. Label those values as provider-reported until the stream is prepared.
4. Add guide and catch-up models separately. EPG describes programmes and schedules; track metadata describes the delivered media. Use the provider's guide APIs for programme titles and timestamps rather than depending on embedded stream tags.

The next device checks should use known 4:3 and anamorphic sources, a cinema image with encoded bars, multiple audio languages, 5.1 audio, actual subtitle cues, dynamic video-size changes and orientation changes. No user-facing playback feature was implemented in this investigation. The temporary diagnostic package was removed after testing; provider credentials, response bodies, content identifiers, titles and media URLs are excluded from this report.
