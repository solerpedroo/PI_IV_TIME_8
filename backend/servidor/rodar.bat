@echo off
rem uso: rodar.bat [porta] [--memoria]
cd /d "%~dp0"
if "%MONGODB_URI%"=="" set "MONGODB_URI=mongodb://127.0.0.1:27017/agrogestao?serverSelectionTimeoutMS=2000"
java -cp "bin;lib/*" Servidor %*
