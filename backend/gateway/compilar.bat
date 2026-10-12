@echo off
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac -encoding UTF-8 -d bin src\*.java src\handlers\*.java src\util\*.java
