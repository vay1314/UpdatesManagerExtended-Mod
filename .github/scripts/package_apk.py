"""Package the APK described by Gradle metadata; never publish an unsigned APK."""

import json
import os
from pathlib import Path
import re
import shutil


def package_apk(project, destination, variant):
    if variant not in ("debug", "release"):
        raise ValueError("Unknown build variant")
    outputs = project / "app/build/outputs/apk" / variant
    metadata = json.loads((outputs / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata["applicationId"] != "io.github.vay1314.updatesmanagerextendedmod":
        raise ValueError("Unexpected application ID")
    elements = metadata["elements"]
    if len(elements) != 1:
        raise ValueError("Expected one universal APK")
    element = elements[0]
    version = str(element["versionName"])
    code = str(element["versionCode"])
    if not re.fullmatch(r"[A-Za-z0-9._+-]+", version) or not code.isdigit():
        raise ValueError("Invalid APK version metadata")
    output = element["outputFile"]
    expected = f"app-{variant}.apk"
    if output != expected:
        raise ValueError("Expected a signed APK: " + expected)
    source = outputs / output
    if not source.is_file():
        raise ValueError("APK output not found")
    destination.mkdir(parents=True, exist_ok=True)
    name = f"UpdatesManagerExtended-Mod-v{version}-build{code}-{variant}"
    target = destination / (name + ".apk")
    shutil.copyfile(source, target)
    return target, name, f"v{version}-mod.{code}"


if __name__ == "__main__":
    apk, name, tag = package_apk(Path("source"), Path("dist"), os.environ["BUILD_VARIANT"])
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"apk={apk.as_posix()}\nname={name}\ntag={tag}\n")
    print(f"APK: {apk}")
