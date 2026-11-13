#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf bin && mkdir bin
find src -name '*.java' ! -path 'src/test/*' > bin/fontes.txt
javac --release 21 -encoding UTF-8 -d bin @bin/fontes.txt
