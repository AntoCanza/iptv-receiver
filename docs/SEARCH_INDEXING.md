# Project website and Google indexing

The public project page is **https://antocanza.github.io/iptv-receiver/**. GitHub Pages publishes the `docs` folder on `main` using the branch publishing source. `docs/.nojekyll` keeps the HTML and assets unchanged. No JavaScript, external font service or app login is required to read the page.

The GitHub repository remains the source of code and technical documentation. Its About section links to the project page, and the README links to both. GitHub controls the HTML, response headers and robots rules for `github.com`; repository files cannot change those rules.

## Included search signals

- Descriptive English title, description, one main heading and crawlable HTML links.
- An HTTPS canonical URL, matching Open Graph URL and absolute image URLs.
- WebSite, WebPage and SoftwareApplication JSON-LD describing the actual Android development status, current source version and real rolling GitHub APK download. There are no invented ratings, prices or Google Play listings.
- A UTF-8 [sitemap](sitemap.xml) containing the public canonical page. Dates are omitted instead of reporting a new modification date on every deployment.
- A custom 404 page with `noindex`; GitHub Pages returns HTTP 404 for missing paths.
- Responsive styling, explicit image dimensions and locally hosted images. The screenshot is an offline fictional catalog already approved for the store preparation package.

Run `python tools/check_site.py` after changing the page, paths, images or sitemap. The site workflow runs this check without building the Android app. Android CI continues to run its existing checks.

## Google Search Console

The repository owner must use their own Google account to verify the website. Repository access alone does not grant access to Search Console.

1. Add the **URL-prefix property** `https://antocanza.github.io/iptv-receiver/` in [Google Search Console](https://search.google.com/search-console/). A DNS property for `github.io` is not appropriate for this project path.
2. Use the HTML-file or HTML-meta-tag verification method. Put Google's actual verification file in `docs/`, or its exact verification meta tag in `docs/index.html`, then commit and publish. Keep verification in place afterward.
3. Submit `https://antocanza.github.io/iptv-receiver/sitemap.xml` in the Sitemaps report.
4. Inspect the home URL, run the live test and request indexing. Monitor the Page indexing report for Google's decision.

A `robots.txt` at `/iptv-receiver/robots.txt` would not control crawling. Crawlers only use the host-root `https://antocanza.github.io/robots.txt`, which this repository cannot publish. Its absence permits crawling; no project-level robots file is required. If an account-level site later adds restrictive rules, check them again.

Google decides whether and when to index eligible pages. Neither a sitemap nor this implementation guarantees indexing or ranking. Rich-result eligibility is separate from general indexing; the app has no public offer or ratings yet.

## Maintenance

Keep the page, [README](../README.md) and [user guide](USER_GUIDE.md) aligned with implemented features. Check the visible version and JSON-LD `softwareVersion` against `app/build.gradle.kts`; keep the APK link on the stable rolling-release URL. Update feature descriptions, FAQs and social descriptions together. Keep Android TV/desktop/native-TV plans distinct from the implemented Android client and retain the device-verification limits.

Update the canonical URL, sitemap, JSON-LD, social URLs and repository homepage together if the public address changes. Add new canonical HTML pages to the sitemap and link to them from existing pages; Markdown guides link to GitHub's rendered repository documentation. The 404 page and Google ownership file remain outside the sitemap. Preserve Google's existing ownership file unchanged. Never publish real provider credentials, private screenshots or the unfinished privacy-policy draft as the app's final policy.

Sources: [Google's technical requirements](https://developers.google.com/search/docs/essentials/technical), [crawlable links](https://developers.google.com/search/docs/crawling-indexing/links-crawlable), [sitemaps](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap), [robots.txt location](https://developers.google.com/crawling/docs/robots-txt/create-robots-txt), and [GitHub Pages publishing sources](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site).
