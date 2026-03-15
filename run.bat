@echo off
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
