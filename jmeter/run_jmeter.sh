#!/usr/bin/env bash
# =====================================================================
# run_jmeter.sh  -  pipeline dei test di carico JMeter per RevHire
#
# Va messo nella cartella jmeter/ (accanto ai .jmx, a db/ e a data/).
#
# USO
#   ./run_jmeter.sh baseline                      1 utente, ogni scenario, 30 s: verifica + tempi per endpoint
#   ./run_jmeter.sh run <test> <scenario> [-Jchiave=valore ...]
#        test:     load | stress | spike | soak
#        scenario: search | login | pdf | register | dashboard | employer_dash | seeker | employer | mixed
#   ./run_jmeter.sh energy                        energia: idle + run pesanti con EnergiBridge (~35 min)
#   ./run_jmeter.sh stress_all                    i 5 stress (~80 min)
#   ./run_jmeter.sh load_spike_soak               load, spike e soak (~1,5 ore con soak da 30 min)
#   ./run_jmeter.sh suite                         stress_all + load_spike_soak
#   ./run_jmeter.sh idle [secondi]                misura l'energia a riposo (app avviata, nessun carico)
#
# VARIABILI D'AMBIENTE (tutte opzionali)
#   LABEL         nome della serie di risultati, es. before / after       (default: before)
#   APP_JAR       percorso del jar: se impostato, lo script avvia e ferma l'app ad ogni run
#                 con le stesse opzioni JVM; se non impostato, l'app deve essere gia' avviata
#   APP_ARGS      argomenti extra per l'app (es. --spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1)
#   HEAP          heap della JVM dell'app                                  (default: 1g)
#   RESET_DB      1 = prima di ogni run clean + populate + export dei CSV (default: 1)
#   DB_CONTAINER  nome del container Oracle                                (default: oracle-db)
#   DB_CONN       connessione sqlplus                                      (default: system/system@//localhost:1521/FREEPDB1)
#   HOST, PORT    dove risponde l'app                                      (default: localhost, 8082)
#   SOAK_S        durata del soak nella suite, in secondi                  (default: 3600)
#   LOAD_REPS     ripetizioni dei load                                     (default: 1)
#   LOAD_USERS    utenti virtuali dei load                                 (default: 20)
#   SOAK_USERS    utenti virtuali del soak                                 (default: 20)
#   COOLDOWN_S    pausa tra un run e l'altro nelle fasi, per raffreddare   (default: 60)
#   ENERGY_REPS   ripetizioni del run pesante nella fase energy            (default: 3)
#   IDLE_S        durata della misura a riposo, in secondi                 (default: 300)
#   JMETER_BIN    comando jmeter                                           (default: jmeter)
#   ENERGY        1 = misura l'energia con EnergiBridge durante JMeter     (default: 0)
#                 (chiede la password sudo; salva energy.csv nella cartella del run)
#   ENERGIBRIDGE_BIN  percorso di energibridge (default: quello nel PATH)
#
# ENERGIA: usa ENERGY=1 solo per misure brevi e controllate (es. load mixed 5 minuti),
#          poi esegui: python3 energy_summary.py results/<LABEL>/*/energy.csv
#
# RISULTATI: results/<LABEL>/<test>_<scenario>_<data>/
#   results.jtl, report/index.html (report HTML), metadata.txt, jmeter.log, app.log, log dei comandi DB
# =====================================================================

set -o pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
DB_DIR="$HERE/db"
DATA="$HERE/data"
RESULTS="$HERE/results"

LABEL="${LABEL:-before}"
HEAP="${HEAP:-1g}"
RESET_DB="${RESET_DB:-1}"
DB_CONTAINER="${DB_CONTAINER:-oracle-db}"
DB_CONN="${DB_CONN:-system/system@//localhost:1521/FREEPDB1}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8082}"
SOAK_S="${SOAK_S:-3600}"
LOAD_REPS="${LOAD_REPS:-1}"
LOAD_USERS="${LOAD_USERS:-20}"
SOAK_USERS="${SOAK_USERS:-20}"
COOLDOWN_S="${COOLDOWN_S:-60}"
ENERGY_REPS="${ENERGY_REPS:-3}"
IDLE_S="${IDLE_S:-300}"
JMETER_BIN="${JMETER_BIN:-jmeter}"
ENERGY="${ENERGY:-0}"
ENERGIBRIDGE_BIN="${ENERGIBRIDGE_BIN:-$(command -v energibridge 2>/dev/null)}"
APP_PID=""
EB_PID=""

fail() { echo "ERRORE: $*" >&2; exit 1; }

stop_app() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    echo "[app] fermo l'applicazione (pid $APP_PID)"
    kill "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
}
trap stop_app EXIT

app_status() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://$HOST:$PORT/jobs"; }

wait_for_app() {
  echo "[app] attendo che l'applicazione risponda su $HOST:$PORT ..."
  for i in $(seq 1 90); do
    [ "$(app_status)" = "200" ] && { echo "[app] pronta"; return 0; }
    sleep 2
  done
  return 1
}

start_app() {
  local dir="$1"
  echo "[app] avvio $APP_JAR (heap $HEAP)"
  # shellcheck disable=SC2086
  java -Xms"$HEAP" -Xmx"$HEAP" -jar "$APP_JAR" --spring.jpa.show-sql=false --logging.level.root=WARN $APP_ARGS > "$dir/app.log" 2>&1 &
  APP_PID=$!
  wait_for_app || fail "l'app non risponde dopo 3 minuti (vedi $dir/app.log)"
}

sqlrun() { docker exec -i "$DB_CONTAINER" sqlplus -s "$DB_CONN" < "$1"; }

energy_start() {  # avvia EnergiBridge in parallelo: misura finche' non compare il file .done
  local dir="$1"
  [ -n "$ENERGIBRIDGE_BIN" ] || fail "energibridge non trovato: imposta ENERGIBRIDGE_BIN=/percorso/di/energibridge"
  sudo -v || fail "EnergiBridge richiede sudo"
  rm -f "$dir/.done"
  sudo "$ENERGIBRIDGE_BIN" -o "$dir/energy.csv" --summary \
    bash -c "while [ ! -f '$dir/.done' ]; do sleep 0.5; done" > "$dir/energy_summary.txt" 2>&1 &
  EB_PID=$!
  sleep 2
}

energy_stop() {
  local dir="$1"
  touch "$dir/.done"
  wait "$EB_PID" 2>/dev/null
  sudo chown "$(id -un)" "$dir/energy.csv" 2>/dev/null
  rm -f "$dir/.done"
  if [ -s "$dir/energy.csv" ]; then echo "[energia] $dir/energy.csv"; else echo "ATTENZIONE: energy.csv vuoto (vedi $dir/energy_summary.txt)"; fi
}

make_csv() {  # make_csv <prefisso> <intestazione> <file>
  { echo "$2"; grep "^$1," "$DATA/all.csv" | cut -d, -f2-; } > "$DATA/$3"
}

reset_db() {
  local dir="$1"
  echo "[db] clean + populate + export"
  mkdir -p "$DATA"
  sqlrun "$DB_DIR/clean_db.sql"          > "$dir/db_clean.log"    2>&1
  grep -q 'ORA-\|SP2-' "$dir/db_clean.log"    && fail "clean_db.sql: vedi $dir/db_clean.log"
  sqlrun "$DB_DIR/populate_db.sql"       > "$dir/db_populate.log" 2>&1
  grep -q 'ORA-\|SP2-' "$dir/db_populate.log" && fail "populate_db.sql: vedi $dir/db_populate.log"
  sqlrun "$DB_DIR/export_test_data.sql"  > "$DATA/all.csv"        2>"$dir/db_export.log"
  grep -q 'ORA-\|SP2-' "$DATA/all.csv"        && fail "export_test_data.sql: vedi $DATA/all.csv"
  make_csv S "email,password,userId,seekerId,resumeId" seekers.csv
  make_csv E "empEmail,empPassword,empUserId"          employers.csv
  make_csv J "jobId"                                   jobs.csv
  make_csv A "applicationId"                           applications.csv
  make_csv P "pUserId,pSeekerId,pResumeId,pJobId"      pairs.csv
  echo "[db] righe: seekers=$(($(wc -l < "$DATA/seekers.csv")-1)) jobs=$(($(wc -l < "$DATA/jobs.csv")-1)) pairs=$(($(wc -l < "$DATA/pairs.csv")-1))"
}

write_metadata() {
  local dir="$1" test="$2" scenario="$3"; shift 3
  {
    echo "data:        $(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "label:       $LABEL"
    echo "test:        $test"
    echo "scenario:    $scenario"
    echo "proprieta':  $*"
    echo "branch git:  $(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)"
    echo "commit git:  $(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null)  (modifiche non committate: $(git -C "$ROOT" status --porcelain 2>/dev/null | wc -l | tr -d ' ') file)"
    echo "sistema:     $(uname -srm)"
    echo "cpu:         $(sysctl -n machdep.cpu.brand_string 2>/dev/null || grep -m1 'model name' /proc/cpuinfo 2>/dev/null)"
    echo "core:        $(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null)"
    echo "java:        $(java -version 2>&1 | head -1)"
    echo "jmeter:      $($JMETER_BIN --version 2>&1 | grep -i 'apache jmeter\|^ *[0-9]\.[0-9]' | head -1 | tr -s ' ')"
    echo "app jar:     ${APP_JAR:-(avviata a mano)}   heap: $HEAP   args: $APP_ARGS"
    echo "reset db:    $RESET_DB"
    echo "energia:     ENERGY=$ENERGY  energibridge=${ENERGIBRIDGE_BIN:-(non trovato)}"
    echo "righe csv:   seekers=$(($(wc -l < "$DATA/seekers.csv" 2>/dev/null || echo 1)-1)) pairs=$(($(wc -l < "$DATA/pairs.csv" 2>/dev/null || echo 1)-1))"
  } > "$dir/metadata.txt"
}

summarize() {
  local jtl="$1"
  awk -F, 'NR>1 { n++; if ($8=="false") e++ } END { printf "[risultato] richieste=%d errori=%d (%.2f%%)\n", n, e+0, (n? 100*e/n : 0) }' "$jtl"
  echo "[risultato] errori per richiesta (prime 5):"
  awk -F, 'NR>1 && $8=="false" { c[$3]++ } END { for (k in c) print c[k], k }' "$jtl" | sort -rn | head -5
}

run_one() {  # run_one <test> <scenario> [-J...]
  local test="$1" scenario="$2"; shift 2
  local jmx="$HERE/${test}_test.jmx"
  [ -f "$jmx" ] || fail "file non trovato: $jmx"
  local dir="$RESULTS/$LABEL/${test}_${scenario}_$(date +%Y%m%d_%H%M%S)"
  mkdir -p "$dir"
  echo; echo "=== $test / $scenario  ($LABEL) -> $dir"

  [ "$RESET_DB" = "1" ] && reset_db "$dir"
  if [ -n "$APP_JAR" ]; then start_app "$dir"; else [ "$(app_status)" = "200" ] || fail "l'app non risponde su $HOST:$PORT/jobs"; fi

  write_metadata "$dir" "$test" "$scenario" "$@"
  [ "$ENERGY" = "1" ] && energy_start "$dir"
  "$JMETER_BIN" -n -t "$jmx" -l "$dir/results.jtl" -j "$dir/jmeter.log" -e -o "$dir/report" \
    -Jhost="$HOST" -Jport="$PORT" -Jdata_dir="$DATA" -Jscenario="$scenario" "$@"
  local rc=$?
  [ "$ENERGY" = "1" ] && energy_stop "$dir"

  [ -n "$APP_JAR" ] && stop_app
  [ $rc -eq 0 ] || echo "ATTENZIONE: jmeter ha restituito $rc (vedi $dir/jmeter.log)"
  [ -f "$dir/results.jtl" ] && summarize "$dir/results.jtl"
  echo "[report] $dir/report/index.html"
}

idle() {  # idle [secondi]: energia a riposo con l'app avviata e nessun carico
  local secs="${1:-300}"
  local dir="$RESULTS/$LABEL/idle_$(date +%Y%m%d_%H%M%S)"
  mkdir -p "$dir"
  echo; echo "=== idle ${secs}s  ($LABEL) -> $dir"
  if [ -n "$APP_JAR" ]; then start_app "$dir"; else [ "$(app_status)" = "200" ] || fail "l'app non risponde su $HOST:$PORT/jobs"; fi
  write_metadata "$dir" idle idle "secondi=$secs"
  energy_start "$dir"
  sleep "$secs"
  energy_stop "$dir"
  [ -n "$APP_JAR" ] && stop_app
}

baseline() {
  for sc in search login pdf register dashboard employer_dash seeker employer; do
    run_one load "$sc" -Jusers=1 -Jramp_s=1 -Jhold_s=30 -Jthink_ms=500 -Jthink_var_ms=0 \
      -Jlist_pct=100 -Japps_pct=100 -Japply_pct=100 -Jfav_pct=100 -Jpdf_pct=100 -Jreg_pct=100 -Jstatus_pct=100 -Jdash_pct=100
  done
}

cooldown() {
  if [ "$COOLDOWN_S" -gt 0 ]; then echo "[pausa] ${COOLDOWN_S}s per far raffreddare il Mac"; sleep "$COOLDOWN_S"; fi
}

energy_phase() {  # idle + run pesanti con EnergiBridge (stesso carico controllato per before e after)
  local i old="$ENERGY"
  idle "$IDLE_S"
  for i in $(seq 1 "$ENERGY_REPS"); do
    cooldown
    ENERGY=1
    run_one load mixed -Jusers=50 -Jramp_s=10 -Jhold_s=240 -Jthink_ms=200 -Jthink_var_ms=0 -Japply_pct=3 -Jfav_pct=3
    ENERGY="$old"
  done
}

stress_all() {
  local sc
  for sc in search login pdf dashboard employer_dash; do
    run_one stress "$sc"
    cooldown
  done
}

load_spike_soak() {
  local r sc
  for r in $(seq 1 "$LOAD_REPS"); do
    for sc in search login pdf; do run_one load "$sc" -Jusers="$LOAD_USERS"; cooldown; done
    run_one load mixed -Jusers="$LOAD_USERS" -Jreg_pct=3; cooldown
    run_one load register -Jusers=5 -Jhold_s=120; cooldown
  done
  run_one spike seeker -Jdash_pct=0 -Japps_pct=0 -Jfav_pct=0 -Jpdf_pct=0 -Japply_pct=10 -Jreg_pct=5
  cooldown
  run_one soak mixed -Jusers="$SOAK_USERS" -Jduration_s="$SOAK_S" -Japply_pct=2 -Jfav_pct=2
}

suite() {
  stress_all
  load_spike_soak
}

case "$1" in
  baseline) baseline ;;
  run)      shift; [ $# -ge 2 ] || fail "uso: $0 run <test> <scenario> [-Jk=v ...]"; run_one "$@" ;;
  suite)    suite ;;
  energy)          energy_phase ;;
  stress_all)      stress_all ;;
  load_spike_soak) load_spike_soak ;;
  idle)     shift; idle "$@" ;;
  *)        awk 'NR>1 && /^set -o pipefail/{exit} NR>1{print}' "$0"; exit 1 ;;
esac
