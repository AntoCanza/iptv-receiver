# Languages and localization

The device language is the first-run default; English is the complete fallback resource set for unsupported languages. The login language icon and Settings language picker offer Use device language plus ten explicit choices. Android 13+ also exposes these choices in system per-app language settings. AppCompat stores the selection on older Android versions and the platform on newer versions. An empty app locale list follows the device's ordered locale preferences.

On the first upgrade from the starter's forced-English default, an old English selection is cleared once. The starter did not distinguish a seeded default from a manual English choice. After this one-time migration, new explicit choices are preserved, including English. A user can always choose Use device language again.

| Language | Locale tag | Resource directory |
| --- | --- | --- |
| English | `en` | `values` |
| Italian | `it` | `values-it` |
| Spanish | `es` | `values-es` |
| French | `fr` | `values-fr` |
| German | `de` | `values-de` |
| Portuguese | `pt` | `values-pt` |
| Arabic | `ar` | `values-ar` |
| Russian | `ru` | `values-ru` |
| Simplified Chinese | `zh-Hans` | `values-b+zh+Hans` |
| Japanese | `ja` | `values-ja` |

The initial Portuguese translation uses European Portuguese. Chinese support here means Simplified Chinese; Traditional Chinese has not been added.

## Scope

App-owned navigation, login labels, search, empty states, playback feedback, resume/history progress, watched/rewatching status, season counts, manual marks, clearing confirmations, subscription reminders, display/stream information panels, programme-guide controls and errors have translated resources in all ten languages. Utility/player icons retain translated accessibility labels and long-press help. Numbered track fallbacks, optional connection-name labels, relevance sorting and schedule-conflict messages are localized. Provider errors are stable `FailureReason` codes translated at display time; raw provider responses are never shown. Native Media3 controls and track selectors use their own translations.

Provider category names, channel names, movie titles, series names, descriptions and episode titles remain as delivered by the service. Translating the interface does not create translated metadata, dubbed audio or subtitles. Audio/subtitle availability depends on the stream.

The app supports right-to-left layout with `android:supportsRtl=true`, start/end positioning, and Compose layout direction. System fonts provide Arabic, Cyrillic, Chinese and Japanese glyph coverage. Visual review on actual devices is still required for long text, line wrapping, mixed-direction titles and playback controls.

## Changing or adding translations

1. Add/change the English string in `values/strings.xml`.
2. Update each translated `strings.xml`; keep the same keys and numbered format placeholders.
3. Keep the language picker, `locales_config.xml`, Gradle resource filters and localization tests in sync if adding a locale.
4. Run the localization unit tests and Android lint.
5. Review the affected screens in that language, including an error and an empty state.

Use resources instead of hardcoded user-facing strings. Language names in the picker are displayed in their native form so a user can recover from an unfamiliar interface language. `IPTV Receiver` is a non-translated product name.

Home-label switching was checked on a physical Pixel for all ten languages. Automated tests verify completeness and formatting, not linguistic quality or full visual layout. Native-speaker review of these initial translations remains part of release validation.

Implementation follows [Android per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages), using `AppCompatActivity` and `AppCompatDelegate.setApplicationLocales` for Compose compatibility on Android 12 and earlier.
