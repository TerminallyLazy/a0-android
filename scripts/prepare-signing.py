#!/usr/bin/env python3
"""Create stable local signing identities once. No secrets enter the repository or stdout."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path.home() / ".config/a0-android/signing"
keytool = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")) / "bin/keytool"
os.umask(0o077)
root.mkdir(parents=True, exist_ok=True, mode=0o700)
for role in ("app", "upload"):
    key = root / f"{role}.p12"
    properties = root / f"{role}.properties"
    if key.exists() or properties.exists():
        if key.exists() and properties.exists():
            print(f"Preserved existing {role} identity")
            continue
        raise SystemExit(f"Incomplete {role} identity; restore its matching files before continuing")
    password = secrets.token_urlsafe(48)
    environment = dict(os.environ, A0_KEY_PASSWORD=password)
    subprocess.run([
        str(keytool), "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
        "-alias", role, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
        "-dname", f"CN=Agent Zero Android {role}, O=TerminallyLazy",
        "-storepass:env", "A0_KEY_PASSWORD", "-keypass:env", "A0_KEY_PASSWORD",
    ], env=environment, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    properties.write_text(f"storeFile={key}\nstorePassword={password}\nkeyAlias={role}\nkeyPassword={password}\n")
    subprocess.run([
        str(keytool), "-exportcert", "-rfc", "-keystore", str(key), "-alias", role,
        "-storepass:env", "A0_KEY_PASSWORD", "-file", str(root / f"{role}-certificate.pem"),
    ], env=environment, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"Created protected {role} identity")
print("Keep an independent encrypted backup of the signing directory before changing machines.")
