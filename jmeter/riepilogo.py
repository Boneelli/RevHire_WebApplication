#!/usr/bin/env python3
"""
riepilogo.py - riepiloghi leggibili dei test JMeter (load, stress, spike, soak), con i nomi delle funzionalita'.

USO (dalla cartella jmeter/, sui results.jtl):
  python3 riepilogo.py load   results/before/load_*/results.jtl
  python3 riepilogo.py stress results/before/stress_*/results.jtl
  python3 riepilogo.py spike  results/before/spike_*/results.jtl
  python3 riepilogo.py soak   results/before/soak_*/results.jtl

Ogni riepilogo inizia con una LEGENDA che dice a quale endpoint corrisponde ogni nome
(es. "Ricerca job" = GET /jobseeker/jobs/search?title=java).

  load    una tabella per run con una riga per funzionalita'; media delle ripetizioni (es. 3 load misti)
          opzione: --skip S   scarta i primi S secondi (salita degli utenti), default 0
  stress  una tabella per run con una riga per funzionalita' e per gradino di utenti (solo il regime
          stabile di ogni gradino)       opzioni: --step 90  --ramp 20
  spike   totali per fase (prima / salita / picco / dopo) e, per funzionalita', tempi prima, durante e
          dopo il picco                  opzioni: --pre 120  --ramp 10  --hold 120  --post 180
  soak    totali per fasce di tempo e, per funzionalita', confronto tra la prima e l'ultima fascia
                                         opzioni: --skip 60  --slice 300

Le opzioni vanno lasciate ai valori predefiniti, che sono quelli usati da misure_reali.sh.
Le run con un energy.csv accanto (sessione energia) sono tenute separate da quelle della sessione performance.
Tempi in millisecondi; p95 = il 95% delle richieste e' piu' veloce di questo valore.
"""
import csv
import os
import re
import sys

# (inizio dell'etichetta JMeter, nome leggibile). "=" = etichetta esatta. Vince la prima che corrisponde;
# l'ordine e' anche quello di stampa (dal login al percorso dell'employer).
NOMI = [
    ("POST /auth/login (seeker)", "Login seeker"),
    ("POST /auth/login (employer)", "Login employer"),
    ("POST /auth/register", "Registrazione"),
    ("=GET /jobs", "Lista job"),
    ("GET /jobs/get", "Dettaglio job"),
    ("GET /jobseeker/jobs/search", "Ricerca job"),
    ("GET /jobseeker/dashboard", "Dashboard seeker"),
    ("POST /applications/submit", "Candidarsi a un job"),
    ("GET /applications/seeker", "Le mie candidature"),
    ("POST /api/jobseeker/favorites", "Aggiungi ai preferiti"),
    ("GET /api/jobseeker/favorites", "Lista preferiti"),
    ("GET /api/resume/resume-pdf", "CV in PDF"),
    ("GET /employer/dashboard", "Dashboard employer"),
    ("GET /applications/employer", "Candidature ricevute"),
    ("POST /applications/update-status", "Cambio stato candidatura"),
]
SCENARI = {"search": "Ricerca job", "login": "Login", "pdf": "CV in PDF", "dashboard": "Dashboard seeker",
           "employer_dash": "Dashboard employer", "register": "Registrazione",
           "mixed": "Uso misto (seeker + employer)", "seeker": "Percorso seeker"}


def nome(label):
    for i, (pre, n) in enumerate(NOMI):
        if (pre[0] == "=" and label == pre[1:]) or (pre[0] != "=" and label.startswith(pre)):
            return n, i
    return label, len(NOMI)


def pct(v, p):
    if not v:
        return 0
    return v[min(len(v) - 1, max(0, int(round(p / 100.0 * len(v) + 0.5)) - 1))]


def carica(path):
    righe = []
    with open(path, newline="", encoding="utf-8", errors="replace") as f:
        for r in csv.DictReader(f):
            try:
                th = r.get("allThreads", "")
                righe.append((int(r["timeStamp"]), int(r["elapsed"]), r["label"], r.get("success") == "true",
                              int(th) if th.isdigit() else None))
            except (KeyError, ValueError, TypeError):
                continue
    if not righe:
        return None
    t0 = min(r[0] for r in righe)
    return [((r[0] - t0) / 1000.0, r[1], r[2], r[3], r[4]) for r in righe]


def finestra(righe, a, b):
    return [r for r in righe if a <= r[0] < b]


def calcola(righe, durata):
    if not righe:
        return None
    el = sorted(r[1] for r in righe)
    n = len(el)
    err = sum(1 for r in righe if not r[3])
    return dict(n=n, avg=sum(el) / n, med=pct(el, 50), p90=pct(el, 90), p95=pct(el, 95), p99=pct(el, 99),
                max=el[-1], err=100.0 * err / n, rps=n / durata if durata > 0 else 0.0)


def per_funzione(righe):
    d = {}
    for r in righe:
        d.setdefault(r[2], []).append(r)
    return sorted(d.items(), key=lambda kv: (nome(kv[0])[1], kv[0]))


def utenti(righe):
    v = [r[4] for r in righe if r[4] is not None]
    return max(set(v), key=v.count) if v else None


def titolo_run(path):
    d = os.path.dirname(os.path.abspath(path))
    base = os.path.basename(d)
    m = re.match(r"^([a-z]+)_(.+?)_\d{8}_\d{6}$", base)
    test, scen = (m.group(1), m.group(2)) if m else (base, "")
    energia = os.path.exists(os.path.join(d, "energy.csv"))
    return test, scen, energia, base


def intestazione(test, scen, energia, base):
    t = "%s · %s" % (test.upper(), SCENARI.get(scen, scen))
    return "== %s%s   [%s]" % (t, " (sessione energia)" if energia else "", base)


def legenda(tutte_le_label):
    visti = {}
    for lab in tutte_le_label:
        n, i = nome(lab)
        visti.setdefault((i, n), set()).add(lab)
    print("LEGENDA (nome -> richiesta JMeter)")
    for (i, n), labs in sorted(visti.items()):
        print("   %-26s %s" % (n, "  |  ".join(sorted(labs))))
    print()


W = 28
H = "   %-{w}s %8s %8s %7s %7s %7s %8s %7s %8s".replace("{w}", str(W))
R = "   %-{w}s %8d %8.1f %7d %7d %7d %8d %7.2f %8.1f".replace("{w}", str(W))
HEAD = ("funzionalita'", "n", "medio", "mediana", "p95", "p99", "max", "err %", "req/s")


def riga(n, s):
    return R % (n[:W], s["n"], s["avg"], s["med"], s["p95"], s["p99"], s["max"], s["err"], s["rps"])


def tabella_funzioni(righe, a, b):
    """stampa una riga per funzionalita' + TOTALE nella finestra [a,b)"""
    w = finestra(righe, a, b)
    dur = min(b, max(r[0] for r in righe)) - a
    for lab, rs in per_funzione(w):
        print(riga(nome(lab)[0], calcola(rs, dur)))
    print(riga("TOTALE", calcola(w, dur)))


# ------------------------------------------------------------------ load
def cmd_load(files, opz):
    skip = float(opz.get("skip", 0))
    runs = []
    for p in files:
        righe = carica(p)
        if righe:
            runs.append((titolo_run(p), righe))
    legenda([r[2] for _, rs in runs for r in rs])
    gruppi = {}
    for (test, scen, en, base), righe in runs:
        righe = [r for r in righe if r[0] >= skip]
        dur = max(r[0] for r in righe) - min(r[0] for r in righe)
        tot = calcola(righe, dur)
        print(intestazione(test, scen, en, base) + "   (durata %.0f s, %d richieste, errori %.2f%%)" % (dur, tot["n"], tot["err"]))
        print(H % HEAD)
        stats = {}
        for lab, rs in per_funzione(righe):
            stats[lab] = calcola(rs, dur)
            print(riga(nome(lab)[0], stats[lab]))
        stats["TOTALE"] = tot
        print(riga("TOTALE", tot))
        print()
        gruppi.setdefault((test, scen, en), []).append(stats)
    for (test, scen, en), lst in gruppi.items():
        if len(lst) < 2:
            continue
        print("== MEDIA DELLE %d RIPETIZIONI: %s · %s%s" % (len(lst), test.upper(), SCENARI.get(scen, scen), " (sessione energia)" if en else ""))
        print("   %-28s %9s %10s %10s %10s %8s" % ("funzionalita'", "medio", "min-medio", "max-medio", "p95 medio", "err %"))
        labs = sorted(set().union(*[set(s) - {"TOTALE"} for s in lst]), key=lambda l: (nome(l)[1], l)) + ["TOTALE"]
        for lab in labs:
            v = [s[lab] for s in lst if lab in s]
            avg = [x["avg"] for x in v]
            print("   %-28s %9.1f %10.1f %10.1f %10.1f %8.2f" % (
                (nome(lab)[0] if lab != "TOTALE" else lab)[:28], sum(avg) / len(avg), min(avg), max(avg),
                sum(x["p95"] for x in v) / len(v), sum(x["err"] for x in v) / len(v)))
        print()


# ------------------------------------------------------------------ stress
def cmd_stress(files, opz):
    step, ramp = float(opz.get("step", 90)), float(opz.get("ramp", 20))
    runs = []
    for p in files:
        righe = carica(p)
        if righe:
            runs.append((titolo_run(p), righe))
    legenda([r[2] for _, rs in runs for r in rs])
    for (test, scen, en, base), righe in runs:
        print(intestazione(test, scen, en, base))
        print("   (solo il regime stabile di ogni gradino: esclusi i primi %.0f s, in cui entrano i nuovi utenti)" % ramp)
        print("   %-10s" % "utenti" + (H % HEAD)[3:])
        tmax = max(r[0] for r in righe)
        k = 0
        picchi = []
        while k * step + ramp + 20 < tmax:
            a, b = k * step + ramp, (k + 1) * step
            w = finestra(righe, a, b)
            if not w:
                break
            dur = min(b, tmax) - a
            u = utenti(w)
            prima = True
            for lab, rs in per_funzione(w):
                s = calcola(rs, dur)
                print("   %-10s" % (("%d utenti" % u) if prima and u else "") + riga(nome(lab)[0], s)[3:])
                prima = False
            tot = calcola(w, dur)
            print("   %-10s" % "" + riga("TOTALE", tot)[3:])
            picchi.append((u, tot))
            k += 1
        if picchi:
            best = max(picchi, key=lambda x: x[1]["rps"])
            print("   -> throughput massimo: %.0f richieste/s (con %s utenti); a %s utenti il tempo medio e' %.1f ms" % (
                best[1]["rps"], best[0], picchi[-1][0], picchi[-1][1]["avg"]))
        print()


# ------------------------------------------------------------------ spike
def cmd_spike(files, opz):
    pre, ramp = float(opz.get("pre", 120)), float(opz.get("ramp", 10))
    hold, post = float(opz.get("hold", 120)), float(opz.get("post", 180))
    warm = 20.0
    fasi = [("prima del picco", warm, pre), ("salita", pre, pre + ramp), ("picco", pre + ramp, pre + ramp + hold),
            ("dopo: primi 30 s", pre + ramp + hold, pre + ramp + hold + 30),
            ("dopo: da 30 s", pre + ramp + hold + 30, pre + ramp + hold + post)]
    runs = []
    for p in files:
        righe = carica(p)
        if righe:
            runs.append((titolo_run(p), righe))
    legenda([r[2] for _, rs in runs for r in rs])
    for (test, scen, en, base), righe in runs:
        print(intestazione(test, scen, en, base))
        print("   Totali per fase  (prima: esclusi i primi %.0f s di avvio)" % warm)
        print("   %-18s %7s %8s %8s %7s %7s %8s %7s" % ("fase", "utenti", "req/s", "medio", "p95", "p99", "max", "err %"))
        for nf, a, b in fasi:
            w = finestra(righe, a, b)
            if not w:
                continue
            s = calcola(w, b - a)
            print("   %-18s %7s %8.1f %8.1f %7d %7d %8d %7.2f" % (nf, utenti(w) or "-", s["rps"], s["avg"], s["p95"], s["p99"], s["max"], s["err"]))
        print()
        print("   Per funzionalita' (tempo medio e p95, in ms)")
        print("   %-28s %17s %17s %17s %12s" % ("funzionalita'", "prima", "picco", "dopo (da 30 s)", "picco/prima"))
        print("   %-28s %8s %8s %8s %8s %8s %8s %12s" % ("", "medio", "p95", "medio", "p95", "medio", "p95", "medio"))
        fp, fk, fd = fasi[0], fasi[2], fasi[4]
        for lab, rs in per_funzione(righe):
            cols, medi = [], []
            for nf, a, b in (fp, fk, fd):
                s = calcola(finestra(rs, a, b), b - a)
                cols += [("%.1f" % s["avg"]), ("%d" % s["p95"])] if s else ["-", "-"]
                medi.append(s["avg"] if s else None)
            var = "%+.0f%%" % (100.0 * (medi[1] / medi[0] - 1)) if medi[0] and medi[1] else "-"
            print("   %-28s %8s %8s %8s %8s %8s %8s %12s" % (nome(lab)[0][:28], *cols, var))
        print()


# ------------------------------------------------------------------ soak
def cmd_soak(files, opz):
    skip, sl = float(opz.get("skip", 60)), float(opz.get("slice", 300))
    runs = []
    for p in files:
        righe = carica(p)
        if righe:
            runs.append((titolo_run(p), righe))
    legenda([r[2] for _, rs in runs for r in rs])
    for (test, scen, en, base), righe in runs:
        print(intestazione(test, scen, en, base))
        tmax = max(r[0] for r in righe)
        fasce, a = [], skip
        while a < tmax:
            b = min(a + sl, tmax)
            if b - a >= sl / 2.0 or not fasce:
                fasce.append((a, b if b - a >= sl / 2.0 else a + sl))
            a += sl
        print("   Totali per fascia di tempo  (esclusi i primi %.0f s di avvio)" % skip)
        print("   %-14s %7s %8s %8s %7s %7s %8s %7s" % ("minuti", "utenti", "req/s", "medio", "p95", "p99", "max", "err %"))
        for a, b in fasce:
            w = finestra(righe, a, b)
            if not w:
                continue
            s = calcola(w, min(b, tmax) - a)
            print("   %-14s %7s %8.1f %8.1f %7d %7d %8d %7.2f" % ("%.0f-%.0f" % (a / 60, min(b, tmax) / 60), utenti(w) or "-", s["rps"], s["avg"], s["p95"], s["p99"], s["max"], s["err"]))
        print()
        (a0, b0), (a1, b1) = fasce[0], fasce[-1]
        print("   Per funzionalita': prima fascia (%.0f-%.0f min) contro ultima (%.0f-%.0f min)" % (a0 / 60, b0 / 60, a1 / 60, min(b1, tmax) / 60))
        print("   %-28s %17s %17s %12s" % ("funzionalita'", "prima", "ultima", "variazione"))
        print("   %-28s %8s %8s %8s %8s %12s" % ("", "medio", "p95", "medio", "p95", "medio"))
        for lab, rs in per_funzione(righe):
            s0 = calcola(finestra(rs, a0, b0), b0 - a0)
            s1 = calcola(finestra(rs, a1, b1), min(b1, tmax) - a1)
            if not s0 or not s1:
                continue
            print("   %-28s %8.1f %8d %8.1f %8d %12s" % (nome(lab)[0][:28], s0["avg"], s0["p95"], s1["avg"], s1["p95"], "%+.0f%%" % (100.0 * (s1["avg"] / s0["avg"] - 1))))
        print()


def main():
    args = sys.argv[1:]
    if not args or args[0] not in ("load", "stress", "spike", "soak"):
        print(__doc__)
        sys.exit(1)
    cmd, args = args[0], args[1:]
    opz, files, i = {}, [], 0
    while i < len(args):
        if args[i].startswith("--") and i + 1 < len(args):
            opz[args[i][2:]] = args[i + 1]
            i += 2
        else:
            files.append(args[i])
            i += 1
    if not files:
        print("Nessun file results.jtl indicato.")
        sys.exit(1)
    {"load": cmd_load, "stress": cmd_stress, "spike": cmd_spike, "soak": cmd_soak}[cmd](files, opz)


if __name__ == "__main__":
    main()
