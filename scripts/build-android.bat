@echo off
echo ========================================================
echo   Building StarX Mail Android APK
echo ========================================================

cd "%~dp0\..\android"

if not defined JAVA_HOME (
    set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\
)

echo Using JAVA_HOME: %JAVA_HOME%

echo Running unit tests...
call gradlew.bat test

echo Assembling Debug APK...
call gradlew.bat assembleDebug

echo.
echo ========================================================
echo   Android build completed successfully!
echo   APK location: android\app\build\outputs\apk\debug\app-debug.apk
echo ========================================================
pause
