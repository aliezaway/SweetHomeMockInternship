#!/bin/sh
# Keep the system default Java version unchanged; use Java 8 for this project.
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
java8_home=$(/usr/libexec/java_home -v 1.8 2>/dev/null) || {
  echo 'Java 8 is required. Install Azul Zulu 8 (aarch64 on Apple Silicon).' >&2
  exit 1
}
cd "$project_root"
exec env JAVA_HOME="$java8_home" mvn "$@"
