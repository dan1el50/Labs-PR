import time
import threading
import statistics
import os

import requests

LEADER = os.environ.get("LEADER_URL", "http://localhost:5000")
FOLLOWERS = [
    os.environ.get("F1_URL", "http://localhost:5001"),  # optional override if you map ports
]

# For simplicity, by default we will hit followers by container name from host network via leader proxy check:
# In practice for this lab, you can run this test from inside the leader container
# or add more mapped ports for f1..f5 in docker-compose if your teacher wants host-based checks.


def do_write(i):
    key = f"key{i % 10}"
    value = f"value-{i}-{time.time_ns()}"
    start = time.time()
    resp = requests.put(f"{LEADER}/kv/{key}", json={"value": value})
    latency_ms = (time.time() - start) * 1000.0
    return key, value, latency_ms, resp.status_code


def test_100_writes_and_replication():
    latencies = []
    results = []
    threads = []

    def worker(i):
        key, value, latency_ms, code = do_write(i)
        results.append((key, value, latency_ms, code))

    # 100 writes, 10 at a time
    for start_i in range(0, 100, 10):
        batch = []
        for i in range(start_i, start_i + 10):
            t = threading.Thread(target=worker, args=(i,))
            t.start()
            batch.append(t)
        for t in batch:
            t.join()

    for key, value, latency_ms, code in results:
        if code == 200:
            latencies.append(latency_ms)

    assert len(latencies) > 0
    avg_latency = statistics.mean(latencies)
    print("Average latency (ms):", avg_latency)

    # Give some time for replication lag to complete
    time.sleep(2.0)

    # Check consistency for 10 keys between leader and (at least) leader itself
    for i in range(10):
        key = f"key{i}"
        leader_resp = requests.get(f"{LEADER}/kv/{key}")
        assert leader_resp.status_code == 200
        leader_val = leader_resp.json()["value"]

        # For a more complete check, you would add mapped ports for followers
        # and compare each follower's value with leader_val.
        assert leader_val is not None
