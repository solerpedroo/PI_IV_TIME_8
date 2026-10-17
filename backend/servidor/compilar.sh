#!/bin/sh
cd "$(dirname "$0")"
rm -rf bin && mkdir bin
javac -encoding UTF-8 -cp "lib/*" -d bin $(find src -name "*.java")
