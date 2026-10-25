# Languages and localization

English is the first-run default and the complete fallback resource set. Users can change language with the picker in the app header. Android 13+ also exposes these choices in system per-app language settings. The selection is stored by AndroidX AppCompat on older Android versions and the platform on newer versions.

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

App-owned navigation, login labels, search, empty states, playback feedback and errors have translated resources. Provider errors are stable `FailureReason` codes that the Android UI translates at display time; raw provider responses are never shown. Native Media3 controls use the dependency's own translations.

Provider category names, channel names, movie titles, series names, descriptions and episode titles remain as delivered by the service. Translating the interface does not create translated metadata, dubbed audio or subtitles. Audio/subtitle availability depends on the stream.

The app supports right-to-left layout with `android:supportsRtl=true`, start/end positioning, and Compose layout direction. System fonts provide Arabic, Cyrillic, Chinese and Japanese glyph coverage. Visual review on actual devices is still required for long text, line wrapping, mixed-direction titles and playback controls.

## Changing or adding translations

1. Add/change the English string in `values/strings.xml`.
2. Update each translated `strings.xml`; keep the same keys and numbered format placeholders.
3. Keep the language picker, `locales_config.xml`, Gradle resource filters and localization tests in sync if adding a locale.
4. Run the localization unit tests and Android lint.
5. Review the affected screens in that language, including an error and an empty state.

Use resources instead of hardcoded user-facing strings. Language names in the picker are displayed in their native form so a user can recover from an unfamiliar interface language. `IPTV Receiver` is a non-translated product name.

The tests verify completeness and formatting, not linguistic quality or visual layout. Native-speaker review of these initial translations remains part of release validation.

Implementation follows [Android per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages), using `AppCompatActivity` and `AppCompatDelegate.setApplicationLocales` for Compose compatibility on Android 12 and earlier.
