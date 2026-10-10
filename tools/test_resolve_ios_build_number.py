import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location(
    "resolve_ios_build_number", Path(__file__).with_name("resolve-ios-build-number.py"))
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)


class BuildNumberTests(unittest.TestCase):
    def test_next_major_exceeds_dotted_and_plain_versions(self):
        self.assertEqual(resolver.next_build_number(["7", "13.1"]), 14)

    def test_empty_history_starts_at_one(self):
        self.assertEqual(resolver.next_build_number([]), 1)

    def test_malformed_version_fails_closed(self):
        for version in (None, "13.x", "1.2.3.4", "10000", "1.123", ""):
            with self.subTest(version=version), self.assertRaises(ValueError):
                resolver.next_build_number([version])

    def test_exhausted_major_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "exhausted"):
            resolver.next_build_number(["9999.99.99"])

    def test_fetches_every_build_page(self):
        calls = []

        def get_json(url):
            calls.append(url)
            if "/v1/apps?" in url:
                return {"data": [{"id": "app-id"}], "links": {"next": None}}
            if "cursor=second" in url:
                return {"data": [{"attributes": {"version": "13.1"}}],
                        "links": {"next": None}}
            return {"data": [{"attributes": {"version": "7"}}],
                    "links": {"next": "/v1/builds?cursor=second"}}

        self.assertEqual(resolver.fetch_versions(get_json), ["7", "13.1"])
        self.assertEqual(len(calls), 3)

    def test_missing_version_fails_closed(self):
        def get_json(url):
            if "/v1/apps?" in url:
                return {"data": [{"id": "app-id"}]}
            return {"data": [{"attributes": {}}]}

        with self.assertRaisesRegex(ValueError, "missing its version"):
            resolver.fetch_versions(get_json)


if __name__ == "__main__":
    unittest.main()
