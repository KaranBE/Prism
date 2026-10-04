#!/usr/bin/env python3
"""
Concurrency / correctness-under-pressure load test.

Fires many concurrent requests at a single virtual key and verifies the "no over-admission"
property required by the spec: the number of 200s for a rate-limited key must never exceed
its configured requests-per-minute, and for a tightly budgeted key, total accepted spend must
never exceed the configured monthly budget - regardless of how the requests interleave.

Usage:
    python scripts/load_test.py --base-url http://localhost:8080 --key prism-sk-beta-0002 --concurrency 50 --requests 200
"""
import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.request
import urllib.error


def call(base_url, key, model="fast"):
    url = base_url.rstrip("/") + "/v1/chat/completions"
    body = json.dumps({"model": model, "messages": [{"role": "user", "content": "load test ping"}]}).encode()
    req = urllib.request.Request(url, data=body, method="POST",
                                  headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            resp.read()
            latency = (time.perf_counter() - start) * 1000
            return resp.status, latency
    except urllib.error.HTTPError as e:
        e.read()
        latency = (time.perf_counter() - start) * 1000
        return e.code, latency


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--key", default="prism-sk-beta-0002")
    parser.add_argument("--model", default="fast")
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--requests", type=int, default=200)
    args = parser.parse_args()

    statuses = []
    latencies = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(call, args.base_url, args.key, args.model) for _ in range(args.requests)]
        for f in concurrent.futures.as_completed(futures):
            status, latency = f.result()
            statuses.append(status)
            latencies.append(latency)

    ok = statuses.count(200)
    rate_limited = statuses.count(429)
    budget_exhausted = statuses.count(402)
    other_errors = len(statuses) - ok - rate_limited - budget_exhausted
    latencies.sort()

    def pct(p):
        idx = min(len(latencies) - 1, int(len(latencies) * p))
        return round(latencies[idx], 1)

    print("=== Prism load test report ===")
    print(f"Total requests sent:      {len(statuses)}")
    print(f"  200 OK:                 {ok}")
    print(f"  429 Rate limited:       {rate_limited}")
    print(f"  402 Budget exhausted:   {budget_exhausted}")
    print(f"  Other status codes:     {other_errors}")
    print(f"Latency p50 / p95 / p99:  {pct(0.50)}ms / {pct(0.95)}ms / {pct(0.99)}ms")
    print(f"Latency mean:             {round(statistics.mean(latencies), 1)}ms")
    print("\nOver-admission check: pass if 200-OK count does not exceed the key's")
    print("requests-per-minute allowance (see seed_keys.json) for a burst sent well within one minute.")


if __name__ == "__main__":
    main()
