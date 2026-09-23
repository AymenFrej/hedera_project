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

ROOT = pathlib.Path(__file__).resolve().parents[4]
BACKEND = ROOT / "backend"
SRC = BACKEND / "src/main/java/com/hedera/agentplatform/policies/PolicyEngine.java"
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
    env = os.environ.copy()
    wrapper = BACKEND / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(wrapper), "test", "-Dtest=PolicyEngineTest"]
    if not wrapper.exists():
        command = ["mvn", "test", "-Dtest=PolicyEngineTest"]
    r = subprocess.run(
        command,
        cwd=BACKEND,
        capture_output=True,
        text=True,
        env=env,
    )
    return r.returncode == 0 and "BUILD SUCCESS" in r.stdout


def main() -> int:
    original = SRC.read_text()
    survivors = []
    try:
        for name, (old, new) in MUTATIONS.items():
            if old not in original:
                print(f"SKIP  {name}: anchor not found - fix the harness")
                survivors.append(name + " (anchor missing)")
                continue
            SRC.write_text(original.replace(old, new, 1))
            if run_tests():
                print(f"SURVIVED  {name}  <-- rule is NOT tested")
                survivors.append(name)
            else:
                print(f"killed    {name}")
    finally:
        SRC.write_text(original)

    if survivors:
        print(f"\n{len(survivors)} mutation(s) survived: {survivors}")
        return 1
    print(f"\nAll {len(MUTATIONS)} mutations killed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
