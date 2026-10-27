"""Exercise release replacement/rollback with a synthetic GitHub API; no account or network is used."""

import copy
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_latest as publishing


SHA = "a" * 40
OLD_SHA = "b" * 40


class FakeGitHub:
    base = "/repos/AntoCanza/iptv-receiver"

    def __init__(self):
        self.head = SHA
        self.tag = OLD_SHA
        self.next_id = 100
        self.uploads = 0
        self.fail_upload = None
        self.bad_digest = False
        self.fail_activation = False
        self.advance_during_upload = False
        self.events = []
        self.releases = {
            1: {"id": 1, "tag_name": "latest", "draft": False, "immutable": False,
                "upload_url": "https://uploads.github.com/repos/AntoCanza/iptv-receiver/releases/1/assets{?name,label}",
                "assets": [{"id": 10, "name": publishing.APK_NAME}, {"id": 11, "name": publishing.CHECKSUM_NAME}]},
            2: {"id": 2, "tag_name": "v0.1.13", "assets": []},
        }
        self.artifacts = {10: {"id": 10, "name": "development-apk"}, 11: {"id": 11, "name": "unsigned-apk"},
                          12: {"id": 12, "name": "verification-reports"}}

    def pages(self, path, key=None):
        return iter(copy.deepcopy(list(self.artifacts.values() if key else self.releases.values())))

    def request(self, method, path, payload=None, *, binary=None, content_type=None, missing_ok=False):
        self.events.append((method, path))
        parsed = urlsplit(path)
        route = parsed.path.removeprefix(self.base)
        if route == "/branches/main":
            result = {"commit": {"sha": self.head}}
        elif route == "/releases/tags/latest":
            result = next((r for r in self.releases.values() if r["tag_name"] == "latest"), None)
        elif route == "/git/ref/tags/latest":
            result = {"object": {"sha": self.tag}} if self.tag else None
        elif route == "/git/refs" and method == "POST":
            self.tag = payload["sha"]
            result = {"object": {"sha": self.tag}}
        elif route == "/git/refs/tags/latest":
            self.tag = payload["sha"] if method == "PATCH" else None
            result = None
        elif route == "/releases" and method == "POST":
            self.next_id += 1
            result = {**payload, "id": self.next_id, "assets": [],
                      "upload_url": f"https://uploads.github.com{self.base}/releases/{self.next_id}/assets{{?name,label}}"}
            self.releases[result["id"]] = result
        elif route.startswith("/releases/assets/"):
            identifier = int(route.rsplit("/", 1)[1])
            owner = next((r for r in self.releases.values() if any(a["id"] == identifier for a in r["assets"])), None)
            result = None
            if owner:
                if method == "DELETE":
                    owner["assets"] = [a for a in owner["assets"] if a["id"] != identifier]
                else:
                    result = next(a for a in owner["assets"] if a["id"] == identifier)
                    result.update(payload)
        elif route.startswith("/releases/") and route.endswith("/assets"):
            self.uploads += 1
            if self.fail_upload == self.uploads:
                raise RuntimeError("Synthetic upload failure")
            self.next_id += 1
            result = {"id": self.next_id, "name": parse_qs(parsed.query)["name"][0], "size": len(binary),
                      "state": "uploaded", "digest": "sha256:" + hashlib.sha256(binary).hexdigest()}
            if self.bad_digest:
                result["digest"] = "sha256:" + "0" * 64
            self.releases[int(route.split("/")[2])]["assets"].append(result)
            if self.advance_during_upload:
                self.head = "c" * 40
        elif route.startswith("/releases/"):
            identifier = int(route.rsplit("/", 1)[1])
            if method == "DELETE":
                self.releases.pop(identifier, None)
                result = None
            else:
                if self.fail_activation:
                    raise RuntimeError("Synthetic release activation failure")
                self.releases[identifier].update(payload)
                result = self.releases[identifier]
        elif route.startswith("/actions/artifacts/") and method == "DELETE":
            self.artifacts.pop(int(route.rsplit("/", 1)[1]), None)
            result = None
        else:
            raise AssertionError((method, path))
        return copy.deepcopy(result)


class PublishLatestTest(unittest.TestCase):
    def setUp(self):
        summary = patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": ""})
        summary.start()
        self.addCleanup(summary.stop)
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.apk = Path(self.folder.name) / publishing.APK_NAME
        self.apk.write_bytes(b"synthetic signed APK fixture")
        self.client = FakeGitHub()
        self.original_assets = copy.deepcopy(self.client.releases[1]["assets"])

    def publish(self):
        return publishing.publish(self.client, self.apk, SHA, "123-1", "0.1.14", 15)

    def assert_old_download_preserved(self):
        self.assertEqual(self.original_assets, self.client.releases[1]["assets"])
        self.assertIn(2, self.client.releases)
        self.assertEqual(OLD_SHA, self.client.tag)
        self.assertEqual(3, len(self.client.artifacts))

    def test_success_keeps_one_release_two_verified_assets_and_only_report_artifacts(self):
        self.assertTrue(self.publish())
        self.assertEqual([1], list(self.client.releases))
        self.assertFalse(self.client.releases[1]["draft"])
        self.assertEqual({publishing.APK_NAME, publishing.CHECKSUM_NAME},
                         {a["name"] for a in self.client.releases[1]["assets"]})
        self.assertEqual(SHA, self.client.tag)
        self.assertEqual(["verification-reports"], [a["name"] for a in self.client.artifacts.values()])
        self.assertEqual(2, self.client.uploads)

    def test_second_upload_failure_leaves_old_apk_release_tag_and_artifacts(self):
        self.client.fail_upload = 2
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assert_old_download_preserved()

    def test_digest_mismatch_cannot_replace_the_working_download(self):
        self.client.bad_digest = True
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assert_old_download_preserved()

    def test_release_activation_failure_restores_original_filenames_and_tag(self):
        self.client.fail_activation = True
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assert_old_download_preserved()

    def test_old_commit_cannot_mutate_releases(self):
        self.client.head = OLD_SHA
        self.assertFalse(self.publish())
        self.assert_old_download_preserved()
        self.assertTrue(all(method == "GET" for method, _ in self.client.events))

    def test_main_advancing_during_upload_discards_only_candidates(self):
        self.client.advance_during_upload = True
        self.assertFalse(self.publish())
        self.assert_old_download_preserved()

    def test_first_failed_upload_removes_the_new_draft_without_publishing_an_empty_release(self):
        self.client.releases = {}
        self.client.tag = None
        self.client.fail_upload = 1
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assertEqual({}, self.client.releases)
        self.assertIsNone(self.client.tag)

    def test_mutable_release_is_required_before_any_assets_are_changed(self):
        self.client.releases[1]["immutable"] = True
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assert_old_download_preserved()
        self.assertEqual(0, self.client.uploads)

    def test_cli_rejects_pull_request_publication_before_network_access(self):
        with patch.dict("os.environ", {"GITHUB_REPOSITORY": "AntoCanza/iptv-receiver", "GITHUB_TOKEN": "synthetic-token",
                                       "GITHUB_REF": "refs/pull/1/merge", "GITHUB_EVENT_NAME": "pull_request"}, clear=True):
            with patch.object(sys, "argv", ["publish_latest.py", str(self.apk)]):
                with patch.object(publishing.GitHub, "request", side_effect=AssertionError("Network call")):
                    with self.assertRaisesRegex(RuntimeError, "only from main"):
                        publishing.main()


if __name__ == "__main__":
    unittest.main()
