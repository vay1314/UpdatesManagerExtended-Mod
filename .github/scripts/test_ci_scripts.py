import base64
import json
from pathlib import Path
import tempfile
import unittest

from package_apk import package_apk
from prepare_signing import prepare


class SigningTest(unittest.TestCase):
    def environment(self):
        return {
            "ANDROID_KEYSTORE_BASE64": base64.b64encode(b"test-keystore").decode(),
            "ANDROID_KEYSTORE_PASSWORD": "password with =:!\\\n中文",
            "ANDROID_KEY_ALIAS": "test-alias",
            "ANDROID_KEY_PASSWORD": "key-password",
            "PUBLISH_RELEASE": "true",
        }

    def test_build_without_secrets_uses_debug_but_cannot_publish(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp)
            self.assertEqual("debug", prepare(project, {}))
            with self.assertRaisesRegex(ValueError, "Publishing requires"):
                prepare(project, {"PUBLISH_RELEASE": "true"})
            self.assertEqual([], list(project.iterdir()))

    def test_partial_or_invalid_secrets_do_not_create_signing_files(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp)
            with self.assertRaisesRegex(ValueError, "Incomplete"):
                prepare(project, {"ANDROID_KEY_ALIAS": "test"})
            environment = self.environment()
            environment["ANDROID_KEYSTORE_BASE64"] = "not-valid-base64!"
            with self.assertRaises(ValueError):
                prepare(project, environment)
            self.assertEqual([], list(project.iterdir()))

    def test_signing_files_escape_properties_and_are_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp)
            self.assertEqual("release", prepare(project, self.environment()))
            self.assertEqual(b"test-keystore", (project / "release-signing.jks").read_bytes())
            properties = (project / "keystore.properties").read_text(encoding="ascii")
            self.assertIn(r"password\ with\ \=\:\!\\\n\u4e2d\u6587", properties)
            self.assertEqual(4, len(properties.splitlines()))
            with self.assertRaisesRegex(ValueError, "overwrite"):
                prepare(project, self.environment())


class PackagingTest(unittest.TestCase):
    def fixture(self, project, output="app-release.apk", app_id=None):
        directory = project / "app/build/outputs/apk/release"
        directory.mkdir(parents=True)
        metadata = {
            "applicationId": app_id or "io.github.vay1314.updatesmanagerextendedmod",
            "elements": [{"versionName": "4.2", "versionCode": 17, "outputFile": output}],
        }
        (directory / "output-metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
        (directory / output).write_bytes(b"test-apk")

    def test_signed_release_is_named_from_actual_apk_metadata(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp) / "source"
            self.fixture(project)
            target, name, tag = package_apk(project, Path(temp) / "dist", "release")
            self.assertEqual("UpdatesManagerExtended-Mod-v4.2-build17-release", name)
            self.assertEqual(b"test-apk", target.read_bytes())
            self.assertEqual("v4.2-mod.17", tag)

    def test_unsigned_release_or_wrong_app_is_rejected(self):
        for output, app_id in (("app-release-unsigned.apk", None),
                               ("app-release.apk", "com.example.other")):
            with self.subTest(output=output, app_id=app_id), tempfile.TemporaryDirectory() as temp:
                project = Path(temp) / "source"
                self.fixture(project, output, app_id)
                with self.assertRaises(ValueError):
                    package_apk(project, Path(temp) / "dist", "release")
                self.assertFalse((Path(temp) / "dist").exists())


if __name__ == "__main__":
    unittest.main()
