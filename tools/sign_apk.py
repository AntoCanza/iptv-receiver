"""Sign the optimized APK with private Actions secrets and verify the public certificate."""

import argparse
import base64
import os
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
CERTIFICATE = ROOT / ".github/android-signing-certificate.sha256"
SECRET_NAMES = (
    "ANDROID_SIGNING_KEYSTORE_BASE64",
    "ANDROID_SIGNING_STORE_PASSWORD",
    "ANDROID_SIGNING_KEY_PASSWORD",
    "ANDROID_SIGNING_KEY_ALIAS",
)


def sign(source: Path, destination: Path) -> None:
    missing = [name for name in SECRET_NAMES if not os.environ.get(name)]
    if missing:
        raise RuntimeError("Missing Actions signing secrets: " + ", ".join(missing))
    if not source.is_file() or source.resolve() == destination.resolve():
        raise ValueError("Supply distinct unsigned input and signed output APK paths")
    expected = CERTIFICATE.read_text(encoding="utf-8").strip().lower()
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise ValueError("Invalid expected signing certificate")
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    signer = sdk / ("apksigner.bat" if os.name == "nt" else "apksigner")
    aligner = sdk / ("zipalign.exe" if os.name == "nt" else "zipalign")
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix="apk-signing-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
            directory = Path(temporary)
            key = directory / "signing.keystore"
            key.write_bytes(base64.b64decode(os.environ[SECRET_NAMES[0]], validate=True))
            key.chmod(0o600)
            aligned = directory / "aligned.apk"
            subprocess.run([str(aligner), "-f", "-P", "16", "4", str(source), str(aligned)],
                           check=True, capture_output=True)
            subprocess.run([
                str(signer), "sign", "--ks", str(key), "--ks-key-alias", os.environ[SECRET_NAMES[3]],
                "--ks-pass", "env:ANDROID_SIGNING_STORE_PASSWORD", "--key-pass", "env:ANDROID_SIGNING_KEY_PASSWORD",
                "--out", str(destination), str(aligned),
            ], check=True, capture_output=True)
            verification = subprocess.run([str(signer), "verify", "--verbose", "--print-certs", str(destination)],
                                          check=True, capture_output=True, text=True)
            certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})", verification.stdout)
            if [value.lower() for value in certificates] != [expected]:
                raise RuntimeError("The APK signing certificate does not match existing installations")
    except Exception:
        destination.unlink(missing_ok=True)
        raise
    # The certificate fingerprint is public; private key/password values never reach output.
    print("APK signature verified: " + expected)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()
    try:
        sign(arguments.source, arguments.destination)
    except subprocess.CalledProcessError:
        parser.exit(1, "Android APK signing or verification failed.\n")
    except (ValueError, RuntimeError, KeyError) as error:
        parser.exit(1, str(error) + "\n")
