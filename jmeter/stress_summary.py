#!/usr/bin/env python3
"""
stress_summary.py - riassume i risultati.jtl di uno o piu' stress in una tabella a finestre di tempo.

USO:  python3 stress_summary.py results/before/stress_*/results.jtl
      python3 stress_summary.py -w 15 results/before/stress_search_*/results.jtl   (finestre da 15 s)

Per ogni finestra (default 30 s) stampa: utenti attivi (massimo), richieste/s, tempo medio,
95-esimo percentile, % di errori. Serve a trovare il punto in cui il throughput smette di salire
o i tempi iniziano a peggiorare (= capacita' dell'applicazione).
"""
import csv
import os
import sys


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0
    k = min(len(sorted_vals) - 1, int(round(p / 100.0 * (len(sorted_vals) - 1))))
    return sorted_vals[k]


def analyze(path, window_s):
    wins = {}
    t0 = None
    with open(path, newline="", encoding="utf-8", errors="replace") as f:
        for r in csv.DictReader(f):
            try:
                ts = int(r["timeStamp"])
                el = int(r["elapsed"])
            except (KeyError, ValueError, TypeError):
                continue
            if t0 is None or ts < t0:
                t0 = ts
            wins.setdefault(ts, []).append((el, r.get("success", "true") == "true", r.get("allThreads")))
    if t0 is None:
        return None
    buckets = {}
    for ts, items in wins.items():
        b = int((ts - t0) / 1000 // window_s)
        buckets.setdefault(b, []).extend(items)
    rows = []
    for b in sorted(buckets):
        items = buckets[b]
        els = sorted(i[0] for i in items)
        errs = sum(1 for i in items if not i[1])
        thr = [int(i[2]) for i in items if i[2] and i[2].isdigit()]
        rows.append((b * window_s, max(thr) if thr else None, len(items) / window_s,
                     sum(els) / len(els), pct(els, 95), 100.0 * errs / len(items), len(items)))
    return rows


def main():
    args = sys.argv[1:]
    window_s = 30
    if args and args[0] == "-w":
        window_s = int(args[1])
        args = args[2:]
    if not args:
        print(__doc__)
        sys.exit(1)
    for p in args:
        rows = analyze(p, window_s)
        name = os.path.basename(os.path.dirname(os.path.abspath(p)))
        print("== %s" % name)
        if not rows:
            print("   (nessun campione)")
            continue
        print("   %8s %8s %10s %9s %9s %7s" % ("da (s)", "utenti", "richieste/s", "medio ms", "p95 ms", "err %"))
        best = max(rows, key=lambda r: r[2])
        total = sum(r[6] for r in rows)
        for r in rows:
            print("   %8d %8s %10.1f %9.1f %9d %7.2f" % (r[0], r[1] if r[1] is not None else "-", r[2], r[3], r[4], r[5]))
        print("   totale richieste: %d   picco: %.1f richieste/s (a %d s)" % (total, best[2], best[0]))


if __name__ == "__main__":
    main()
