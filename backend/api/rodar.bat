@echo off
rem Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
rem uso: rodar.bat [porta] [--memoria]  — preferir emular.bat na raiz
cd /d "%~dp0"
echo %* | findstr /i /c:"--memoria" >nul
set USA_MEMORIA=%ERRORLEVEL%

if %USA_MEMORIA% neq 0 (
  if "%MONGODB_URI%"=="" (
    if exist "..\..\.env" (
      powershell -NoProfile -ExecutionPolicy Bypass -File "..\..\scripts\carregar-env.ps1" "..\..\.env"
    )
  )
  if "%MONGODB_URI%"=="" (
    echo Defina MONGODB_URI ^(MongoDB Atlas^) em .env na raiz.
    echo Use emular.bat na raiz do repositorio.
    echo Guia: docs\mongodb-atlas.md
    exit /b 1
  )
)

if "%SERVIDOR_ALGORITMOS_DISABLED%"=="" (
  call "%~dp0iniciar-algoritmos.bat" 3000
)

java -cp "bin;lib/*" Servidor %*