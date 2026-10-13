@echo off
cd /d "%~dp0"
if not exist bin\Gateway.class (
    echo Gateway nao compilado. Rode compilar.bat primeiro.
    exit /b 1
)
java -cp bin Gateway
