import importlib.util
import plistlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ios_release", Path(__file__).with_name("verify-ios-release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ExportedComposeConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        base = Path(self.directory.name)
        self.source = base / "source"
        self.source.mkdir()
        (self.source / "BUILD_SOURCE.txt").write_text("Source commit: " + "a" * 40 + "\n")
        self.app = base / "KKKeyboard.app"
        self.extension = self.app / "PlugIns/KKKeyboardExtension.appex"
        for bundle, identifier in [(self.app, "com.ghtnql.kkkeyboard"), (self.extension, "com.ghtnql.kkkeyboard.keyboard")]:
            bundle.mkdir(parents=True, exist_ok=True)
            with (bundle / "Info.plist").open("wb") as stream:
                plistlib.dump({"CFBundleIdentifier": identifier, "CFBundleVersion": "22", "CFBundleShortVersionString": "1.0", "CADisableMinimumFrameDurationOnPhone": True}, stream)
        assets = [("sharedUI/src/commonMain/composeResources/drawable/image%d.png" % i, self.app) for i in range(8)]
        assets += [("shared/audio/sound%d.wav" % i, self.app) for i in range(9)]
        assets += [("shared/themes/" + name, self.extension) for name in ["seoul_day.jpg", "seoul_day_portrait.jpg", "seoul_night.jpg", "seoul_night_portrait.jpg"]]
        assets += [("shared/themes/themes.json", self.app), ("shared/content/learning_items.json", self.app), ("shared/dictionaries/ja_common.json", self.extension)]
        assets += [("shared/phrases/default_phrases.json", bundle) for bundle in [self.app, self.extension]]
        for relative, bundle in assets:
            source = self.source / relative
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(relative.encode())
            (bundle / source.name).write_bytes(source.read_bytes())
        self.ads = patch.object(release, "verify_production_ads", return_value={"test_ads": False})
        self.ads.start()
        self.addCleanup(self.ads.stop)

    def test_export_accepts_both_configured_bundles(self):
        manifest = release.verify(self.app, self.source, "22")
        self.assertTrue(manifest["bundles"]["extension"]["compose_frame_duration_enabled"])
        self.assertEqual(26, len(manifest["assets"]))

    def test_export_rejects_missing_false_or_non_boolean_extension_flag(self):
        path = self.extension / "Info.plist"
        original = plistlib.loads(path.read_bytes())
        for value in [None, False, "true", 1]:
            with self.subTest(value=value):
                info = dict(original)
                if value is None:
                    info.pop("CADisableMinimumFrameDurationOnPhone")
                else:
                    info["CADisableMinimumFrameDurationOnPhone"] = value
                path.write_bytes(plistlib.dumps(info))
                with self.assertRaisesRegex(ValueError, "extension must enable CADisable"):
                    release.verify(self.app, self.source, "22")

    def test_export_rejects_missing_app_flag_even_when_extension_is_valid(self):
        path = self.app / "Info.plist"
        info = plistlib.loads(path.read_bytes())
        info.pop("CADisableMinimumFrameDurationOnPhone")
        path.write_bytes(plistlib.dumps(info))
        with self.assertRaisesRegex(ValueError, "app must enable CADisable"):
            release.verify(self.app, self.source, "22")


if __name__ == "__main__":
    unittest.main()
