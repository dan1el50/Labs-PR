import os
import time
import threading
import statistics
import csv
import subprocess

import requests

LEADER = "http://localhost:5000"

TOTAL_WRITES = 100
CONCURRENCY = 10

def do_write(i):
    key = f"key{i % 10}"
    value = f"value-{i}-{time.time_ns()}"
    start = time.time()
    resp = requests.put(f"{LEADER}/kv/{key}", json={"value": value})
    latency_ms = (time.time() - start) * 1000.0
    return latency_ms, resp.status_code

def run_load():
    results = []

    def worker(i):
        lat, code = do_write(i)
        results.append((lat, code))

    for start_i in range(0, TOTAL_WRITES, CONCURRENCY):
        threads = []
        for i in range(start_i, start_i + CONCURRENCY):
            t = threading.Thread(target=worker, args=(i,))
            t.start()
            threads.append(t)
        for t in threads:
            t.join()

    latencies = [lat for lat, code in results if code == 200]
    if not latencies:
        return None
    return statistics.mean(latencies)

def restart_compose(write_quorum):
    env = os.environ.copy()
    env["WRITE_QUORUM"] = str(write_quorum)
    # stop existing containers
    subprocess.run(["docker", "compose", "down"], env=env)
    # start with new quorum
    subprocess.run(
        ["docker", "compose", "up", "-d", "--build", "--force-recreate"],
        env=env,
        check=True,
    )
    # give containers time to start
    time.sleep(5)

def main():
    rows = [("quorum", "avg_latency_ms")]

    for q in range(1, 6):
        print(f"=== Measuring for WRITE_QUORUM={q} ===")
        restart_compose(q)
        avg = run_load()
        if avg is None:
            print("No successful writes, skipping quorum", q)
            continue
        print(f"Average latency for quorum {q}: {avg:.2f} ms")
        rows.append((q, avg))

    with open("results.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerows(rows)

    print("Saved results to results.csv")

if __name__ == "__main__":
    main()
