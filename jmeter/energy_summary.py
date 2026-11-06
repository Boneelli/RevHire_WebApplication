#!/usr/bin/env python3
"""
energy_summary.py - riassume i file energy.csv prodotti da EnergiBridge (via run_jmeter.sh ENERGY=1).

USO:  python3 energy_summary.py results/before/*/energy.csv
      python3 energy_summary.py --csv results/before/*/energy.csv > riepilogo.csv

Per ogni file stampa durata, energia totale, potenza media, uso medio della CPU e, se accanto
al file c'e' results.jtl, il numero di richieste e l'energia per richiesta (J/richiesta).

I nomi delle colonne di EnergiBridge cambiano da piattaforma a piattaforma: lo script riconosce
  - colonne di ENERGIA cumulativa (nome con ENERGY): energia = ultimo - primo valore (in J)
  - colonne di POTENZA istantanea (nome con POWER): energia = integrale nel tempo (in W)
  - colonne CPU_USAGE: media
e stampa quali ha usato (riga 'colonne'), cosi' si puo' controllare.
"""
import csv
import os
import sys


def num(x):
    try:
        v = float(x)
    except (TypeError, ValueError):
        return None
    return None if v != v else v  # scarta i NaN (EnergiBridge li scrive per alcune colonne)


def analyze(path):
    with open(path, newline="", encoding="utf-8", errors="replace") as f:
        rows = list(csv.reader(f))
    if len(rows) < 3:
        return None
    header = [h.strip() for h in rows[0]]
    data = [r for r in rows[1:] if len(r) == len(header)]
    cols = {h: [num(r[i]) for r in data] for i, h in enumerate(header)}

    # asse dei tempi in secondi
    if "Time" in cols and all(v is not None for v in cols["Time"]):
        t = [(v - cols["Time"][0]) / 1000.0 for v in cols["Time"]]
    elif "Delta" in cols:
        acc, t = 0.0, []
        for v in cols["Delta"]:
            acc += (v or 0.0)
            t.append(acc / 1000.0)
    else:
        return {"errore": "colonna Time/Delta non trovata: " + ", ".join(header[:12])}
    duration = t[-1] - t[0]

    res = {"durata_s": duration, "campioni": len(data), "colonne": []}
    energy_j = {}
    for h, vals in cols.items():
        up = h.upper()
        clean = [v for v in vals if v is not None]
        if len(clean) < 2:
            continue
        if "ENERGY" in up:
            tot = sum(max(b - a, 0.0) for a, b in zip(clean, clean[1:]))  # tollera azzeramenti
            energy_j[h] = tot
            res["colonne"].append(h + " (cumulativa)")
        elif "POWER" in up:
            tot = 0.0
            for i in range(1, len(vals)):
                if vals[i] is not None and vals[i - 1] is not None:
                    tot += 0.5 * (vals[i] + vals[i - 1]) * (t[i] - t[i - 1])
            energy_j[h] = tot
            res["colonne"].append(h + " (potenza)")
    res["energia_J"] = energy_j
    usage = [h for h in cols if h.upper().startswith("CPU_USAGE")]
    if usage:
        per_sample = []
        for i in range(len(data)):
            vs = [cols[h][i] for h in usage if cols[h][i] is not None]
            if vs:
                per_sample.append(sum(vs) / len(vs))
        res["cpu_medio_%"] = sum(per_sample) / len(per_sample) if per_sample else None

    jtl = os.path.join(os.path.dirname(path), "results.jtl")
    if os.path.exists(jtl):
        with open(jtl, encoding="utf-8", errors="replace") as f:
            res["richieste"] = max(sum(1 for _ in f) - 1, 0)
    return res


def main():
    args = [a for a in sys.argv[1:] if a != "--csv"]
    as_csv = "--csv" in sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    out = []
    for p in args:
        r = analyze(p)
        name = os.path.basename(os.path.dirname(os.path.abspath(p)))
        if r is None or "errore" in (r or {}):
            print("%s: file non analizzabile %s" % (name, (r or {}).get("errore", "")), file=sys.stderr)
            continue
        out.append((name, r))
        if not as_csv:
            print("== %s" % name)
            print("   durata: %.1f s   campioni: %d" % (r["durata_s"], r["campioni"]))
            print("   colonne: " + (", ".join(r["colonne"]) or "(nessuna colonna di energia/potenza riconosciuta)"))
            for h, e in r["energia_J"].items():
                line = "   %-32s energia %9.2f J   potenza media %7.2f W" % (h, e, e / r["durata_s"] if r["durata_s"] else 0)
                if r.get("richieste"):
                    line += "   %8.4f J/richiesta" % (e / r["richieste"])
                print(line)
            if r.get("cpu_medio_%") is not None:
                print("   uso medio CPU: %.1f %%" % r["cpu_medio_%"])
            if r.get("richieste"):
                print("   richieste: %d" % r["richieste"])
    if as_csv:
        w = csv.writer(sys.stdout)
        w.writerow(["run", "durata_s", "colonna", "energia_J", "potenza_media_W", "richieste", "J_per_richiesta", "cpu_medio_%"])
        for name, r in out:
            for h, e in r["energia_J"].items():
                req = r.get("richieste")
                w.writerow([name, "%.1f" % r["durata_s"], h, "%.3f" % e, "%.3f" % (e / r["durata_s"] if r["durata_s"] else 0),
                            req or "", "%.5f" % (e / req) if req else "", "%.1f" % r["cpu_medio_%"] if r.get("cpu_medio_%") is not None else ""])


if __name__ == "__main__":
    main()
