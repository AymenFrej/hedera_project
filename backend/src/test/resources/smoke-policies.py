#!/usr/bin/env python3
"""Outside-in smoke of the policies vertical against a running backend.

Not a unit test: it proves the HTTP surface a judge clicks actually settles a
spend end to end. Run it against localhost:8080 with the app already up.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"


def call(path, body=None, method="GET"):
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


def main():
    _, before = call("/policies/state")
    print("state:", before["envelopes"])

    _, allow = call(
        "/policies/decide",
        {"envelope": "ESSENTIALS", "amount": 20, "counterparty": "landlord-tunis"},
    )
    print("ALLOW ->", allow["verdict"], allow["ruleId"], "audit:", allow["auditEventId"])

    _, deny = call(
        "/policies/decide",
        {"envelope": "RENT", "amount": 999999, "counterparty": "landlord-tunis"},
    )
    print("DENY  ->", deny["verdict"], deny["ruleId"], "approvalId:", deny["approvalId"])

    _, hold = call(
        "/policies/decide",
        {"envelope": "EMERGENCY", "amount": 50, "counterparty": "clinic-tunis"},
    )
    print("HOLD  ->", hold["verdict"], hold["ruleId"], "approvalId:", hold["approvalId"])

    _, queue = call("/policies/approvals")
    print("queue :", len(queue), "items, first status", queue[0]["status"])

    code, answered = call("/policies/approvals/%s/approve" % hold["approvalId"], method="POST")
    print("approve ->", code, answered["status"], "by", answered["decidedBy"])

    _, after = call("/policies/state")
    print("state :", after["envelopes"])

    code, _ = call("/policies/approvals/%s/approve" % hold["approvalId"], method="POST")
    print("re-answer ->", code, "(expect 409)")

    failures = []
    if allow["verdict"] != "ALLOW":
        failures.append("small known spend was not ALLOW")
    if deny["verdict"] != "DENY" or deny["approvalId"] is not None:
        failures.append("DENY reached the human queue")
    if hold["verdict"] != "HOLD" or not hold["approvalId"]:
        failures.append("emergency spend did not HOLD")
    if answered["status"] != "APPROVED":
        failures.append("approval did not settle")
    if after["envelopes"]["EMERGENCY"] != before["envelopes"]["EMERGENCY"] - 50:
        failures.append("approved emergency spend did not leave the envelope")
    if after["envelopes"]["ESSENTIALS"] != before["envelopes"]["ESSENTIALS"] - 20:
        failures.append("allowed spend did not leave the envelope")
    if code != 409:
        failures.append("answering twice was not refused")

    if failures:
        print("FAIL:", "; ".join(failures))
        return 1
    print("OK: allow/deny/hold, queue, approval, debit, replay refusal")
    return 0


if __name__ == "__main__":
    sys.exit(main())
