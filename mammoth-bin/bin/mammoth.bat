@echo off
setlocal
set "MAMMOTH_HOME=%~dp0.."
set "DIRNAME=%~dp0"

if "%JAVA_HOME%"=="" (
    echo Error: JAVA_HOME is not set
    exit /b 1
)

set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
set "MAMMOTH_CP=%MAMMOTH_HOME%\lib\*"

"%JAVA_CMD%" -cp "%MAMMOTH_CP%" -Dmammoth.prog=mammoth org.mammoth.cli.MammothCLI mammoth %*
