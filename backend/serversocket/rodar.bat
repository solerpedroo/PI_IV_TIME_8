@echo off
rem uso: rodar.bat [porta]
cd /d "%~dp0"
java -cp bin Servidor %*
