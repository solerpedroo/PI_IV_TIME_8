@echo off
rem Sobe o ServerSocket de algoritmos em segundo plano (mesmo papel do .sh).
setlocal
set "PORTA=%~1"
if "%PORTA%"=="" set "PORTA=3000"
set "DIR=%~dp0..\serversocket"

pushd "%DIR%"
call compilar.bat
if errorlevel 1 exit /b 1
start "AgroGestão-Algoritmos" /B java -cp bin Servidor %PORTA%
popd

timeout /t 1 /nobreak >nul
set "ALG_PORT=%PORTA%"
endlocal & set "SERVIDOR_ALGORITMOS_HOST=127.0.0.1" & set "SERVIDOR_ALGORITMOS_PORT=%ALG_PORT%"
exit /b 0