@echo off
rem Sobe AgroGestão com MongoDB Atlas (carrega .env, compila se preciso).
rem Uso: emular.bat [--build] [porta]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\emular.ps1" %*