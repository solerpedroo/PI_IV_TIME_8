@echo off
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac --release 21 -encoding UTF-8 -d bin src\*.java src\handlers\*.java src\util\*.java
