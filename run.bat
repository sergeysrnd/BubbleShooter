@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
    for /d %%J in ("%LOCALAPPDATA%\Programs\Java\jdk-21*") do set "JAVA_HOME=%%~fJ"
)
echo Building and running BubbleShooter...
call gradlew.bat installDist
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Starting BubbleShooter...
call build\install\BubbleShooter\bin\BubbleShooter.bat
pause
