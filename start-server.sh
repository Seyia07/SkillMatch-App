#!/usr/bin/env bash
# Mac / Linux version of start-server.bat
set -e
cd "$(dirname "$0")/server"
[ -d .venv ] || python3 -m venv .venv
source .venv/bin/activate
pip install -q -r requirements.txt
export DEV_ECHO_OTP=1                       # demo only: show sign-up codes in the app
export SECRET_KEY=dev-only-secret-key-change-before-real-use-0123456789
python -m scripts.seed_demo
if [ ! -f .admin_done ]; then
  read -r -p "Admin email (must end with @strathmore.edu): " ADMIN_EMAIL
  read -r -s -p "Admin password (8+ characters, letters and numbers): " ADMIN_PASS; echo
  python -m scripts.create_admin "$ADMIN_EMAIL" "$ADMIN_PASS" && touch .admin_done
fi
echo "Admin page: http://localhost:8000/admin   API docs: http://localhost:8000/docs"
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
