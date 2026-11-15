@echo off
rem Autor principal: Pedro Henrique Contardi Soler - RA: 25005592
rem uso: rodar.bat [porta] [--memoria]
cd /d "%~dp0"
echo %* | findstr /i /c:"--memoria" >nul
if errorlevel 1 (
  if "%MONGODB_URI%"=="" (
    echo Defina MONGODB_URI ^(MongoDB Atlas^). Veja docs\mongodb-atlas.md
    exit /b 1
  )
)
java -cp "bin;lib/*" Servidor %*