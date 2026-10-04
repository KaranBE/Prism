#!/usr/bin/env python3
"""
One-command routing evaluation: sends every prompt in eval_set.json through the gateway with
model="auto", infers which tier was actually chosen from the x-prism-provider header's
resolved model, and reports accuracy for Prism's classifier versus a naive length-only
baseline (>N words => "smart"). The eval set deliberately includes short-but-hard and
long-but-trivial traps that a length-only baseline gets wrong by construction.

Usage:
    python scripts/routing_eval.py --base-url http://localhost:8080 --key prism-sk-alpha-0001
"""
import argparse
import json
import urllib.request
from pathlib import Path

LENGTH_BASELINE_WORD_THRESHOLD = 40


def call_auto(base_url, key, prompt):
    url = base_url.rstrip("/") + "/v1/chat/completions"
    body = json.dumps({"model": "auto", "messages": [{"role": "user", "content": prompt}]}).encode()
    req = urllib.request.Request(url, data=body, method="POST",
                                  headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        headers = dict(resp.headers)
        body_json = json.loads(resp.read().decode())
    resolved_model = body_json.get("model", "")
    tier = "smart" if "smart" in resolved_model else "fast"
    return tier, headers.get("x-prism-provider")


def length_only_baseline(prompt):
    return "smart" if len(prompt.split()) > LENGTH_BASELINE_WORD_THRESHOLD else "fast"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--key", default="prism-sk-alpha-0001")
    parser.add_argument("--eval-set", default=str(Path(__file__).parent / "eval_set.json"))
    args = parser.parse_args()

    cases = json.loads(Path(args.eval_set).read_text())

    prism_correct = 0
    baseline_correct = 0
    print(f"{'trap':<18}{'expected':<9}{'prism':<9}{'baseline':<9}prompt")
    print("-" * 100)
    for case in cases:
        prompt = case["prompt"]
        expected = case["expected_tier"]
        trap = case.get("trap", "-")

        prism_tier, _provider = call_auto(args.base_url, args.key, prompt)
        baseline_tier = length_only_baseline(prompt)

        prism_ok = prism_tier == expected
        baseline_ok = baseline_tier == expected
        prism_correct += prism_ok
        baseline_correct += baseline_ok

        short_prompt = (prompt[:47] + "...") if len(prompt) > 50 else prompt
        print(f"{trap:<18}{expected:<9}{prism_tier:<9}{baseline_tier:<9}{short_prompt}")

    total = len(cases)
    print("-" * 100)
    print(f"Prism auto-router accuracy:   {prism_correct}/{total} ({100 * prism_correct / total:.1f}%)")
    print(f"Length-only baseline accuracy:{baseline_correct}/{total} ({100 * baseline_correct / total:.1f}%)")
    if prism_correct > baseline_correct:
        print("\nResult: PASS - Prism's classifier beats the length-only baseline.")
    else:
        print("\nResult: FAIL - Prism's classifier did not beat the length-only baseline.")


if __name__ == "__main__":
    main()
