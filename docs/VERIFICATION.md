# Verification record

Date: 2026-10-25. Application code verified at commit `00179d5`.

## Automated checks

- 23 JVM unit tests passed: 3 model/security tests, 12 provider tests, and 8 screen-state/localization tests.
- Debug and release Android lint passed with no correctness warnings/errors. Version and target-SDK migration notices are informational by policy.
- Debug APK and minified, unsigned release APK assembled successfully with JDK 21, compile/target API 36 and Build Tools 36.0.0.
- [GitHub Android CI passed](https://github.com/AntoCanza/iptv-receiver/actions/runs/40210306192) for the verified code commit.
- All 48 app-owned translatable strings are present in all ten languages, with matching format arguments.
- Before publication, source was checked for the supplied private connection values and access tokens; none were committed. GitHub secret scanning and push protection are enabled.

## Physical phone checks

Device: Pixel 9 Pro XL, Android API 37. Device serial numbers and account identifiers are intentionally excluded.

| Flow | Result |
| --- | --- |
| First launch on an Italian-language phone | App opened in English |
| HTTPS provider login | Successful; three home sections displayed |
| Remember connection | Encrypted save completed successfully before entering the session |
| Live TV -> category -> channel | Broadcast video displayed; H.264 video and AAC audio decoders initialized |
| Movies -> category -> details -> Play | Movie video displayed with playback progress and controls; H.264/AAC decoders initialized |
| Series -> category -> details -> season/episode | Episode video displayed; correct episode navigation and H.264/AAC decoder initialization observed |
| Leaving playback | Returned through the catalog to Home; one player used at a time |
| Language switching | Correct home labels observed for English, Italian, Spanish, French, German, Portuguese, Arabic, Russian, Simplified Chinese and Japanese |
| Session during language switching | Logged-in session retained |
| Test cleanup | App left at English Home; temporary USB screen-awake setting restored |

The supplied provider redirects video delivery to HTTP servers that do not accept HTTPS. Playback was checked with the per-connection **Allow HTTP streams** option enabled. Account/catalog requests remained HTTPS-only. Default-off HTTP restrictions were also exercised before compatibility was enabled.

The provider includes separator/intro entries among live channels. One such entry returned a short MP4 clip even from its HLS-labelled endpoint. A real broadcast channel was subsequently selected and displayed successfully using the progressive `ts` endpoint.

Decoder initialization and visible frames were verified. This record does not claim an independent listening test or native-speaker review. No raw provider responses, titles, stream URLs or playback screenshots are published here.

## Development APK

Version: `0.1.0`. Package: `io.github.antocanza.iptvreceiver`.

SHA-256 of the verified development APK:

```text
8734e41e46e60f669429935f62fb9350f43398c937afbe42bf90ef749d7353be
```

This APK uses development signing. Production signing and distribution remain owner-managed release work.

## Remaining release gates

Tablet layouts, Android 8.0/older supported devices, rotation continuity, network recovery, subtitle/audio-track combinations, large catalogs, and the minified release on hardware need further verification. Full catalog caching, durable watch progress, next-episode playback and Android TV are still planned. Review initial translations with native speakers and test mixed-direction metadata before release.
