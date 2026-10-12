from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock

from publish_release import publish


class PublishTest(unittest.TestCase):
    COMMIT = "a" * 40
    TAG = "v4.2-mod.17"

    def result(self, code=0, stdout="", stderr=""):
        return subprocess.CompletedProcess([], code, stdout, stderr)

    def runner(self, remote=""):
        return Mock(side_effect=[self.result(), self.result(1, stderr="gh: Not Found (HTTP 404)"),
                                 self.result(stdout=remote), self.result()])

    def artifact(self, directory):
        apk = Path(directory) / "app.apk"
        apk.write_bytes(b"apk")
        Path(str(apk) + ".sha256").write_text("checksum", encoding="ascii")
        return apk

    def test_new_version_creates_tag_at_the_exact_built_commit(self):
        with tempfile.TemporaryDirectory() as temp:
            runner = self.runner()
            self.assertTrue(publish(self.TAG, self.artifact(temp), self.COMMIT,
                                    "owner/repo", runner))
            command = runner.call_args_list[-1].args[0]
            self.assertEqual(["gh", "release", "create", self.TAG], command[:4])
            self.assertEqual(self.COMMIT, command[command.index("--target") + 1])
            self.assertNotIn("--verify-tag", command)

    def test_existing_version_replaces_apk_and_checksum(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = self.artifact(temp)
            runner = Mock(side_effect=[self.result(), self.result(stdout="existing release"),
                                      self.result()])
            self.assertTrue(publish(self.TAG, apk, self.COMMIT, "owner/repo", runner))
            self.assertEqual(["gh", "release", "upload", self.TAG, str(apk),
                              str(apk) + ".sha256", "--clobber"], runner.call_args.args[0])
            self.assertEqual(3, runner.call_count)

    def test_existing_release_is_not_modified_when_checksum_is_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"apk")
            runner = Mock(side_effect=[self.result(), self.result(stdout="existing release")])
            with self.assertRaisesRegex(ValueError, "checksum"):
                publish(self.TAG, apk, self.COMMIT, "owner/repo", runner)
            self.assertEqual(2, runner.call_count)

    def test_failed_overwrite_is_reported_as_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            runner = Mock(side_effect=[self.result(), self.result(stdout="existing release"),
                                      subprocess.CalledProcessError(1, ["gh", "release", "upload"])])
            with self.assertRaises(subprocess.CalledProcessError):
                publish(self.TAG, self.artifact(temp), self.COMMIT, "owner/repo", runner)

    def test_authentication_errors_do_not_create_a_tag_or_release(self):
        runner = Mock(side_effect=[self.result(), self.result(1, stderr="HTTP 403")])
        with self.assertRaises(RuntimeError):
            publish(self.TAG, Path("app.apk"), self.COMMIT, "owner/repo", runner)
        self.assertEqual(2, runner.call_count)

    def test_tag_on_another_commit_is_rejected(self):
        runner = self.runner("b" * 40 + "\trefs/tags/" + self.TAG + "\n")
        with self.assertRaisesRegex(ValueError, "another commit"):
            publish(self.TAG, Path("app.apk"), self.COMMIT, "owner/repo", runner)
        self.assertEqual(3, runner.call_count)

    def test_annotated_tag_is_checked_against_its_peeled_commit(self):
        reference = "refs/tags/" + self.TAG
        remote = "b" * 40 + "\t" + reference + "\n" + self.COMMIT + "\t" + reference + "^{}\n"
        with tempfile.TemporaryDirectory() as temp:
            runner = self.runner(remote)
            publish(self.TAG, self.artifact(temp), self.COMMIT, "owner/repo", runner)
            self.assertIn("--verify-tag", runner.call_args_list[-1].args[0])

    def test_missing_checksum_prevents_publication(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "app.apk"
            apk.write_bytes(b"apk")
            runner = self.runner()
            with self.assertRaisesRegex(ValueError, "checksum"):
                publish(self.TAG, apk, self.COMMIT, "owner/repo", runner)
            self.assertEqual(3, runner.call_count)

    def test_beta_version_creates_a_prerelease(self):
        with tempfile.TemporaryDirectory() as temp:
            runner = self.runner()
            publish("v4.3-beta.1-mod.18", self.artifact(temp), self.COMMIT, "owner/repo", runner)
            self.assertIn("--prerelease", runner.call_args_list[-1].args[0])


if __name__ == "__main__":
    unittest.main()
