@echo off
setlocal
cd /d "%~dp0server"
where python >nul 2>nul || (echo Python is not installed. Install it from python.org and tick "Add python.exe to PATH". & pause & exit /b 1)
if not exist .venv python -m venv .venv
call .venv\Scripts\activate
pip install -q -r requirements.txt
rem Demo settings: sign-up codes are shown in the app instead of being emailed. Never use these in production.
set DEV_ECHO_OTP=1
set SECRET_KEY=dev-only-secret-key-change-before-real-use-0123456789
python -m scripts.seed_demo
if exist .admin_done goto run
set /p ADMIN_EMAIL=Admin email (must end with @strathmore.edu): 
set /p ADMIN_PASS=Admin password (8+ characters, letters and numbers): 
python -m scripts.create_admin "%ADMIN_EMAIL%" "%ADMIN_PASS%"
if errorlevel 1 goto fail
echo done> .admin_done
:run
echo.
echo SkillMatch server is starting. Admin page: http://localhost:8000/admin   API docs: http://localhost:8000/docs
echo Keep this window open while you use the app. Press Ctrl+C to stop.
uvicorn app.main:app --host 0.0.0.0 --port 8000
goto end
:fail
echo Admin setup failed. Read the message above, then run this file again.
pause
:end
