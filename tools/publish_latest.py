"""Publish a verified main APK to one rolling release, keeping the last good download on failure."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
APK_NAME = "iptv-receiver.apk"
CHECKSUM_NAME = APK_NAME + ".sha256"
TAG = "latest"


class GitHub:
    def __init__(self, repository: str, token: str):
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository) or not token:
            raise ValueError("A GitHub repository and token are required")
        self.base = "/repos/" + repository
        self.token = token

    def request(self, method, path, payload=None, *, binary=None, content_type=None, missing_ok=False):
        url = "https://api.github.com" + path if path.startswith("/") else path
        if urlsplit(url).scheme != "https" or urlsplit(url).netloc not in {"api.github.com", "uploads.github.com"}:
            raise ValueError("Unexpected GitHub API host")
        body = json.dumps(payload).encode() if payload is not None else binary
        headers = {"Accept": "application/vnd.github+json", "Authorization": "Bearer " + self.token,
                   "X-GitHub-Api-Version": "2026-03-10", "Content-Type": content_type or "application/json"}
        try:
            with urlopen(Request(url, data=body, headers=headers, method=method), timeout=90) as response:
                data = response.read()
                return json.loads(data) if data else None
        except HTTPError as error:
            if error.code == 404 and missing_ok:
                return None
            # Do not emit headers, tokens or raw error responses.
            raise RuntimeError(f"GitHub {method} {urlsplit(url).path} returned HTTP {error.code}") from None

    def pages(self, path, key=None):
        page = 1
        while True:
            response = self.request("GET", path + f"?per_page=100&page={page}")
            values = response[key] if key else response
            yield from values
            if len(values) < 100:
                return
            page += 1


def main_head(client):
    return client.request("GET", client.base + "/branches/main")["commit"]["sha"]


def point_tag(client, sha, exists):
    if exists:
        client.request("PATCH", client.base + "/git/refs/tags/" + TAG, {"sha": sha, "force": True})
    else:
        client.request("POST", client.base + "/git/refs", {"ref": "refs/tags/" + TAG, "sha": sha})


def upload(client, release, name, data, content_type):
    endpoint = release["upload_url"].split("{", 1)[0] + "?" + urlencode({"name": name})
    asset = client.request("POST", endpoint, binary=data, content_type=content_type)
    return asset


def check_upload(asset, data):
    expected = "sha256:" + hashlib.sha256(data).hexdigest()
    if asset.get("state") != "uploaded" or asset.get("size") != len(data) or asset.get("digest") != expected:
        raise RuntimeError("Uploaded release asset did not pass size/digest verification")


def prune_apk_artifacts(client):
    # Reports keep their seven-day retention. APK transfer/legacy APK artifacts are redundant after publication.
    for artifact in list(client.pages(client.base + "/actions/artifacts", "artifacts")):
        if artifact["name"] in {"unsigned-apk", "development-apk"}:
            client.request("DELETE", client.base + f'/actions/artifacts/{artifact["id"]}', missing_ok=True)


def publish(client, apk: Path, sha: str, build_id: str, version: str, version_code: int):
    if not re.fullmatch(r"[a-f0-9]{40}", sha) or not re.fullmatch(r"\d+-\d+", build_id):
        raise ValueError("Invalid workflow commit or build identifier")
    if main_head(client) != sha:
        print("A newer main commit exists; keeping the current release.")
        return False
    content = apk.read_bytes()
    if not content:
        raise ValueError("The signed APK is empty")
    checksum = (hashlib.sha256(content).hexdigest() + "  " + APK_NAME + "\n").encode()
    release = client.request("GET", client.base + "/releases/tags/" + TAG, missing_ok=True)
    if release and release.get("immutable"):
        raise RuntimeError("The rolling latest release must remain mutable")
    created = release is None
    if created:
        release = client.request("POST", client.base + "/releases", {
            "tag_name": TAG, "target_commitish": sha, "name": "Latest Android build", "draft": True,
            "prerelease": False, "make_latest": "false",
        })
    release_path = client.base + f'/releases/{release["id"]}'
    original_assets = list(release["assets"])
    old_ref = client.request("GET", client.base + "/git/ref/tags/" + TAG, missing_ok=True)
    staged = []
    tag_changed = False
    published = False
    try:
        for name, data, mime in [(APK_NAME, content, "application/vnd.android.package-archive"),
                                 (CHECKSUM_NAME, checksum, "text/plain")]:
            candidate = f"candidate-{build_id}-{name}"
            # A retry can remove only its own unfinished upload, never the working APK.
            for previous in original_assets[:]:
                if previous["name"] == candidate:
                    client.request("DELETE", client.base + f'/releases/assets/{previous["id"]}', missing_ok=True)
                    original_assets.remove(previous)
            asset = upload(client, release, candidate, data, mime)
            staged.append((asset, name))
            check_upload(asset, data)
        if main_head(client) != sha:
            print("Main advanced during upload; keeping the current release.")
            return False
        # Retain old assets under temporary names until both replacements and metadata are ready.
        for asset in original_assets:
            client.request("PATCH", client.base + f'/releases/assets/{asset["id"]}',
                           {"name": f'previous-{asset["id"]}-{asset["name"]}'})
        for asset, name in staged:
            client.request("PATCH", client.base + f'/releases/assets/{asset["id"]}', {"name": name})
        point_tag(client, sha, exists=old_ref is not None)
        tag_changed = True
        repository = client.base.removeprefix("/repos/")
        body = (f"Latest successful development build from `main`.\n\n"
                f"Version: **{version}** (Android versionCode {version_code}).\n\n"
                f"Source: [{sha[:12]}](https://github.com/{repository}/commit/{sha}).\n\n"
                f"Download **{APK_NAME}** below to install or update Android 8.0+ devices. "
                "The APK uses the existing app signing certificate so updates preserve local data.\n\n"
                "This release is replaced after the next successful main build. "
                "The accompanying SHA-256 file verifies the download.\n")
        client.request("PATCH", release_path, {"name": f"Latest Android APK · {version}", "body": body,
                                               "draft": False, "prerelease": False, "make_latest": "true"})
        published = True
    finally:
        if not published:
            # Upload or activation failure leaves the previous download and tag available.
            for asset, _ in staged:
                try:
                    client.request("DELETE", client.base + f'/releases/assets/{asset["id"]}', missing_ok=True)
                except RuntimeError:
                    pass
            for asset in original_assets:
                try:
                    client.request("PATCH", client.base + f'/releases/assets/{asset["id"]}', {"name": asset["name"]})
                except RuntimeError:
                    pass
            if tag_changed:
                if old_ref:
                    point_tag(client, old_ref["object"]["sha"], exists=True)
                else:
                    client.request("DELETE", client.base + "/git/refs/tags/" + TAG, missing_ok=True)
            if created:
                client.request("DELETE", release_path, missing_ok=True)
    for asset in original_assets:
        client.request("DELETE", client.base + f'/releases/assets/{asset["id"]}', missing_ok=True)
    for old in list(client.pages(client.base + "/releases")):
        if old["id"] != release["id"]:
            client.request("DELETE", client.base + f'/releases/{old["id"]}', missing_ok=True)
    prune_apk_artifacts(client)
    print(f"Published {APK_NAME} from main {sha[:12]}; previous release downloads removed.")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as output:
            output.write(f"[Download the latest APK](https://github.com/{repository}/releases/latest/download/{APK_NAME})\n")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", type=Path)
    parser.add_argument("--delete-artifact")
    arguments = parser.parse_args()
    client = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
    if arguments.delete_artifact:
        if not arguments.delete_artifact.isdecimal():
            raise ValueError("Invalid artifact identifier")
        client.request("DELETE", client.base + "/actions/artifacts/" + arguments.delete_artifact, missing_ok=True)
        return
    if os.environ.get("GITHUB_REF") != "refs/heads/main" or os.environ.get("GITHUB_EVENT_NAME") not in {"push", "workflow_dispatch"}:
        raise RuntimeError("APK publication is allowed only from main pushes or main manual runs")
    if arguments.apk is None:
        parser.error("A signed APK is required")
    source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'versionName\s*=\s*"([^"]+)"', source).group(1)
    version_code = int(re.search(r"versionCode\s*=\s*(\d+)", source).group(1))
    publish(client, arguments.apk, os.environ["GITHUB_SHA"],
            os.environ["GITHUB_RUN_ID"] + "-" + os.environ["GITHUB_RUN_ATTEMPT"], version, version_code)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, KeyError) as error:
        sys.exit(str(error))
