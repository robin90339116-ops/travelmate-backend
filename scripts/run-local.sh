#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
if [ ! -f .env.local ]; then
  echo 'Missing .env.local. Add private provider configuration first.' >&2
  exit 1
fi
if [ ! -f target/travelmate-backend-1.0.0.jar ]; then
  echo 'Build the backend with ./mvnw verify first.' >&2
  exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then java_bin="$JAVA_HOME/bin/java"; else java_bin=java; fi
# Maven replaces target/*.jar during packaging. Never run from that mutable path.
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/travelmate-runtime.XXXXXX")
cp target/travelmate-backend-1.0.0.jar "$runtime_dir/backend.jar"
child_pid=
cleanup() {
  if [ -n "$child_pid" ]; then kill "$child_pid" 2>/dev/null || true; wait "$child_pid" 2>/dev/null || true; fi
  rm -f "$runtime_dir/backend.jar"
  rmdir "$runtime_dir" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$java_bin" -jar "$runtime_dir/backend.jar" \
  "--spring.config.additional-location=file:$project_dir/.env.local[.properties]" \
  --spring.profiles.active=local --server.address=127.0.0.1 "$@" &
child_pid=$!
wait "$child_pid"
