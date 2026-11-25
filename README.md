# SkillMatch

Android app + server in one project. Students log in with their Strathmore email, add skills (or upload a CV),
and see checked internship and WBL listings with their fit shown **requirement by requirement**
(Met / Partly met / Missing). Admins add and check listings on a web page. A study mode records timed sessions
for the research.

Kotlin · Jetpack Compose (Material 3) · MVVM + StateFlow · Navigation Compose · minSdk 26
Server: Python · FastAPI · SQLite (PostgreSQL later) · scikit-learn

```
SkillMatch/
  app/                 the Android app
  server/              the server (Python)
  start-server.bat     starts the server on your PC (start-server.sh on Mac/Linux)
  render.yaml          lets you put the server online on Render in one step
```

## Run it on your own PC (3 steps)

**1. Start the server.** Install Python 3.10+ (python.org, tick "Add to PATH"), then double-click `start-server.bat`.
The first run installs things and asks you to make an **admin email and password**. Leave the window open.
Check http://localhost:8000/docs opens in your browser.

**2. Run the app.** In Android Studio: File → Open → pick this `SkillMatch` folder (the one with `settings.gradle.kts`),
wait for Gradle sync, set Gradle JDK to 17 or 21, pick a device and press ▶.

**3. Point the app at the server.**
| Where the app runs | Server address |
|---|---|
| Android emulator | `http://10.0.2.2:8000` (already the default) |
| Real phone, same Wi-Fi as your PC | `http://YOUR-PC-IP:8000`. Find the IP with `ipconfig` (IPv4 Address). Allow Python through the Windows firewall. |

On the login screen tap **Server address**, type it, and Save.

## Put the server online (Render, free)

Do this once. Then the app works from any phone with internet and nobody has to run `start-server.bat`.

1. **GitHub:** create an empty repository at github.com/new (private is fine). In this folder, open a terminal and run:
   ```
   git init
   git add .
   git commit -m "SkillMatch"
   git branch -M main
   git remote add origin https://github.com/YOUR-NAME/YOUR-REPO.git
   git push -u origin main
   ```
2. **Render:** sign up at render.com with GitHub. New → **Blueprint** → pick the repository. It reads `render.yaml`.
   It asks for `ADMIN_EMAIL` (must end with @strathmore.edu) and `ADMIN_PASSWORD`. Fill them in and click Apply.
3. Wait for the first deploy (about 5 minutes). Open the address Render shows, like `https://skillmatch-server-xxxx.onrender.com/health`. You should see `{"status":"ok"...}`.
4. **In the app:** login screen → Server address → type that `https://...onrender.com` address (no trailing slash) → Save.
5. **Admin page:** `https://...onrender.com/admin`. The admin login code is not emailed. Open the service's **Logs** tab on Render and copy the code printed there.

Good to know (free tier):
* The server **sleeps after 15 minutes without use** and takes about a minute to wake up. Open the `/health` address a couple of minutes before your demo.
* The free database **expires after 30 days**. Fine for the defence. Move to a paid database or export your data (admin page, CSV) before then.
* `DEV_ECHO_OTP=1` is on so sign-up works without email. That means anyone can create accounts or reset a password, so use it for demos with fake data only.
  For real students, set `DEV_ECHO_OTP` to `0` and add the `SMTP_*` settings (see `server/.env.example`).

## Try it

1. **Sign up** with any `@strathmore.edu` address. The code is shown on screen in demo mode (no email needed).
2. **My skills** tab: choose a CV file (PDF, DOCX or TXT) or add skills by hand, then **Save and see my fit**.
3. **Listings** tab: ranked by fit. Open one to see each requirement marked Met / Partly met / Missing, warning flags,
   and the apply link. Save or report a listing.
4. **Admin:** open `http://localhost:8000/admin` in a browser, log in (the admin code is printed in the server window),
   add a listing, check the requirements it was split into, and publish. Reports from students show up there too.
5. **Study mode:** Account → Start a study session. Pick *without fit scores* or *with fit scores* and Set A or B.
   Star the listings you would apply for, finish, rate your confidence, then answer the SUS (and TAM) survey.
   Admins download the data as CSV (participant codes only, never emails).

The 6 listings added by the demo are SAMPLES. Replace them with real, checked listings from the admin page.

## Tests
Server: `cd server`, activate the venv, `python -m pytest -q` (19 tests).

## Notes
* Demo mode (`DEV_ECHO_OTP=1`) shows codes in the app. For real use set `SMTP_*` settings, a long `SECRET_KEY`, and put the server behind HTTPS.
  Release builds of the app only allow https addresses. Debug builds also allow http for development.
* The earlier offline version (on-phone TF-IDF model and US job listings) has been replaced by the server's matching.
* More detail about the server and matching: `server/README.md`.

## Troubleshooting
* **Sync fails / Unsupported Java:** Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK = 17 or 21.
* **"Can't reach the server":** is the server window still open? Right address for emulator vs phone? Firewall allowed Python?
* **Server changed but the app shows old data:** delete `server/skillmatch.db` and restart (it is recreated with the sample listings).
