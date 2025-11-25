import csv
import matplotlib.pyplot as plt

def main():
    qs = []
    lats = []

    with open("results.csv", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            qs.append(int(row["quorum"]))
            lats.append(float(row["avg_latency_ms"]))

    plt.figure(figsize=(6, 4))
    plt.plot(qs, lats, marker="o")
    plt.xlabel("Write quorum (W)")
    plt.ylabel("Average write latency (ms)")
    plt.title("Write quorum vs average latency")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("quorum_vs_latency.png", dpi=150)
    print("Saved plot to quorum_vs_latency.png")

if __name__ == "__main__":
    main()
