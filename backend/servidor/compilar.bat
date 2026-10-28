@echo off
rem Miguel — RA: 25003110
rem Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac --release 21 -encoding UTF-8 -cp "lib/*" -d bin src\*.java src\http\*.java src\handlers\*.java src\dados\*.java src\seguranca\*.java src\servicos\*.java src\util\*.java
