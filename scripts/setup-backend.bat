@echo off
echo ========================================================
echo   Setting up StarX Mail Backend
echo ========================================================

cd "%~dp0\..\backend"

if not exist .env (
    echo Creating .env from .env.example...
    copy .env.example .env
)

echo Installing dependencies...
call npm install

echo Building TypeScript project...
call npm run build

echo.
echo ========================================================
echo   Backend setup complete!
echo   Run 'npm start' or 'npm run dev' inside backend/
echo ========================================================
pause
