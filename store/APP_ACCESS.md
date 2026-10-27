# Reviewer access template

Enter the final values directly in Play Console's private App access section. Never add credentials to this repository.

1. Open IPTV Receiver. The interface follows the device language; use the login language icon or Settings → Language to select English if needed.
2. Enter the dedicated review connection name, HTTPS provider base URL, username and password supplied privately by the publisher.
3. Tap Connect. Choose Live TV, Movies or Series, then a category, or search across the section at its root. Select a movie and Play, or a series, season and episode.
4. Rotate during playback or use the fullscreen control. Back exits fullscreen before leaving the player. Review available display, audio/subtitle and stream-information controls.
5. Return to details/history to inspect progress and Resume/Start over. Open TV guide to inspect the provider's schedule and eligible replay, when supplied. These flows require compatible reviewer media.
6. Adult blocking is enabled by default. Provide any necessary dedicated reviewer parental instructions privately; never reuse a personal password. Reset restores adult blocking and removes all custom locks rather than recovering a password.
7. Settings → Account → Logout opens a confirmation. Confirm to remove the locally remembered connection. Viewing records remain local; Settings → Watch history and progress → Clear for this account removes them after a separate confirmation.

The publisher must provide a stable, non-expiring review account with legally cleared sample media, accessible from Google's review locations, without OTP, payment or geographic restrictions. Prefer HTTPS account/media/artwork addresses; HTTP also works automatically, with no compatibility switch, and is unencrypted. If any access restriction is unavoidable, explain it precisely in Console and verify the account regularly.

The debug screenshot fixture is not included in release builds and cannot provide review access. Do not publish a real user's account or use the user's private test subscription as a reviewer credential.
