"""Create versioned releases or replace their APK assets on subsequent builds."""

import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote


def publish(tag, apk, commit, repository, run=subprocess.run):
    run(["git", "check-ref-format", "refs/tags/" + tag], check=True)
    existing = run(["gh", "api", f"repos/{repository}/releases/tags/{quote(tag, safe='')}"],
                   capture_output=True, text=True)
    if existing.returncode == 0:
        if not apk.is_file() or not Path(str(apk) + ".sha256").is_file():
            raise ValueError("APK or checksum is missing")
        run(["gh", "release", "upload", tag, str(apk), str(apk) + ".sha256", "--clobber"],
            check=True)
        print(f"Replaced APK and checksum in {tag} with build from {commit}")
        return True
    if "HTTP 404" not in existing.stderr:
        raise RuntimeError("Cannot query existing release; publication aborted")

    reference = "refs/tags/" + tag
    remote = run(["git", "ls-remote", "--tags", "origin", reference, reference + "^{}"],
                 check=True, capture_output=True, text=True)
    refs = {}
    for line in remote.stdout.splitlines():
        sha, name = line.split()
        refs[name] = sha
    tag_commit = refs.get(reference + "^{}", refs.get(reference))
    if tag_commit is not None and tag_commit != commit:
        raise ValueError("Version tag points to another commit; increment versionCode")
    if not apk.is_file() or not Path(str(apk) + ".sha256").is_file():
        raise ValueError("APK or checksum is missing")

    command = ["gh", "release", "create", tag, str(apk), str(apk) + ".sha256",
               "--target", commit, "--generate-notes",
               "--title", "Updates Manager Extended Mod " + tag]
    if tag_commit is not None:
        command.append("--verify-tag")
    if re.search(r"-(alpha|beta|rc)(?:[.\d-]|$)", tag):
        command.append("--prerelease")
    run(command, check=True)
    print(f"Published {tag} from {commit}")
    return True


if __name__ == "__main__":
    publish(os.environ["RELEASE_TAG"], Path(os.environ["APK_FILE"]),
            os.environ["BUILD_COMMIT"], os.environ["GH_REPO"])
