"""Validate the published HTML, sitemap and local resources without dependencies."""

from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.parse import unquote, urljoin, urlsplit
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1] / "docs"
SITE_URL = "https://antocanza.github.io/iptv-receiver/"
LANGUAGE = "en"


class Page(HTMLParser):
    def __init__(self, source):
        super().__init__(convert_charrefs=True)
        self.tags = []
        self.ids = set()
        self.schemas = []
        self.schema = None
        self.feed(source)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.tags.append((tag, attrs))
        if "id" in attrs:
            assert attrs["id"] not in self.ids, f'Duplicate id: {attrs["id"]}'
            self.ids.add(attrs["id"])
        if tag == "script" and attrs.get("type") == "application/ld+json":
            self.schema = ""

    def handle_data(self, data):
        if self.schema is not None:
            self.schema += data

    def handle_endtag(self, tag):
        if tag == "script" and self.schema is not None:
            self.schemas.append(json.loads(self.schema))
            self.schema = None

    def select(self, tag, **attrs):
        return [a for t, a in self.tags if t == tag and all(a.get(k) == v for k, v in attrs.items())]


def main():
    pages = {}
    assert (ROOT / ".nojekyll").is_file(), "Missing .nojekyll"
    for path in ROOT.rglob("*.html"):
        if re.fullmatch(r"google[0-9a-f]+\.html", path.name):
            continue
        pages[path] = Page(path.read_text(encoding="utf-8"))

    sitemap = ET.parse(ROOT / "sitemap.xml").getroot()
    namespace = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
    assert sitemap.tag == namespace + "urlset", "Invalid sitemap namespace"
    locations = [node.text for node in sitemap.findall(f"{namespace}url/{namespace}loc")]
    assert locations and len(locations) == len(set(locations)), "Empty or duplicate sitemap URLs"
    canonical_urls = set()
    titles = set()

    for path, page in pages.items():
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT).as_posix()
        expected_url = urljoin(SITE_URL, relative.removesuffix("index.html"))
        assert page.select("html", lang=LANGUAGE), f"Wrong language: {relative}"
        assert page.select("meta", name="viewport"), f"Missing viewport: {relative}"
        title = re.findall(r"<title>(.*?)</title>", source, re.DOTALL)
        assert len(title) == 1 and title[0].strip(), f"Invalid title: {relative}"
        assert len(page.select("h1")) == 1, f"Expected one h1: {relative}"
        robots = page.select("meta", name="robots")
        assert len(robots) == 1, f"Missing or duplicate robots meta: {relative}"
        directives = {part.strip().lower() for part in robots[0].get("content", "").split(",")}
        if relative == "404.html":
            assert "noindex" in directives, "404 must be noindex"
        else:
            assert not directives.intersection({"noindex", "none", "nofollow"}), f"Blocked page: {relative}"
            assert title[0] not in titles, f"Duplicate title: {relative}"
            titles.add(title[0])
            description = page.select("meta", name="description")
            assert len(description) == 1 and description[0].get("content"), f"Missing description: {relative}"
            canonical = page.select("link", rel="canonical")
            assert len(canonical) == 1 and canonical[0].get("href") == expected_url, f"Wrong canonical: {relative}"
            assert page.select("meta", property="og:url", content=expected_url), f"Wrong Open Graph URL: {relative}"
            assert page.select("link", rel="sitemap"), f"Missing sitemap link: {relative}"
            canonical_urls.add(expected_url)

        for tag, attrs in page.tags:
            if tag == "img":
                assert "alt" in attrs, f"Missing image alt: {relative}"
                assert all(attrs.get(k, "").isdigit() for k in ("width", "height")), f"Missing image dimensions: {relative}"
            attribute = "src" if tag in {"img", "script"} else "href"
            link = attrs.get(attribute)
            if not link:
                continue
            parsed = urlsplit(urljoin(expected_url, link))
            if parsed.scheme != "https" or parsed.netloc != urlsplit(SITE_URL).netloc:
                continue
            prefix = urlsplit(SITE_URL).path
            if not parsed.path.startswith(prefix):
                continue
            target = (ROOT / unquote(parsed.path[len(prefix):])).resolve()
            assert target.is_relative_to(ROOT.resolve()), f"Path escapes site: {link}"
            if parsed.path.endswith("/"):
                target /= "index.html"
            assert target.is_file(), f"Broken local URL in {relative}: {link}"
            if parsed.fragment and target in pages:
                assert unquote(parsed.fragment) in pages[target].ids, f"Broken anchor in {relative}: {link}"

    assert set(locations) == canonical_urls, "Sitemap must list exactly the indexable canonical pages"
    home = pages[ROOT / "index.html"]
    for name in ("og:title", "og:description", "og:image", "og:image:alt"):
        assert home.select("meta", property=name), f"Missing {name}"
    assert home.select("meta", name="twitter:card", content="summary_large_image"), "Missing share card"
    assert len(home.schemas) == 1, "Expected one JSON-LD graph"
    graph = home.schemas[0]
    assert graph.get("@context") == "https://schema.org", "Invalid schema context"
    nodes = graph.get("@graph", [])
    types = {t for node in nodes for t in ([node["@type"]] if isinstance(node["@type"], str) else node["@type"])}
    assert {"WebSite", "WebPage", "SoftwareApplication"} <= types, "Incomplete structured data"
    assert all(node.get("url") == SITE_URL for node in nodes), "Wrong structured-data URL"
    assert all("aggregateRating" not in node and "offers" not in node for node in nodes), "Do not invent ratings or offers"
    print(f"Site checks passed: {len(pages)} HTML pages, {len(locations)} canonical sitemap URLs; metadata, JSON-LD, images and local links verified.")


if __name__ == "__main__":
    main()
