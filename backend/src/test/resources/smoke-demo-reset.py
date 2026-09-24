#!/usr/bin/env python3
"""Proves the demo reset against the running app: runway restored, queue empty,
audit trail untouched."""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"


def call(path, method="GET", body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST" if data is not None else method,
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def wait_up():
    for _ in range(45):
        try:
            if call("/policies/state")[0] == 200:
                return True
        except Exception:
            pass
        time.sleep(2)
    return False


def main():
    if not wait_up():
        print("FAIL: backend never came up on :8080")
        return 1

    _, before_state = call("/policies/state")
    _, before_queue = call("/policies/approvals")
    _, before_audit = call("/audit")
    print("before: envelopes", before_state["envelopes"])
    print("before: %d approvals, %d audit events" % (len(before_queue), len(before_audit)))

    code, restored = call("/policies/demo/reset", method="POST")
    print("reset ->", code, restored["envelopes"], "known:", restored["knownCounterparties"])

    _, after_queue = call("/policies/approvals")
    _, after_audit = call("/audit")
    print("after : %d approvals, %d audit events" % (len(after_queue), len(after_audit)))

    failures = []
    if restored["envelopes"] != {"RENT": 500, "ESSENTIALS": 300, "EMERGENCY": 200}:
        failures.append("runway not restored to the seeded amounts")
    if after_queue:
        failures.append("queue still holds %d approvals" % len(after_queue))
    if restored["knownCounterparties"] != ["landlord-tunis"]:
        failures.append("counterparties not back to the seed")
    if len(after_audit) < len(before_audit):
        failures.append("reset erased audit events")

    if failures:
        print("FAIL:", "; ".join(failures))
        return 1
    print("OK: runway restored, queue empty, audit trail intact")
    return 0


if __name__ == "__main__":
    sys.exit(main())
