import time
import requests

LEADER = "http://localhost:5000"
FOLLOWERS = [
    "http://localhost:5001",
    "http://localhost:5002",
    "http://localhost:5003",
    "http://localhost:5004",
    "http://localhost:5005",
]

NUM_KEYS = 10  # key0 .. key9

def main():
    # Wait a bit so remaining replication finishes
    time.sleep(2.0)

    all_ok = True

    for i in range(NUM_KEYS):
        key = f"key{i}"

        # Read from leader
        leader_resp = requests.get(f"{LEADER}/kv/{key}")
        leader_resp.raise_for_status()
        leader_data = leader_resp.json()
        leader_value = leader_data.get("value")
        leader_version = leader_data.get("version")

        print(f"\nKey {key}: leader value={leader_value}, version={leader_version}")

        # Compare on each follower
        for idx, base_url in enumerate(FOLLOWERS, start=1):
            try:
                r = requests.get(f"{base_url}/kv/{key}", timeout=2.0)
                r.raise_for_status()
                data = r.json()
                f_value = data.get("value")
                f_version = data.get("version")

                same = (f_value == leader_value) and (f_version == leader_version)
                status = "OK" if same else "MISMATCH"
                print(f"  follower f{idx}: value={f_value}, version={f_version} -> {status}")

                if not same:
                    all_ok = False
            except Exception as e:
                all_ok = False
                print(f"  follower f{idx}: ERROR ({e})")

    print("\nOverall result:", "ALL MATCH" if all_ok else "SOME MISMATCHES")

if __name__ == "__main__":
    main()
