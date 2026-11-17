#!/bin/sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"
./scripts/java8-mvn.sh test
java8_home=$(/usr/libexec/java_home -v 1.8)
"$java8_home/bin/javac" -cp 'target/classes:SweetHome3D/lib/*' -d target/test-classes verification/InternshipRegressionChecks.java
"$java8_home/bin/java" -Djava.awt.headless=true -cp 'target/test-classes:target/classes:SweetHome3D/lib/*' InternshipRegressionChecks
python3 test_submission.py
