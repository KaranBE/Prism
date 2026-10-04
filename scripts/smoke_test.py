#!/usr/bin/env python3
"""
Prism smoke test - exercises every feature the spec's demo must show, against a running
gateway (default http://localhost:8080). Exits non-zero on the first failure so it is safe
to wire into CI.

Usage:
    python scripts/smoke_test.py [--base-url http://localhost:8080]
"""
import argparse
import json
import sys
import time
import urllib.request
import urllib.error

PASS = "PASS"
FAIL = "FAIL"

results = []


def record(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"[{PASS if ok else FAIL}] {name}" + (f" - {detail}" if detail else ""))


def call(base_url, path, method="GET", headers=None, body=None, timeout=15):
    url = base_url.rstrip("/") + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            try:
                parsed = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                parsed = raw
            return resp.status, dict(resp.headers), parsed
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, dict(e.headers), parsed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    args = parser.parse_args()
    base = args.base_url

    good_key = "prism-sk-alpha-0001"
    restricted_key = "prism-sk-beta-0002"
    lowbudget_key = "prism-sk-lowbudget-0003"
    auth = lambda k: {"Authorization": f"Bearer {k}"}

    # 1. Invalid key is rejected
    status, _, body = call(base, "/v1/chat/completions", "POST", auth("not-a-real-key"),
                            {"model": "fast", "messages": [{"role": "user", "content": "hi"}]})
    record("Invalid virtual key rejected (401)", status == 401 and body["error"]["code"] == "INVALID_VIRTUAL_KEY")

    # 2. Basic non-streaming completion + header contract
    status, headers, body = call(base, "/v1/chat/completions", "POST", auth(good_key),
                                  {"model": "fast", "messages": [{"role": "user", "content": "What is the capital of France?"}]})
    has_headers = all(h in headers for h in ["x-prism-provider", "x-prism-cache", "x-prism-fallback", "x-prism-cost-usd"])
    record("Non-streaming completion returns 200 + full header contract", status == 200 and has_headers,
           f"headers={headers.get('x-prism-provider')}/{headers.get('x-prism-cache')}")

    # 3. Model not allowed for a restricted key
    status, _, body = call(base, "/v1/chat/completions", "POST", auth(restricted_key),
                            {"model": "smart", "messages": [{"role": "user", "content": "hi"}]})
    record("Model-not-allowed rejected (403)", status == 403 and body["error"]["code"] == "MODEL_NOT_ALLOWED")

    # 4. Semantic cache: same prompt again should be a HIT
    prompt = {"model": "fast", "messages": [{"role": "user", "content": "Explain what a semantic cache is."}]}
    call(base, "/v1/chat/completions", "POST", auth(good_key), prompt)
    time.sleep(0.3)  # allow async cache store to land
    status, headers, _ = call(base, "/v1/chat/completions", "POST", auth(good_key), prompt)
    record("Repeated prompt is served from semantic cache", headers.get("x-prism-cache") == "HIT")

    # 5. Paraphrased prompt should also hit the cache
    paraphrase = {"model": "fast", "messages": [{"role": "user", "content": "What is a semantic cache, exactly?"}]}
    status, headers, _ = call(base, "/v1/chat/completions", "POST", auth(good_key), paraphrase)
    record("Paraphrased prompt is served from semantic cache", headers.get("x-prism-cache") == "HIT")

    # 6. Budget exhaustion
    # A deliberately large cap creates a deterministic up-front reservation, even though the
    # mock provider later returns a short completion and the ledger is reconciled to actual use.
    status, _, body = call(base, "/v1/chat/completions", "POST", auth(lowbudget_key),
                            {"model": "smart", "max_tokens": 1000,
                             "messages": [{"role": "user", "content": "Write a very long essay about distributed systems, in great depth"}]})
    record("Budget-exhausted key rejected (402)", status == 402 and body["error"]["code"] == "BUDGET_EXHAUSTED")

    # 7. Rate limiting: hammer the restricted key (20 rpm) past its limit
    rejected = False
    for _ in range(25):
        status, _, _ = call(base, "/v1/chat/completions", "POST", auth(restricted_key),
                             {"model": "fast", "messages": [{"role": "user", "content": "ping"}]})
        if status == 429:
            rejected = True
            break
    record("Requests-per-minute limit eventually rejects (429)", rejected)

    # 8. Smart routing: "auto" on an obviously hard prompt should not error and should return 200
    status, headers, _ = call(base, "/v1/chat/completions", "POST", auth(good_key),
                               {"model": "auto", "messages": [{"role": "user", "content": "Prove that sqrt(2) is irrational."}]})
    record("auto alias resolves and responds 200", status == 200)

    # 9. Provider failover: take mock-provider-a down, call a model it serves, expect success via fallback
    call(base, "/admin/providers/mock-provider-a/toggle?healthy=false", "POST")
    status, headers, _ = call(base, "/v1/chat/completions", "POST", auth(good_key),
                               {"model": "fast", "messages": [{"role": "user", "content": "will this fail over?"}]})
    record("Live provider failover: request still succeeds with a downed primary provider",
           status == 200 and headers.get("x-prism-fallback") == "true")
    call(base, "/admin/providers/mock-provider-a/toggle?healthy=true", "POST")  # restore for later runs

    # 10. Usage summary API responds
    status, _, body = call(base, "/v1/usage/summary", "GET", auth(good_key))
    record("Usage summary API responds 200", status == 200 and "totalRequests" in body)

    failures = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failures)}/{len(results)} checks passed.")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
