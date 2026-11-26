#!/usr/bin/env python3
"""Static checks of the GitHub workflows. Run from the repository root."""
import re
import sys
from pathlib import Path

import yaml

FILES = [".github/workflows/release.yml", ".github/workflows/pr-checks.yml"]
USES_VALUE = re.compile(r"^[\w.-]+/[\w.-]+(/[\w./-]+)?@[0-9a-f]{40} # v\d+\.\d+\.\d+$")
PIN = "7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6"
RELEASE_REF = "${{ env.RELEASE_REF }}"
VERIFIER_PATH = "signer-verifier"


def load(path):
    return yaml.safe_load(Path(path).read_text())


def steps(doc, job):
    return doc["jobs"][job]["steps"]


def step_named(doc, job, name):
    found = [s for s in steps(doc, job) if s.get("name") == name]
    assert len(found) == 1, f"{job}: expected one step {name!r}, found {len(found)}"
    return found[0]


def test_every_uses_is_sha_pinned():
    count = 0
    for path in FILES:
        for line in Path(path).read_text().splitlines():
            if "uses:" not in line:
                continue
            value = line.split("uses:", 1)[1].strip()
            assert USES_VALUE.match(value), f"{path}: unpinned {value!r}"
            count += 1
    assert count > 0, "no uses: lines found"


def test_checkouts_drop_credentials_and_keep_release_ref():
    total = 0
    for path in FILES:
        doc = load(path)
        for job, body in doc["jobs"].items():
            for s in body["steps"]:
                if str(s.get("uses", "")).startswith("actions/checkout@"):
                    total += 1
                    with_ = s.get("with", {})
                    assert with_.get("persist-credentials") is False, f"{path} {job}: credentials persisted"
                    if path.endswith("release.yml") and with_.get("path") != VERIFIER_PATH:
                        assert with_.get("ref") == RELEASE_REF, f"{path} {job}: ref changed"
                    if with_.get("path") == VERIFIER_PATH:
                        assert with_.get("ref") == "${{ github.sha }}", "verifier checkout must use the workflow revision"
    assert total > 0, "no checkouts found"


def test_permissions_read_top_level_write_only_on_release_job():
    for path in FILES:
        doc = load(path)
        assert doc["permissions"] == {"contents": "read"}, f"{path}: top-level permissions"
        for job, body in doc["jobs"].items():
            if path.endswith("release.yml") and job == "release":
                assert body["permissions"] == {"contents": "write"}, "release job permissions"
            else:
                assert "permissions" not in body, f"{path} {job}: has permissions"


def test_release_contract_unchanged():
    rel = ".github/workflows/release.yml"
    doc = load(rel)
    tag = doc[True]["workflow_dispatch"]["inputs"]["release_tag"]
    assert tag["required"] is True and tag["type"] == "string", "release_tag input changed"
    secrets = step_named(doc, "release", "Validate release secrets")
    assert "KEYPASS" not in secrets["run"] and "KEYPASS" not in secrets["env"], "KEYPASS must stay optional"
    build = step_named(doc, "release", "Build release APK")
    assert "KEYPASS" in build["env"], "KEYPASS dropped from the build step"
    assert step_named(doc, "release", "Create GitHub Release")["with"]["generate_release_notes"] is True


def test_signer_step_gates_upload_and_publish():
    doc = load(".github/workflows/release.yml")
    release = steps(doc, "release")
    names = [s.get("name") for s in release]
    gate = "Verify release signer"
    assert gate in names, "signer step missing"
    i = names.index(gate)
    assert i > names.index("Build release APK"), "signer step before the build"
    later = ["Prepare release artifacts", "Upload APK artifact", "Create GitHub Release"]
    for name in later:
        assert i < names.index(name), f"signer step not before {name}"
    step = release[i]
    assert "tools/verify-apk-signer.sh" in step["run"]
    assert '"$RELEASE_CERT_SHA256"' in step["run"], "run does not use the pin variable"
    assert step["env"]["RELEASE_CERT_SHA256"] == PIN, "pin value wrong"
    assert "if" not in step and "continue-on-error" not in step, "signer step is soft"
    apk_dir = "app/build/outputs/apk/prod/release"
    assert apk_dir in step["run"], "signer step checks another directory"
    assert apk_dir in step_named(doc, "release", "Prepare release artifacts")["run"], "packaging reads another directory"
    for name in later:
        s = step_named(doc, "release", name)
        assert "if" not in s and "continue-on-error" not in s, f"{name} is soft"
    assert "continue-on-error" not in doc["jobs"]["release"], "release job is soft"


def test_workflows_parse():
    for path in FILES:
        doc = load(path)
        assert isinstance(doc, dict) and "jobs" in doc, f"{path}: no jobs"


def main():
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"PASS {name}")
            except AssertionError as e:
                failed += 1
                print(f"FAIL {name}: {e}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
