#!/usr/bin/env bash
# =====================================================================
# misure_reali.sh  -  sessioni di misura "vere" (before / after)
#
# USO (dalla cartella jmeter/):
#   ./misure_reali.sh <before|after> stress     5 stress (uno per funzione)            ~50 min
#   ./misure_reali.sh <before|after> perf       load, spike e soak                     ~85 min
#   ./misure_reali.sh <before|after> energy     riposo + 3 misure di energia           ~30 min
#
# Prima di ogni sessione: Mac in carica, modalita' a basso consumo spenta, schermo acceso,
# altre app e notifiche chiuse, computer non usato. Le tre sessioni vanno fatte uguali
# su before e after (stessi parametri qui sotto!).
#
# Dopo la sessione "stress" decidi gli utenti per load e soak (circa il 60% del carico
# massimo sostenuto) e modifica i valori LOAD_USERS / SOAK_USERS qui sotto: poi NON cambiarli
# piu' tra before e after.
# =====================================================================

# ---- PARAMETRI: devono restare identici tra before e after --------------------
LOAD_USERS="${LOAD_USERS:-20}"          # utenti dei load (dopo lo stress: ~60% della capacita')
LOAD_HOLD_S="${LOAD_HOLD_S:-300}"       # durata a regime di ogni load (secondi)
LOAD_MIXED_REPS="${LOAD_MIXED_REPS:-3}" # ripetizioni del load misto
STRESS_STEP_S="${STRESS_STEP_S:-90}"    # durata di ogni gradino dello stress (secondi)
STRESS_U="${STRESS_U:-10 10 10 20 20}"  # utenti aggiunti a ogni gradino (cumulativi: 10,20,30,50,70)
SPIKE_BASE="${SPIKE_BASE:-10}"          # utenti di base dello spike
SPIKE_USERS="${SPIKE_USERS:-100}"       # utenti che entrano di colpo nel picco
SOAK_USERS="${SOAK_USERS:-20}"          # utenti del soak (~60% della capacita')
SOAK_S="${SOAK_S:-1200}"                # durata del soak in secondi
COOLDOWN_S="${COOLDOWN_S:-120}"         # pausa tra una run e l'altra (secondi)
IDLE_S="${IDLE_S:-300}"                 # durata della misura a riposo
ENERGY_USERS="${ENERGY_USERS:-50}"      # utenti della misura di energia
ENERGY_RUNS="${ENERGY_RUNS:-3}"         # ripetizioni della misura di energia
# ------------------------------------------------------------------------------

set -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE" || exit 1

LABEL="$1"
SESSION="$2"
case "$LABEL" in before|after) ;; *) awk 'NR>1 && /^# ---- PARAMETRI/{exit} NR>1{print}' "$0"; exit 1 ;; esac
case "$SESSION" in stress|perf|energy) ;; *) awk 'NR>1 && /^# ---- PARAMETRI/{exit} NR>1{print}' "$0"; exit 1 ;; esac

export LABEL SOAK_S COOLDOWN_S
export APP_JAR="${APP_JAR:-../target/RevHire_WebApplication-1.0.0-exec.jar}"
export APP_ARGS="${APP_ARGS:---spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/FREEPDB1}"
DB_CONTAINER="${DB_CONTAINER:-oracle-db}"

preflight() {
  local ok=1 n
  command -v jmeter >/dev/null 2>&1 || { echo "ERRORE: jmeter non trovato"; ok=0; }
  command -v java   >/dev/null 2>&1 || { echo "ERRORE: java non trovato"; ok=0; }
  [ -f "$APP_JAR" ] || { echo "ERRORE: jar non trovato: $APP_JAR (esegui: mvn clean package -DskipTests)"; ok=0; }
  docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$DB_CONTAINER" || { echo "ERRORE: il container $DB_CONTAINER non e' attivo"; ok=0; }
  if [ -n "$(lsof -iTCP:8082 -sTCP:LISTEN -t 2>/dev/null)" ]; then
    echo "ERRORE: la porta 8082 e' occupata: ferma l'app avviata a mano"; ok=0
  fi
  if [ "$SESSION" = "energy" ]; then
    [ -n "$ENERGIBRIDGE_BIN" ] || command -v energibridge >/dev/null 2>&1 || { echo "ERRORE: energibridge non trovato: esporta ENERGIBRIDGE_BIN"; ok=0; }
  fi
  if command -v pmset >/dev/null 2>&1; then
    pmset -g batt | grep -q "AC Power" || echo "ATTENZIONE: il Mac non e' collegato alla corrente"
    [ "$(pmset -g | awk '/lowpowermode/{print $2}')" = "1" ] && echo "ATTENZIONE: modalita' a basso consumo attiva"
  fi
  n=$(git -C .. status --porcelain -- . ':(exclude)jmeter/results' ':(exclude)jmeter/data' 2>/dev/null | wc -l | tr -d ' ')
  [ "${n:-0}" != "0" ] && echo "ATTENZIONE: $n file non committati: il commit registrato nei metadata non descrive tutto il codice"
  [ "$ok" = "1" ] || exit 1
}

R() { echo; echo ">>> ./run_jmeter.sh $*"; ./run_jmeter.sh "$@"; echo "[pausa] ${COOLDOWN_S}s per far raffreddare il Mac"; sleep "${COOLDOWN_S}"; }

[ "$SKIP_CHECKS" = "1" ] || preflight

mkdir -p "results/$LABEL"
LOG="results/$LABEL/sessione_${SESSION}_$(date +%Y%m%d_%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1

echo "Sessione: $SESSION   versione: $LABEL"
echo "Parametri: LOAD_USERS=$LOAD_USERS LOAD_HOLD_S=$LOAD_HOLD_S STRESS_STEP_S=$STRESS_STEP_S STRESS_U=\"$STRESS_U\" COOLDOWN_S=$COOLDOWN_S LOAD_MIXED_REPS=$LOAD_MIXED_REPS SPIKE_BASE=$SPIKE_BASE SPIKE_USERS=$SPIKE_USERS SOAK_USERS=$SOAK_USERS SOAK_S=$SOAK_S IDLE_S=$IDLE_S ENERGY_USERS=$ENERGY_USERS ENERGY_RUNS=$ENERGY_RUNS"
echo "Log: $LOG"
echo
echo "Controlla: Mac in carica, basso consumo spento, schermo acceso, altre app e notifiche chiuse,"
echo "           computer non usato fino alla fine."
if [ "$YES" != "1" ]; then read -r -p "Premi Invio per iniziare (Ctrl+C per annullare) " _; fi

# impedisce lo stop del Mac finche' lo script e' in esecuzione
command -v caffeinate >/dev/null 2>&1 && { caffeinate -i -w $$ & }

case "$SESSION" in
  stress)
    set -- $STRESS_U
    for sc in search login pdf dashboard employer_dash; do
      R run stress "$sc" -Jstep_s="$STRESS_STEP_S" -Ju1="$1" -Ju2="$2" -Ju3="$3" -Ju4="$4" -Ju5="$5"
    done
    echo
    echo "Fine stress. Nei report guarda 'Transactions per second' e 'Response times over time':"
    echo "il punto in cui il throughput smette di salire e' la capacita'. Poi imposta LOAD_USERS e SOAK_USERS"
    echo "(circa il 60% della capacita') in cima a questo script, e mandami i risultati."
    ;;
  perf)
    for sc in search login pdf; do R run load "$sc" -Jusers="$LOAD_USERS" -Jhold_s="$LOAD_HOLD_S"; done
    R run load register -Jusers=5 -Jhold_s=120
    i=1
    while [ "$i" -le "$LOAD_MIXED_REPS" ]; do
      R run load mixed -Jusers="$LOAD_USERS" -Jhold_s="$LOAD_HOLD_S" -Jreg_pct=3
      i=$((i+1))
    done
    R run spike seeker -Jbase_users="$SPIKE_BASE" -Jspike_users="$SPIKE_USERS" \
      -Jdash_pct=0 -Japps_pct=0 -Jfav_pct=0 -Jpdf_pct=0 -Japply_pct=10 -Jreg_pct=5
    R run soak mixed -Jusers="$SOAK_USERS" -Jduration_s="$SOAK_S" -Japply_pct=2 -Jfav_pct=2
    ;;
  energy)
    R idle "$IDLE_S"
    i=1
    while [ "$i" -le "$ENERGY_RUNS" ]; do
      echo; echo ">>> ENERGY=1 ./run_jmeter.sh run load mixed (energia, prova $i di $ENERGY_RUNS)"
      ENERGY=1 ./run_jmeter.sh run load mixed -Jusers="$ENERGY_USERS" -Jramp_s=10 -Jhold_s=240 \
        -Jthink_ms=200 -Jthink_var_ms=0 -Japply_pct=3 -Jfav_pct=3
      i=$((i+1))
    done
    echo
    echo "=== Riepilogo energia ($LABEL)"
    python3 energy_summary.py results/"$LABEL"/idle_*/energy.csv results/"$LABEL"/load_mixed_*/energy.csv
    ;;
esac

echo
echo "Sessione $SESSION ($LABEL) terminata. Risultati in results/$LABEL/"
