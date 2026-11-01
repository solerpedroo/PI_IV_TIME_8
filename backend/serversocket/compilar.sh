#!/bin/sh
cd "$(dirname "$0")"
rm -rf bin && mkdir bin
javac --release 21 -encoding UTF-8 -d bin $(find src -name "*.java")
