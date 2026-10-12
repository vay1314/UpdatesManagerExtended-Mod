"""Prepare a stable release signing key from Actions Secrets without logging secrets."""

import base64
import os
from pathlib import Path

SECRETS = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)


def escape_property(value):
    result = []
    for char in value:
        if char in "\\ =:#!":
            result.append("\\" + char)
        elif char in "\n\r\t":
            result.append({"\n": r"\n", "\r": r"\r", "\t": r"\t"}[char])
        elif not 0x20 <= ord(char) <= 0x7E:
            encoded = char.encode("utf-16-be")
            result.extend(f"\\u{int.from_bytes(encoded[i:i + 2], 'big'):04x}"
                          for i in range(0, len(encoded), 2))
        else:
            result.append(char)
    return "".join(result)


def prepare(project, environment):
    present = [bool(environment.get(name)) for name in SECRETS]
    if any(present) and not all(present):
        missing = ", ".join(name for name in SECRETS if not environment.get(name))
        raise ValueError("Incomplete signing Secrets; missing: " + missing)
    if not all(present):
        if environment.get("PUBLISH_RELEASE") == "true":
            raise ValueError("Publishing requires all four Android signing Secrets")
        return "debug"

    key = base64.b64decode("".join(environment[SECRETS[0]].split()), validate=True)
    if not key:
        raise ValueError("Signing keystore is empty")
    keystore = project / "release-signing.jks"
    properties = project / "keystore.properties"
    if keystore.exists() or properties.exists():
        raise ValueError("Refusing to overwrite existing signing files")
    values = {
        "storeFile": "../release-signing.jks",
        "storePassword": environment[SECRETS[1]],
        "keyAlias": environment[SECRETS[2]],
        "keyPassword": environment[SECRETS[3]],
    }
    keystore.write_bytes(key)
    properties.write_text("".join(f"{name}={escape_property(value)}\n"
                                  for name, value in values.items()), encoding="ascii")
    keystore.chmod(0o600)
    properties.chmod(0o600)
    return "release"


if __name__ == "__main__":
    variant = prepare(Path("source"), os.environ)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"variant={variant}\n")
    print(f"Build variant: {variant}")
