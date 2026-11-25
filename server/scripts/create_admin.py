"""Create or promote an admin account.  Usage: python -m scripts.create_admin you@strathmore.edu 'Password123'"""
import sys

from app.bootstrap import ensure_admin

if len(sys.argv) != 3:
    sys.exit("Usage: python -m scripts.create_admin EMAIL PASSWORD")
try:
    print(ensure_admin(sys.argv[1], sys.argv[2]))
except ValueError as e:
    sys.exit(str(e))
