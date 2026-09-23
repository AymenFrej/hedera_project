#!/usr/bin/env python3
"""Prove the policy tests actually bite.

Each mutation breaks one rule in PolicyEngine.java. A mutation that still
passes the suite means the rule is untested - the test is decoration.
"""
import re
import shutil
import subprocess
import pathlib
import os

SRC = pathlib.Path(
    "backend/src/main/java/com/hedera/agentplatform/policies/PolicyEngine.java"
)
BACKUP = SRC.with_suffix(".java.bak")

MUTATIONS = {
    # DENY must be checked before HOLD. Make insufficient funds fall through
    # to the emergency/unknown HOLD branches instead.
    "deny_no_longer_beats_hold": (
        "    if (amount > balance) {\n      return new PolicyDecision(\n          Verdict.DENY,\n          \"funds.insufficient\",",
        "    if (false) {\n      return new PolicyDecision(\n          Verdict.DENY,\n          \"funds.insufficient\",",
    ),
    # Emergency must always require a human.
    "emergency_no_longer_needs_human": (
        "    if (envelope == Envelope.EMERGENCY) {",
        "    if (false) {",
    ),
    # Unknown counterparty must be held.
    "unknown_counterparty_allowed": (
        "    if (!state.knownCounterparties().contains(request.counterparty())) {",
        "    if (false) {",
    ),
    # Envelopes must sum to the total; drop the remainder into the void.
    "allocation_loses_funds": (
        "    envelopes.put(Envelope.EMERGENCY, total - rent - essentials);",
        "    envelopes.put(Envelope.EMERGENCY, 0L);",
    ),
}


def run_tests() -> bool:
    env = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")
    r = subprocess.run(
        ["./mvnw", "test", "-Dtest=PolicyEngineTest"],
        cwd="backend",
        capture_output=True,
        text=True,
        env=env,
    )
    return "BUILD SUCCESS" in r.stdout


def main() -> int:
    shutil.copy(SRC, BACKUP)
    survivors = []
    try:
        for name, (old, new) in MUTATIONS.items():
            text = BACKUP.read_text()
            if old not in text:
                print(f"SKIP  {name}: anchor not found - fix the harness")
                survivors.append(name + " (anchor missing)")
                continue
            SRC.write_text(text.replace(old, new, 1))
            if run_tests():
                print(f"SURVIVED  {name}  <-- rule is NOT tested")
                survivors.append(name)
            else:
                print(f"killed    {name}")
    finally:
        shutil.copy(BACKUP, SRC)
        BACKUP.unlink()

    if survivors:
        print(f"\n{len(survivors)} mutation(s) survived: {survivors}")
        return 1
    print(f"\nAll {len(MUTATIONS)} mutations killed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
