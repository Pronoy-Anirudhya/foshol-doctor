#!/usr/bin/env bash
# Interactive terminal client for every Foshol Doctor HTTP API.
# Press the number shown, Enter. Tokens and IDs are remembered in tools/.run/api-session.json.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION="${SESSION_FILE:-$ROOT/tools/.run/api-session.json}"
DEMO_IMG="$ROOT/docs/demo/images"
DEMO_AUD="$ROOT/docs/demo/audio"

CROP_RICE="01800000-0000-7000-8000-000000000001"
DISEASE_BLAST="01800000-0000-7000-8000-000000000103"
REMEDY_BLAST="01800000-0000-7000-8000-000000000507"
SYMPTOM_BROWN="01800000-0000-7000-8000-000000000301"
PHONE="+8801711111111"
OTP="123456"

mkdir -p "$ROOT/tools/.run"

if [[ ! -f "$SESSION" ]]; then
  cat >"$SESSION" <<EOF
{
  "farmer_token": "",
  "officer_token": "",
  "admin_token": "",
  "active_token": "",
  "active_role": "",
  "case_id": "",
  "image_id": "",
  "audio_id": "",
  "task_id": "",
  "advisory_id": "",
  "crop_id": "$CROP_RICE",
  "disease_id": "$DISEASE_BLAST",
  "remedy_id": "$REMEDY_BLAST",
  "symptom_id": "$SYMPTOM_BROWN"
}
EOF
fi

state() {
  python3 - "$SESSION" "$1" <<'PY'
import json, sys
path, key = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    data = json.load(fh)
print(data.get(key) or "")
PY
}

set_state() {
  python3 - "$SESSION" "$1" "$2" <<'PY'
import json, sys
path, key, value = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, encoding="utf-8") as fh:
    data = json.load(fh)
data[key] = value
with open(path, "w", encoding="utf-8") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")
PY
}

pretty() {
  python3 -c 'import json,sys; raw=sys.stdin.read();
print(raw if not raw.strip() else json.dumps(json.loads(raw), indent=2, ensure_ascii=False))' 2>/dev/null || cat
}

harvest() {
  python3 - "$SESSION" "${1:-/tmp/foshol-api-body}" <<'PY'
import json, sys
path, body_path = sys.argv[1], sys.argv[2]
with open(body_path, encoding="utf-8") as fh:
    raw = fh.read()
if not raw.strip():
    raise SystemExit(0)
body = json.loads(raw)
with open(path, encoding="utf-8") as fh:
    state = json.load(fh)

def put(key, value):
    if value:
        state[key] = str(value)

if isinstance(body, dict):
    token = body.get("token")
    role = ((body.get("principal") or {}).get("role") or "").upper()
    if token:
        put("active_token", token)
        put("active_role", role)
        if role == "FARMER":
            put("farmer_token", token)
        elif role == "OFFICER":
            put("officer_token", token)
        elif role == "ADMIN":
            put("admin_token", token)
        print(f"session: stored {role or 'login'} bearer ({len(str(token))} chars)", file=sys.stderr)
    put("case_id", body.get("caseId"))
    put("advisory_id", body.get("advisoryId"))
    put("task_id", body.get("taskId") or body.get("reviewTaskId"))
    images = body.get("images") or []
    if images and isinstance(images, list) and isinstance(images[0], dict):
        put("image_id", images[0].get("imageId") or images[0].get("id"))
    audio = body.get("audio") or {}
    if isinstance(audio, dict):
        put("audio_id", audio.get("audioId") or audio.get("id"))
    content = body.get("content")
    if isinstance(content, list) and content:
        row = content[0]
        if isinstance(row, dict):
            put("case_id", row.get("caseId") or state.get("case_id"))
            put("task_id", row.get("reviewTaskId") or row.get("taskId") or state.get("task_id"))
            put("advisory_id", row.get("advisoryId") or state.get("advisory_id"))
            put("image_id", row.get("imageId") or state.get("image_id"))
    task = body.get("task") or {}
    if isinstance(task, dict):
        put("task_id", task.get("taskId") or task.get("reviewTaskId"))
        put("case_id", task.get("caseId") or state.get("case_id"))
    case = body.get("case") or {}
    if isinstance(case, dict):
        put("case_id", case.get("caseId"))
        images = case.get("images") or []
        if images and isinstance(images[0], dict):
            put("image_id", images[0].get("imageId"))

with open(path, "w", encoding="utf-8") as fh:
    json.dump(state, fh, indent=2)
    fh.write("\n")
print(raw)
PY
}

need_id() {
  local key="$1" label="$2"
  local value
  value="$(state "$key")"
  if [[ -z "$value" ]]; then
    echo "missing $label — call the matching list/submit API first (stored in $SESSION)" >&2
    return 1
  fi
  printf '%s' "$value"
}

auth_header() {
  local role="$1"
  local token=""
  local used="$role"
  case "$role" in
    farmer) token="$(state farmer_token)" ;;
    officer) token="$(state officer_token)" ;;
    admin) token="$(state admin_token)" ;;
    auto|none) token="" ;;
    *) token="" ;;
  esac
  if [[ "$role" != "none" && -z "$token" ]]; then
    token="$(state active_token)"
    used="$(state active_role)"
    used="${used:-active}"
  fi
  if [[ "$role" != "none" && -z "$token" ]]; then
    token="$(state farmer_token)"
    used="farmer"
  fi
  if [[ "$role" != "none" && -z "$token" ]]; then
    token="$(state officer_token)"
    used="officer"
  fi
  if [[ "$role" != "none" && -z "$token" ]]; then
    token="$(state admin_token)"
    used="admin"
  fi
  if [[ "$role" != "none" && -z "$token" ]]; then
    echo "no bearer token in session — run 2 (farmer OTP), 3 (officer) or 4 (admin) first" >&2
    return 1
  fi
  if [[ -n "$token" ]]; then
    echo "  bearer ${used} (${#token} chars)" >&2
    printf 'Authorization: Bearer %s' "$token"
  fi
}

invoke() {
  local method="$1" path="$2" role="$3"
  shift 3
  local body="" extra_args=()
  if [[ $# -gt 0 && "${1:-}" != -* ]]; then
    body="$1"
    shift
  fi
  if (($# > 0)); then
    extra_args=("$@")
  fi
  local url="${BASE_URL}${path}"
  local header
  header="$(auth_header "$role")" || return 1
  echo
  echo "→ ${method} ${url}  [${role}]"
  local args=(-sS -D /tmp/foshol-api-headers -o /tmp/foshol-api-body -w '\nHTTP %{http_code}\n' -X "$method")
  if [[ -n "$header" ]]; then
    args+=(-H "$header")
  fi
  if [[ -n "$body" ]]; then
    echo "request body:"
    echo "$body" | pretty
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  if ((${#extra_args[@]} > 0)); then
    args+=("${extra_args[@]}")
  fi
  args+=("$url")
  curl "${args[@]}" || true
  echo "response headers (selected):"
  grep -Ei '^(HTTP/|content-type:|location:|idempotency-replayed:|x-correlation-id:)' /tmp/foshol-api-headers || true
  echo "response body:"
  if python3 -c 'import json,sys; json.load(open("/tmp/foshol-api-body"))' 2>/dev/null; then
    harvest /tmp/foshol-api-body | pretty
  else
    cat /tmp/foshol-api-body
    echo
  fi
}

invoke_multipart() {
  local role="$1" crop="$2" image="$3" audio="${4:-}"
  local url="${BASE_URL}/api/v1/cases"
  local header key
  header="$(auth_header "$role")" || return 1
  key="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  echo
  echo "→ POST ${url}  [${role}] multipart cropId=${crop}"
  echo "  image ${image}"
  [[ -n "$audio" ]] && echo "  audio ${audio}"
  local args=(-sS -D /tmp/foshol-api-headers -o /tmp/foshol-api-body -w '\nHTTP %{http_code}\n'
    -H "$header" -H "Idempotency-Key: ${key}"
    -F "cropId=${crop}"
    -F "fieldArea=2"
    -F "fieldAreaUnit=DECIMAL"
    -F "noteBn=ডেমো কেস টার্মিনাল থেকে"
    -F "images=@${image};type=image/jpeg")
  if [[ -n "$audio" ]]; then
    args+=(-F "audio=@${audio};type=audio/wav" -F "audioDurationMs=1200")
  fi
  args+=("$url")
  curl "${args[@]}"
  echo "response headers (selected):"
  grep -Ei '^(HTTP/|content-type:|location:|idempotency-replayed:)' /tmp/foshol-api-headers || true
  echo "response body:"
  harvest /tmp/foshol-api-body | pretty
}

print_menu() {
  cat <<'MENU'

  Foshol Doctor API  (q quit, s session — stays open after each result)

  AUTH
   1  POST /api/v1/auth/otp/request
   2  POST /api/v1/auth/otp/verify          → farmer token
   3  POST /api/v1/auth/officer/login       → officer token
   4  POST /api/v1/auth/officer/login       → admin token
   5  GET  /api/v1/me                       [farmer]
   6  GET  /api/v1/me                       [officer]

  KNOWLEDGE  (any saved login token)
   7  GET  /api/v1/crops
   8  GET  /api/v1/crops/{cropId}/diseases
   9  GET  /api/v1/diseases/{diseaseId}
  10  GET  /api/v1/diseases/{diseaseId}/remedies
  11  GET  /api/v1/symptoms

  CASES (farmer)
  12  POST /api/v1/cases                    PRIMARY rice blast photo
  13  POST /api/v1/cases                    SECONDARY photo + audio
  14  POST /api/v1/cases                    blurry photo (expect 422)
  15  GET  /api/v1/cases
  16  GET  /api/v1/cases/{caseId}
  17  GET  /api/v1/cases/{caseId}/images/{imageId}/content
  18  GET  /api/v1/cases/{caseId}/images/{imageId}/url
  19  GET  /api/v1/cases/{caseId}/audio/content
  20  GET  /api/v1/cases/{caseId}/audio/url

  ANALYSIS
  21  GET  /api/v1/cases/{caseId}/analysis
  22  GET  /api/v1/cases/{caseId}/gradcam

  REVIEW (officer)
  23  GET  /api/v1/review/queue
  24  GET  /api/v1/review/tasks/{taskId}
  25  POST /api/v1/review/tasks/{taskId}/claim
  26  POST /api/v1/review/tasks/{taskId}/release
  27  POST /api/v1/review/tasks/{taskId}/symptoms
  28  POST /api/v1/review/tasks/{taskId}/approve
  29  POST /api/v1/review/tasks/{taskId}/reject
  30  GET  /api/v1/cases/{caseId}/advisory
  31  GET  /api/v1/cases/{caseId}/advisories
  32  POST /api/v1/advisories/{advisoryId}/revise

  ADMIN / STREAM / NOTIFY
  33  GET  /api/v1/admin/stats              [admin]
  34  GET  /api/v1/notifications            [farmer]
  35  GET  /api/v1/stream                   [farmer, 8s sample]
  36  GET  /actuator/health                 (no auth)

MENU
}

run_choice() {
  local n="$1"
  local crop disease remedy symptom case_id image_id task_id advisory_id
  crop="$(state crop_id)"; crop="${crop:-$CROP_RICE}"
  disease="$(state disease_id)"; disease="${disease:-$DISEASE_BLAST}"
  remedy="$(state remedy_id)"; remedy="${remedy:-$REMEDY_BLAST}"
  symptom="$(state symptom_id)"; symptom="${symptom:-$SYMPTOM_BROWN}"
  case "$n" in
    1) invoke POST /api/v1/auth/otp/request none "{\"phone\":\"${PHONE}\"}" ;;
    2) invoke POST /api/v1/auth/otp/verify none "{\"phone\":\"${PHONE}\",\"code\":\"${OTP}\"}" ;;
    3) invoke POST /api/v1/auth/officer/login none '{"username":"officer","password":"password"}' ;;
    4) invoke POST /api/v1/auth/officer/login none '{"username":"admin","password":"password"}' ;;
    5) invoke GET /api/v1/me farmer ;;
    6) invoke GET /api/v1/me officer ;;
    7) invoke GET /api/v1/crops auto ;;
    8) invoke GET "/api/v1/crops/${crop}/diseases" auto ;;
    9) invoke GET "/api/v1/diseases/${disease}" auto ;;
    10) invoke GET "/api/v1/diseases/${disease}/remedies" auto ;;
    11) invoke GET /api/v1/symptoms auto ;;
    12) invoke_multipart farmer "$crop" "$DEMO_IMG/01-rice-blast-primary.jpg" ;;
    13) invoke_multipart farmer "$crop" "$DEMO_IMG/02-rice-brown-spot-ambiguous.jpg" "$DEMO_AUD/secondary-brown-spot.wav" ;;
    14) invoke_multipart farmer "$crop" "$DEMO_IMG/07-blurry-reject.jpg" ;;
    15) invoke GET "/api/v1/cases?page=0&size=20" farmer ;;
    16)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}" farmer
      ;;
    17)
      case_id="$(need_id case_id caseId)" || return 0
      image_id="$(need_id image_id imageId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/images/${image_id}/content?variant=DERIVATIVE" farmer
      ;;
    18)
      case_id="$(need_id case_id caseId)" || return 0
      image_id="$(need_id image_id imageId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/images/${image_id}/url?variant=original" farmer
      ;;
    19)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/audio/content" farmer
      ;;
    20)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/audio/url" farmer
      ;;
    21)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/analysis" farmer
      ;;
    22)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/gradcam" farmer
      ;;
    23) invoke GET "/api/v1/review/queue?page=0&size=20" officer ;;
    24)
      task_id="$(need_id task_id taskId)" || return 0
      invoke GET "/api/v1/review/tasks/${task_id}" officer
      ;;
    25)
      task_id="$(need_id task_id taskId)" || return 0
      invoke POST "/api/v1/review/tasks/${task_id}/claim" officer
      ;;
    26)
      task_id="$(need_id task_id taskId)" || return 0
      invoke POST "/api/v1/review/tasks/${task_id}/release" officer
      ;;
    27)
      task_id="$(need_id task_id taskId)" || return 0
      invoke POST "/api/v1/review/tasks/${task_id}/symptoms" officer "{\"symptomIds\":[\"${symptom}\"]}"
      ;;
    28)
      task_id="$(need_id task_id taskId)" || return 0
      invoke POST "/api/v1/review/tasks/${task_id}/approve" officer \
        "{\"diseaseId\":\"${disease}\",\"remedyIds\":[\"${remedy}\"],\"officerNoteBn\":\"ডেমো অনুমোদন — মাঠ কর্মকর্তা থেকে\"}"
      ;;
    29)
      task_id="$(need_id task_id taskId)" || return 0
      invoke POST "/api/v1/review/tasks/${task_id}/reject" officer \
        '{"reasonCode":"INSUFFICIENT_DETAIL","messageBn":"ডেমো প্রত্যাখ্যান — আরও স্পষ্ট ছবি দিন"}'
      ;;
    30)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/advisory" farmer
      ;;
    31)
      case_id="$(need_id case_id caseId)" || return 0
      invoke GET "/api/v1/cases/${case_id}/advisories" farmer
      ;;
    32)
      advisory_id="$(need_id advisory_id advisoryId)" || return 0
      invoke POST "/api/v1/advisories/${advisory_id}/revise" officer \
        "{\"diseaseId\":\"${disease}\",\"remedyIds\":[\"${remedy}\"],\"officerNoteBn\":\"ডেমো সংশোধিত পরামর্শ\"}"
      ;;
    33) invoke GET /api/v1/admin/stats admin ;;
    34) invoke GET "/api/v1/notifications?page=0&size=20" farmer ;;
    35)
      header="$(auth_header farmer)" || return 0
      echo "→ GET ${BASE_URL}/api/v1/stream  [farmer, 8 seconds]"
      curl -sS -N --max-time 8 -H "$header" -H 'Accept: text/event-stream' "${BASE_URL}/api/v1/stream" || true
      echo
      ;;
    36) invoke GET /actuator/health none ;;
    s|S)
      echo
      python3 -m json.tool "$SESSION"
      ;;
    q|Q) echo "bye"; exit 0 ;;
    *) echo "unknown choice: $n" ;;
  esac
}

if ! curl -sf "${BASE_URL}/actuator/health" >/dev/null; then
  echo "API is not up at ${BASE_URL}. Start it with: ${ROOT}/tools/start-stack.sh" >&2
  exit 1
fi

pause_after_result() {
  echo
  echo "Result is on screen. The client stays open."
  while true; do
    read -r -p "Enter = menu,  q = quit> " next
    case "${next:-}" in
      q|Q)
        echo "bye"
        exit 0
        ;;
      "")
        return 0
        ;;
      *)
        echo "Press Enter to go back to the menu, or q to quit."
        ;;
    esac
  done
}

echo "API ${BASE_URL}  session ${SESSION}"
echo "Suggested order: 1 → 2 → 3 → 4 → 7 → 12 → 16 → 21 → 23 → 25 → 28 → 30"
echo "This client stays open after each call. Quit only with q."
set +e
print_menu
while true; do
  read -r -p "API number> " choice || {
    echo
    echo "stdin closed; type q from a real terminal to quit."
    continue
  }
  [[ -z "${choice:-}" ]] && continue
  case "$choice" in
    q|Q)
      echo "bye"
      exit 0
      ;;
  esac
  run_choice "$choice" || true
  pause_after_result
  print_menu
done
