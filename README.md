# FitPal

An **offline-first** Android calorie and fitness tracker with an optional AI food-recognition
layer and a Wear OS companion for the Galaxy Watch. Everything works with no internet — AI photo
recognition, a cloud model, and Wear OS sync are opt-in extras layered on top of a fully local app.

- 📸 Snap a photo, describe a meal in words, scan a barcode, or type in your own numbers
- 🧠 Food recognition runs **on-device** (private, free, no internet) or, if you add your own key,
  on **Google Gemini** for sharper results — it falls back automatically
- 📊 Analytics, a daily "balance" score, AI-written daily/weekly/monthly reviews
- ⌚ A Galaxy Watch companion for quick water/meal/exercise logging and live stats
- 🔒 No account, no server, no ads. Your data stays on your phone unless you export it yourself

---

## Contents

- [Installing on your phone](#installing-on-your-phone)
- [Installing on your watch](#installing-on-your-watch-optional)
- [First run](#first-run)
- [Connecting AI (optional)](#connecting-ai-optional)
- [Settings, explained](#settings-explained)
- [Data & privacy](#data--privacy)
- [Building from source](#building-from-source)
- [Credits & licensing](#credits--licensing)

---

## Installing on your phone

FitPal isn't on the Play Store — grab the APK from **[Releases](../../releases/latest)**.

1. On your phone, open the release page above and download **`FitPal-app-<version>.apk`**.
2. Tap the downloaded file. If Android blocks it, you'll be prompted to allow your browser (or
   Files app) to **install unknown apps** — approve that once and tap install again.
3. Open FitPal and grant the permissions it asks for as you go:
   - **Camera** — for photo-based food recognition
   - **Notifications** — for the "analysing…" / reminder notifications
   - Anything else (Health Connect, etc.) is optional and only asked for when you turn on that
     specific feature in Settings.
4. Walk through the short first-run setup (see [First run](#first-run) below).

**Requirements:** Android 9 (API 28) or newer.

> These release builds are signed with a shared debug key so the phone and watch apps can talk to
> each other out of the box — this is a personal/sideloaded distribution, not a Play Store release.

---

## Installing on your watch (optional)

The watch app is a **thin remote** for your phone — it doesn't run any AI itself, so the phone app
must be installed first (see above). It works even when the phone app isn't open.

1. **Pair the watch to your phone** first, the normal way, via the **Galaxy Wearable** app (if you
   haven't already).
2. **Turn on developer options on the watch:** Watch **Settings → About watch** → tap **Software
   version** repeatedly until developer mode turns on.
3. **Turn on ADB debugging:** Watch **Settings → Developer options** → enable **ADB debugging** and
   **Debug over Wi-Fi**. Note the IP address it shows.
4. **On your phone or PC**, with [ADB](https://developer.android.com/tools/adb) installed:
   ```bash
   adb connect <watch-ip>:5555
   adb install -r FitPal-watch-<version>.apk
   ```
5. Open **FitPal** on the watch. On first open, tap **"Sync watch steps"** on the home screen and
   allow the activity-recognition permission — this lets the watch report its own step count
   directly, so your step total never depends on Samsung Health syncing it for you.
6. You should see a small dot on the watch screen: **green = phone connected**, **red = not
   reachable**, with a **Reconnect** button if it ever needs a nudge. The same status (plus a
   manual **Reconnect & sync**) is in **Settings → Activity & health** on the phone.

What the watch can do: log water (also from a **tile** and a **complication** on the watch face),
describe a meal or workout by voice/text (the phone does the AI work in the background and shows a
notification when it's ready to review), and see today's calories/macros/water/burn at a glance.
**Reviewing and editing results always happens on the phone** — the watch is for quick input, not
detailed editing.

---

## First run

The first time you open FitPal it asks a few quick questions — sex, age, height, current weight,
and your goal (lose fat / maintain / build muscle / recomp) — so your calorie and macro targets are
accurate from day one, then shows a short intro to logging. You can change any of this later in
**Settings → Profile & goals**.

---

## Connecting AI (optional)

FitPal recognises food from photos and free-text descriptions using AI. **This step is optional —
the app works without it** (you can always log food by typing your own numbers, searching a
built-in food database, or scanning a barcode). Two engines are available, and you can set up
either or both:

### Option A — Online (Google Gemini): faster, no download, needs a free API key

1. Get a free key at [aistudio.google.com](https://aistudio.google.com) → **"API keys"**.
2. In FitPal: **Settings → AI → Online AI** → paste the key.
3. That's it — online analysis is on by default once a key is present, and it automatically falls
   back to on-device (see below) if you're offline, out of free quota, or turn it off.

> Heads up: on Google's free tier, your prompts (and any meal photos you send) may be reviewed to
> improve their models. Use the on-device model instead if you'd rather nothing leave your phone.

### Option B — On-device (Gemma 3n): fully private, no internet needed after setup

This is a larger one-time setup because the model itself (~4.4 GB) has to be downloaded, and
Google gates it behind a free Hugging Face account.

1. **Create a free account** at [huggingface.co/join](https://huggingface.co/join) (or log in).
2. **Accept the model's license.** Open
   [google/gemma-3n-E4B-it-litert-preview](https://huggingface.co/google/gemma-3n-E4B-it-litert-preview)
   while logged in, fill in the short form near the top, and click **"Acknowledge license."**
   Access is usually instant.
3. **Create an access token:** [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)
   → **"Create new token"** → **Read** role is enough → copy the value (`hf_…`) — you can't view it
   again later, only regenerate it.
4. In FitPal: **Settings → AI → "On-device AI model"** → paste the token → **Download AI model**.
   Stay on Wi-Fi; it's ~4.4 GB, one time only.
5. When it says **"All set — the AI is ready to use,"** food recognition works fully offline.

If you set up both, FitPal always **prefers online** (when available) and treats on-device as the
fallback for offline use, low quota, or if you turn online AI off.

**Troubleshooting:** "Access denied" almost always means the license wasn't accepted on that exact
repo, or the token belongs to a different Hugging Face account than the one that accepted it. A
failed download can just be retried — no partial files are kept.

### Optional extras on the same screen — bigger offline food databases

Separate from the AI model, and all optional:
- **Full USDA database** (~13 MB) — recommended, thousands more foods for search/barcode.
- **Branded products** (~428 MB) — ~1.9M packaged products, only worth it if you scan a lot of
  packaged food.
- **European foods (Open Food Facts)** — pulls popular Central-European products (regional
  Slovak/Czech/Polish brands, etc.) the USDA set is missing. For a bigger or custom slice than the
  built-in popular set, see [tools/README.md](tools/README.md).

---

## Settings, explained

Settings are grouped Android-style, each its own focused screen:

| Section | What's in it |
|---|---|
| **Profile & goals** | Sex, age, height, current weight, fitness goal; a manual calorie-goal override (leave at 0 to auto-calculate from your profile + weight); per-macro targets (protein/fat/carbs/fiber), each settable to "auto" or a fixed number. |
| **Activity & health** | How much to trim step-based calorie estimates (trackers tend to over-count); connect **Health Connect** so steps Samsung Health writes are read in; watch-link status and a manual reconnect (see [watch install](#installing-on-your-watch-optional)). |
| **Quick-add & meal times** | The tap-to-add portion-size chips shown when logging food and drinks (edit, add, or remove your own); the time windows that decide whether something you log counts as breakfast/lunch/dinner/snack. |
| **AI** | Online Gemini key + model id (with a "test connection" button) and the on-device model download — see [Connecting AI](#connecting-ai-optional); an optional free-text **personal context** field (e.g. "vegetarian," "eat lunch at school," "tight budget") that AI overviews take into account so advice actually fits your life. |
| **Personalize** | Reorder or hide the cards on Home/Analytics; turn on/off the daily reminder, per-meal reminders, weigh-in reminders, and automatic daily/weekly AI overviews (with their own times); a Home display toggle for collapsing empty meal panels. |
| **Data & about** | Export your data to a JSON file / import it back (a manual backup — the downloaded food databases aren't included since they can just be re-downloaded); clear all data; app version and current AI mode. |

A few things worth knowing about how the app behaves:
- **Calorie ring color** reflects your day's overall balance (calories + macros + micronutrients),
  not just whether you hit your calorie number — it turns red specifically when you go over budget.
- **"Your collection"** (saved foods/workouts) is what quick-logging is built around: save anything
  you log often, organize it into folders, and re-log it in one tap next time.
- There's an optional **garden** — a small offline "don't let it wilt" habit-streak feature, entirely
  separate from the nutrition tracking.

---

## Data & privacy

- No account, no login, no ads, no analytics/tracking SDKs.
- All your logs live in a local database on your phone. **Settings → Data & about → Export** writes
  a plain JSON backup you control.
- Nothing leaves your phone unless *you* turn on Online AI (Google) — see the note in
  [Connecting AI](#connecting-ai-optional) about Google's free-tier data usage.
- The Wear OS link talks directly to your paired watch over Bluetooth via the Android Wearable Data
  Layer — no cloud service in between.

---

## Building from source

Requires **Android Studio** (Kotlin 2.0, AGP 8.7). Clone the repo and open it in Android Studio —
it will fetch dependencies and set up the Gradle wrapper automatically. Three modules:

- `:app` — the phone app
- `:wear` — the Wear OS companion (shares the phone app's `applicationId`, so it must be **signed
  with the same key** as whatever `:app` build you install alongside it)
- `:shared` — the small wire contract the two talk over

For the on-device AI and the offline food-database tooling, see
[Connecting AI](#connecting-ai-optional) and [tools/README.md](tools/README.md) above.

---

## Credits & licensing

- Food data: [USDA FoodData Central](https://fdc.nal.usda.gov/) and
  [Open Food Facts](https://world.openfoodfacts.org) (ODbL — keep a "Data: Open Food Facts" credit
  if you redistribute any exported data file).
- On-device AI: Google's **Gemma 3n**, via [MediaPipe](https://ai.google.dev/edge/mediapipe).
- Online AI: Google **Gemini**, via the public Gemini API (a personal API key you provide).
- Fonts: Fraunces and Inter (OFL-licensed; see `app/src/main/assets/licenses/`).

This is a personal project, shared as-is with no warranty.
