#!/bin/sh
# Compila o ServerSocket e executa a suíte JUnit 5 (sem socket na 3000).
set -eu
cd "$(dirname "$0")"

if command -v mvn >/dev/null 2>&1; then
  exec mvn -q test
fi

JUNIT_VER=1.11.4
# SHA-256 de org.junit.platform:junit-platform-console-standalone:1.11.4 (Maven Central)
JUNIT_SHA256=b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/agrogestao"
JAR="$CACHE/junit-platform-console-standalone-${JUNIT_VER}.jar"

junit_sha_ok () {
  command -v sha256sum >/dev/null 2>&1 || return 0
  [ "$(sha256sum "$JAR" | awk '{print $1}')" = "$JUNIT_SHA256" ]
}

if [ ! -f "$JAR" ] || ! junit_sha_ok; then
  mkdir -p "$CACHE"
  if ! command -v curl >/dev/null 2>&1; then
    echo "mvn não encontrado e curl ausente; instale Maven ou curl." >&2
    exit 1
  fi
  curl -fsSL -o "$JAR" \
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VER}/junit-platform-console-standalone-${JUNIT_VER}.jar"
  if ! junit_sha_ok; then
    echo "checksum do JUnit standalone não confere; remova $JAR e tente de novo." >&2
    rm -f "$JAR"
    exit 1
  fi
fi

rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -cp "$JAR" -d bin $(find src -name '*.java' ! -path 'src/test/*')
javac --release 21 -encoding UTF-8 -cp "bin:$JAR" -d bin src/test/java/*.java
exec java -jar "$JAR" execute --class-path bin --scan-classpath
