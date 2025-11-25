import os
import time
import random
import threading

from flask import Flask, request, jsonify
import requests

app = Flask(__name__)

NODE_ROLE = os.environ.get("NODE_ROLE", "leader")
PORT = int(os.environ.get("PORT", "5000"))

if NODE_ROLE == "leader":
    raw = os.environ.get("FOLLOWER_URLS", "")
    FOLLOWER_URLS = [u.strip() for u in raw.split(",") if u.strip()]
    WRITE_QUORUM = int(os.environ.get("WRITE_QUORUM", "1"))
    MIN_DELAY = float(os.environ.get("MIN_DELAY_MS", "0")) / 1000.0
    MAX_DELAY = float(os.environ.get("MAX_DELAY_MS", "1000")) / 1000.0
else:
    FOLLOWER_URLS = []
    WRITE_QUORUM = 0
    MIN_DELAY = 0.0
    MAX_DELAY = 0.0

store = {}
store_lock = threading.Lock()

current_version = 0
version_lock = threading.Lock()


@app.route("/kv/<key>", methods=["GET"])
def get_key(key):
    with store_lock:
        entry = store.get(key)
    if entry is None:
        return jsonify({"value": None}), 200
    version, value = entry
    return jsonify({"value": value, "version": version}), 200


@app.route("/kv/<key>", methods=["PUT"])
def put_key(key):
    if NODE_ROLE != "leader":
        return jsonify({"error": "writes_allowed_only_on_leader"}), 400

    data = request.get_json(force=True) or {}
    if "value" not in data:
        return jsonify({"error": "missing_value"}), 400
    value = data["value"]

    global current_version
    with version_lock:
        current_version += 1
        write_version = current_version

    start = time.time()
    with store_lock:
        store[key] = (write_version, value)

    if not FOLLOWER_URLS:

        latency_ms = (time.time() - start) * 1000.0
        return jsonify({
            "status": "ok",
            "acks": 0,
            "required": 0,
            "latency_ms": latency_ms,
            "version": write_version
        }), 200

    quorum_event = threading.Event()
    ack_lock = threading.Lock()
    ack_count = 0

    def replicate_to(url):
        nonlocal ack_count
        delay = random.uniform(MIN_DELAY, MAX_DELAY)
        time.sleep(delay)
        try:
            resp = requests.post(
                f"{url}/replicate",
                json={"key": key, "value": value, "version": write_version},
                timeout=3.0,
            )
            if resp.status_code == 200:
                with ack_lock:
                    ack_count += 1
                    if ack_count >= WRITE_QUORUM:
                        quorum_event.set()
        except Exception:
            # follower unavailable or network error; ignore
            pass

    threads = []
    for url in FOLLOWER_URLS:
        t = threading.Thread(target=replicate_to, args=(url,), daemon=True)
        t.start()
        threads.append(t)

    quorum_event.wait(timeout=5.0)

    with ack_lock:
        success = ack_count >= WRITE_QUORUM

    latency_ms = (time.time() - start) * 1000.0
    status_code = 200 if success else 500
    return jsonify({
        "status": "ok" if success else "error",
        "acks": ack_count,
        "required": WRITE_QUORUM,
        "latency_ms": latency_ms,
        "version": write_version
    }), status_code


@app.route("/replicate", methods=["POST"])
def replicate():
    if NODE_ROLE != "follower":
        return jsonify({"error": "only_followers_replicate"}), 400

    data = request.get_json(force=True) or {}
    key = data.get("key")
    value = data.get("value")
    version = data.get("version")

    if key is None or version is None:
        return jsonify({"error": "invalid_replication_request"}), 400

    with store_lock:
        existing = store.get(key)
        if existing is None or version >= existing[0]:
            store[key] = (version, value)

    return jsonify({"status": "ok"}), 200


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "role": NODE_ROLE,
        "port": PORT
    }), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, threaded=True)
