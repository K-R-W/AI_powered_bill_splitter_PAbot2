# PA_bot2

An Android app that scans receipts with AI and splits the bill among friends, with optional export to Splitwise.

## Features

- **AI bill scanning** — photograph a receipt; Gemini extracts every line item and a suggested title automatically
- **Flexible splitting** — assign items to one or more people; shared items are divided evenly
- **Multi-currency support** — works with any currency symbol
- **Splitwise export** — link your Splitwise account and push splits directly
- **Adaptive layout** — list/detail pane on tablets; single-column on phones; Material Design 3 throughout

## Requirements

- Android 12+ (API 31)
- Android Studio Hedgehog or newer
- A Google account (for Gemini AI scanning)
- A Splitwise account (optional — only needed for export)

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
cd YOUR_REPO_NAME
```

### 2. Create your secrets file

```bash
cp .env.template .env
```

Open `.env` and fill in the three values described in the sections below.

---

### 3. Google OAuth credential (required for AI scanning)

The app signs in with Google to obtain an access token for the Gemini API.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project (or select an existing one).
2. Enable the **Generative Language API**: *APIs & Services → Library → search "Generative Language API" → Enable*.
3. Configure the OAuth consent screen: *APIs & Services → OAuth consent screen*.
   - User type: **External**
   - Fill in app name, support email, and developer contact.
   - Add the scope `https://www.googleapis.com/auth/generative-language.retriever`.
4. Create a **Web Application** credential: *APIs & Services → Credentials → Create Credentials → OAuth client ID → Web application*.
   - No redirect URIs needed for this credential.
   - Copy the **Client ID** — this is your `GOOGLE_SERVER_CLIENT_ID`.
5. Create an **Android** credential for the same project: *Create Credentials → OAuth client ID → Android*.
   - Package name: `com.example.pa_bot2`
   - SHA-1: run the command below and paste the result.

```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# macOS / Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Add to `.env`:
```
GOOGLE_SERVER_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

---

### 4. Splitwise OAuth credentials (optional)

Only needed if you want to export splits to Splitwise.

1. Go to [https://secure.splitwise.com/apps](https://secure.splitwise.com/apps) and click **Register your application**.
2. Set the **Callback URL** to exactly: `http://localhost:8080/splitwise-callback`
3. After saving, copy the **Consumer Key** and **Consumer Secret**.

Add to `.env`:
```
SPLITWISE_CLIENT_ID=your_splitwise_consumer_key
SPLITWISE_CLIENT_SECRET=your_splitwise_consumer_secret
```

> If you don't need Splitwise, leave the values as the placeholder text. The app builds and runs without them; the Splitwise link button will simply not complete authentication.

---

### 5. Build and run

Open the project in Android Studio and run the `app` configuration on a device or emulator running Android 12+. The secrets in `.env` are loaded at compile time via `BuildConfig` — no runtime config files needed.

---

## Project structure

```
app/src/main/java/com/example/pa_bot2/
├── api/            Splitwise Retrofit API definition
├── model/          Room database, repositories, data models
├── navigation/     Navigation 3 destination definitions
├── ocr/            BillExtractor interface + Gemini implementation
├── ui/
│   ├── screens/    Composable screens (Home, Capture, Detail, Settings, Onboarding)
│   └── theme/      Material 3 colour, typography, theme
└── util/           AuthHandler (Google), SplitwiseAuthHandler, camera & bill utilities
```

## Tech stack

| Layer | Library |
|-------|---------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation 3 |
| Adaptive layout | Compose Material Adaptive (`ListDetailPaneScaffold`) |
| Camera | CameraX |
| AI / OCR | Google Gemini API (via user's Google account) |
| Persistence | Room + DataStore |
| Networking | OkHttp + Retrofit + Moshi / kotlinx.serialization |
| Auth | Credential Manager (Google Sign-In), Splitwise OAuth 2.0 |

## Contributing

Contributions are welcome. Open an issue first for significant changes so we can discuss the approach.

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.
