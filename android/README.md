# F.R.I.D.A.Y. Android conversation

The Android project retains its Compose conversation UI, ViewModel, repository,
local JSON history, and Filament orb renderer. The backend host setup and
Windows API contract are documented in [../BACKEND.md](../BACKEND.md).

## Connect to the Windows AI host

1. Configure and run the Windows service using the steps in
   [../BACKEND.md](../BACKEND.md).
2. In the app, open **CONNECTION**.
3. Enter the complete private-LAN endpoint, for example
   `http://192.168.1.20:8765/api/chat`.
4. Enter the server access token printed by the host's `friday_server.py
   --show-token` command and save.

The endpoint is stored in app preferences. The server token is encrypted using
AES-GCM with a key stored in Android Keystore; it is not compiled into
`BuildConfig`, written to the conversation file, or sent to the AI provider.
Leaving the token field blank when saving retains the previously saved token.
Use **CLEAR TOKEN** to remove it.

The Android request is:

```json
{
  "message": "latest user text",
  "messages": [
    {"role": "user", "content": "earlier text"},
    {"role": "assistant", "content": "actual earlier provider response"},
    {"role": "user", "content": "latest user text"}
  ]
}
```

It includes `Authorization: Bearer <server-token>` and expects the actual
server response `{"reply":"...","status":"success"}`. HTTP errors, invalid
responses, timeouts, missing credentials, and server/provider unavailability
are shown as errors rather than assistant messages.

## Windows networking and safety

An Android emulator cannot use its own `localhost` to reach the Windows host.
Use `10.0.2.2` when the emulator provides that host-gateway mapping. A
physical Android device must use the PC's reachable private LAN address. Allow
inbound TCP port 8765 in Windows Firewall only on the trusted private network
profile. Do not configure public port forwarding or use the service on an
untrusted network. The debug build allows HTTP for this local deployment; use
HTTPS if connecting over an untrusted network.

## Build and tests

Run unit tests and assemble the debug APK from this directory:

```sh
ANDROID_HOME=/home/codespace/android-sdk \
ANDROID_SDK_ROOT=/home/codespace/android-sdk \
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```

Android unit tests exercise the exact API serialization, bearer header,
conversation state transitions, and on-disk conversation persistence. They use
a local test server and do not prove a live Windows backend, provider account,
or physical-device network connection.
