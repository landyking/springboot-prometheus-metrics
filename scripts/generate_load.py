#!/usr/bin/env python3
"""Generate deliberately uneven traffic with a repeatable one-minute cycle."""

import concurrent.futures
import os
import random
import time
import urllib.error
import urllib.request

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080").rstrip("/")
CYCLE_SECONDS = max(20, int(os.getenv("CYCLE_SECONDS", "60")))


def traffic_profile(second):
    quarter = CYCLE_SECONDS / 4
    if second < quarter:
        return 1, "trough"
    if second < quarter * 2:
        progress = (second - quarter) / quarter
        return round(2 + progress * 8), "rising"
    if second < quarter * 3:
        return 12, "peak"
    progress = (second - quarter * 3) / quarter
    return round(8 - progress * 6), "falling"


def choose_request():
    # Intentionally unbalanced: greeting 50%, compute 25%, memory 15%, orders 10%.
    choice = random.random()
    if choice < 0.50:
        return "GET", "/api/greeting?name=Grafana"
    if choice < 0.75:
        size = random.choice((10_000, 25_000, 60_000, 120_000))
        return "GET", f"/api/compute?size={size}"
    if choice < 0.90:
        megabytes = random.choice((8, 16, 24, 32))
        return "POST", f"/api/memory-pressure?megabytes={megabytes}"
    return "POST", "/api/orders"


def make_request():
    method, path = choose_request()
    request = urllib.request.Request(BASE_URL + path, method=method)
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except (urllib.error.URLError, TimeoutError):
        return 0


def main():
    print(f"Sending traffic to {BASE_URL}; cycle={CYCLE_SECONDS}s", flush=True)
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        while True:
            tick = time.monotonic()
            cycle_second = int(tick - started) % CYCLE_SECONDS
            requests_per_second, phase = traffic_profile(cycle_second)
            results = list(pool.map(lambda _: make_request(), range(requests_per_second)))
            if cycle_second % 5 == 0:
                errors = sum(status == 0 or status >= 500 for status in results)
                print(
                    f"phase={phase:<7} rate={requests_per_second:>2}/s errors={errors}",
                    flush=True,
                )
            elapsed = time.monotonic() - tick
            time.sleep(max(0, 1 - elapsed))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("Stopped.")
