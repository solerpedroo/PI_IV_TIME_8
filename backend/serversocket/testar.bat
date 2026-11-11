@echo off
cd /d "%~dp0"
where mvn >nul 2>nul && (mvn -q test && exit /b 0)

set JUNIT_VER=1.11.4
set JUNIT_SHA256=b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc
set CACHE=%LOCALAPPDATA%\agrogestao
set JAR=%CACHE%\junit-platform-console-standalone-%JUNIT_VER%.jar

if not exist "%JAR%" goto download
for /f "tokens=1" %%h in ('certutil -hashfile "%JAR%" SHA256 ^| findstr /r "^[0-9a-f]"') do set "HASH=%%h"
if /i not "%HASH%"=="%JUNIT_SHA256%" goto download
goto compile

:download
if not exist "%CACHE%" mkdir "%CACHE%"
curl -fsSL -o "%JAR%" "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/%JUNIT_VER%/junit-platform-console-standalone-%JUNIT_VER%.jar"
if errorlevel 1 exit /b 1
for /f "tokens=1" %%h in ('certutil -hashfile "%JAR%" SHA256 ^| findstr /r "^[0-9a-f]"') do set "HASH=%%h"
if /i not "%HASH%"=="%JUNIT_SHA256%" (
  echo checksum do JUnit standalone nao confere
  del "%JAR%"
  exit /b 1
)

:compile
if exist bin rmdir /s /q bin
mkdir bin
javac --release 21 -encoding UTF-8 -cp "%JAR%" -d bin src\*.java src\protocolo\*.java src\protocolo\mensagens\*.java src\algoritmos\*.java
javac --release 21 -encoding UTF-8 -cp "bin;%JAR%" -d bin src\test\java\*.java
java -jar "%JAR%" execute --class-path bin --scan-classpath
