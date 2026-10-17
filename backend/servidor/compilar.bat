@echo off
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac -encoding UTF-8 -cp "lib/*" -d bin src\*.java src\http\*.java src\handlers\*.java src\dados\*.java src\seguranca\*.java src\util\*.java
