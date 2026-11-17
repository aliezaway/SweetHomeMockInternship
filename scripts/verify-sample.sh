#!/bin/sh
# Uses the application PDF printer; requires a graphical desktop session.
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
./scripts/java8-mvn.sh compile -q
java8_home=$(/usr/libexec/java_home -v 1.8)
mkdir -p target/test-classes
"$java8_home/bin/javac" -cp 'target/classes:SweetHome3D/lib/*' -d target/test-classes verification/SamplePrintCheck.java
"$java8_home/bin/java" -cp 'target/test-classes:target/classes:SweetHome3D/lib/*' SamplePrintCheck
