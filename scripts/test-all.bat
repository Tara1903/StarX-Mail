@echo off
echo ========================================================
echo   Running All Tests (Backend + Android)
echo ========================================================

echo 1. Testing Backend...
cd "%~dp0\..\backend"
call npm test
if %errorlevel% neq 0 (
    echo [ERROR] Backend tests failed!
    exit /b %errorlevel%
)

echo.
echo 2. Testing Android...
cd "%~dp0\..\android"
if not defined JAVA_HOME (
    set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\
)
call gradlew.bat test
if %errorlevel% neq 0 (
    echo [ERROR] Android tests failed!
    exit /b %errorlevel%
)

echo.
echo ========================================================
echo   All Tests Passed! (Backend + Android)
echo ========================================================
pause
