# F.R.I.D.A.Y. Windows intelligence host

The Android app connects over a trusted private LAN to this Python 3.12 FastAPI
service. It sends the ordered local conversation to a host-configured
OpenAI-compatible chat-completions provider and displays only the provider's
actual reply. There is no canned, classifier, echo, or simulated response path.

## Existing assistant and integration boundary

The existing `Friday.py` desktop assistant uses `neuralintents==0.0.4` with
`intents.json`. That component classifies one utterance and chooses a configured
intent response; it does not generate contextual dialogue. The desktop entry
point also starts its UI/audio workflow and can invoke mapped local actions, so
the HTTP service intentionally does not import it or run those actions.

The server instead retains one lightweight OpenAI-compatible provider client
for its lifetime. Each chat request passes the supplied history to that
provider. Provider credentials and the selected model are read only on the
Windows host. The Android app never receives provider credentials.

## Windows 10 setup

From the repository root in PowerShell, create a Python 3.12 environment and
install the separate backend requirements. The existing desktop
`requirements.txt` remains unchanged and is not required by this service.

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-backend.txt
New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\F.R.I.D.A.Y" | Out-Null
Copy-Item .env.example "$env:LOCALAPPDATA\F.R.I.D.A.Y\server.env"
notepad "$env:LOCALAPPDATA\F.R.I.D.A.Y\server.env"
```

In the user-profile `server.env`, set the provider's OpenAI-compatible API base
URL, model identifier, and API key. Keep the key and server token out of the
repository and Android build. Do not copy the existing desktop integration's
legacy credential literals into this service; rotate any legacy credentials
that may have been exposed.

Start the host with `start_friday_server.bat`. It listens on all host interfaces
at port `8765` by default; `FRIDAY_HOST` and `FRIDAY_PORT` can be changed in the
user-profile configuration file. The API access token is generated once and
stored in `%LOCALAPPDATA%\F.R.I.D.A.Y\server-token`, outside the repository.
To copy it into Android Connection settings, run:

```powershell
.\.venv\Scripts\python.exe .\friday_server.py --show-token
```

Treat that output like a password. Do not include it in screenshots, logs,
source files, Gradle properties, or issue reports. Alternatively, a
`FRIDAY_SERVER_TOKEN` of at least 32 random characters can be configured in the
private host environment file.

## API contract

`GET /health` is unauthenticated and reports whether the HTTP server and engine
are running, whether provider configuration is present, and whether the
provider responds to a short readiness check. It never returns credentials.

`POST /api/chat` requires `Authorization: Bearer <server-token>` and JSON:

```json
{
  "message": "Follow-up question",
  "messages": [
    {"role": "user", "content": "Earlier question"},
    {"role": "assistant", "content": "Earlier generated response"},
    {"role": "user", "content": "Follow-up question"}
  ]
}
```

The `messages` array has up to 100 ordered user/assistant turns; `message` must
match its last user entry. Each content string is limited to 16,000 characters
and request bodies to 1 MB. Successful responses have the exact shape:

```json
{"reply": "The actual response returned by the configured AI provider.", "status": "success"}
```

Authentication failures return HTTP 401; invalid requests return 422; missing,
unavailable, or failing providers return HTTP 503 with a safe error message.
Provider timeouts, HTTP errors, invalid response payloads, and empty replies
are surfaced as errors; the server does not substitute a fallback answer. The
host API token is compared without being logged. Provider keys are sent only
from the host to the configured provider.

The default provider URL is HTTPS. Plain HTTP provider URLs are accepted only
for loopback development endpoints to avoid sending credentials over an
unencrypted network.

## LAN and Android setup

On Windows, use `ipconfig` to find the PC's private LAN IPv4 address. In the
Android app's **CONNECTION** dialog, enter:

```text
http://<PC-LAN-IP>:8765/api/chat
```

Paste the host token from `--show-token`; Android stores it encrypted with an
Android Keystore-backed AES-GCM key. For an Android emulator that maps the host
to its gateway, use `http://10.0.2.2:8765/api/chat`. A physical phone must use
the PC's reachable private LAN address.

Allow inbound TCP port 8765 in Windows Firewall only for the trusted private
network profile needed by the phone. Do not configure router port forwarding
or expose the service to the public internet. The development Android build
permits local HTTP for the Windows LAN connection; do not use that transport on
an untrusted network.

Verify the host independently at `http://<PC-LAN-IP>:8765/health`. A response
with `configured: false` means provider URL/model/key configuration is missing;
`ready: false` means the provider readiness check did not succeed. The health
check is not proof that the provider will accept a chat request.

## Tests and verification limits

Run automated tests from the repository root:

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

These tests use local fake provider boundaries to verify request history,
serialization, authorization, error handling, and token persistence. They do
not verify a live provider account, network access from a Windows host, or a
physical Android device. Live AI responses require a real compatible provider
URL, model, and host-only API key in the private configuration file.
