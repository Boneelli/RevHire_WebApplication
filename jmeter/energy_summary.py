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
import re
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


TIPI = {"idle": "A riposo (nessun carico)", "load_mixed": "Carico misto (50 utenti)"}


def etichetta(name):
    m = re.match(r"^(.+?)_(\d{8})_(\d{2})(\d{2})(\d{2})$", name)
    if not m:
        return name
    return "%s, ore %s:%s:%s" % (TIPI.get(m.group(1), m.group(1)), m.group(3), m.group(4), m.group(5))


def print_tables(out):
    """tabella leggibile: una riga per misura, energia totale e netta (sopra il consumo a riposo)"""
    cols = []
    for _, r in out:
        for h in r["energia_J"]:
            if h not in cols:
                cols.append(h)
    if not cols:
        print("Nessuna colonna di energia/potenza riconosciuta nei file.")
    for col in cols:
        runs = [(n, r) for n, r in out if col in r["energia_J"]]
        idle = [r["energia_J"][col] / r["durata_s"] for n, r in runs if n.startswith("idle") and r["durata_s"]]
        p_idle = sum(idle) / len(idle) if idle else None
        print("ENERGIA - colonna di EnergiBridge: %s" % col)
        if p_idle is not None:
            print("Consumo a riposo: %.2f W (media di %d misure). Energia netta = energia - (consumo a riposo x durata)." % (p_idle, len(idle)))
        print("%-44s %8s %9s %8s %6s %9s %11s %10s %12s" % ("misura", "durata s", "energia J", "media W", "CPU %", "richieste", "J/richiesta", "netta J", "netta J/ric."))
        carichi = []
        for n, r in runs:
            e, d, req = r["energia_J"][col], r["durata_s"], r.get("richieste")
            netta = e - p_idle * d if p_idle is not None and not n.startswith("idle") else None
            cpu = r.get("cpu_medio_%")
            print("%-44s %8.1f %9.1f %8.2f %6s %9s %11s %10s %12s" % (
                etichetta(n)[:44], d, e, e / d if d else 0, "%.1f" % cpu if cpu is not None else "-", req or "-",
                "%.4f" % (e / req) if req else "-", "%.1f" % netta if netta is not None else "-",
                "%.5f" % (netta / req) if netta is not None and req else "-"))
            if not n.startswith("idle") and req:
                carichi.append((e, e / req, netta, netta / req if netta is not None else None))
        if len(carichi) > 1:
            def m(i):
                v = [c[i] for c in carichi if c[i] is not None]
                return sum(v) / len(v) if v else None

            def sp(i):
                v = [c[i] for c in carichi if c[i] is not None]
                return 100.0 * (max(v) - min(v)) / (sum(v) / len(v)) if v else None
            print("MEDIA DELLE %d MISURE SOTTO CARICO: energia %.1f J (variazione tra le misure %.1f%%), %.4f J/richiesta" % (
                len(carichi), m(0), sp(0), m(1)))
            if m(2) is not None:
                print("                                    energia netta %.1f J (variazione %.1f%%), %.5f J/richiesta netti" % (m(2), sp(2), m(3)))
        print()


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
        print_tables(out)
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
