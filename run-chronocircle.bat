@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo JDK 17 or later is required. Add javac to PATH and try again.
    pause
    exit /b 1
)

where javaw >nul 2>nul
if errorlevel 1 (
    echo Java 17 or later is required. Add javaw to PATH and try again.
    pause
    exit /b 1
)

if not exist "%~dp0out" mkdir "%~dp0out"
javac -encoding UTF-8 -d "%~dp0out" "%~dp0ChronoCircleApp.java"
if errorlevel 1 (
    echo ChronoCircle could not be compiled.
    pause
    exit /b 1
)

start "" javaw -cp "%~dp0out" ChronoCircleApp
