import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from friday_engine import AIProviderError
from friday_server import RequestSizeLimitMiddleware, create_app, get_server_access_token


ACCESS_TOKEN = "test-access-token-that-is-at-least-32-characters"


class FakeProvider:
    name = "test-provider"
    configured = True

    def __init__(self, response="Actual AI response", error=None):
        self.response = response
        self.error = error
        self.messages = None

    def is_ready(self):
        return True

    def generate(self, messages):
        self.messages = messages
        if self.error:
            raise self.error
        return self.response


class FridayServerTests(unittest.TestCase):
    def setUp(self):
        self.provider = FakeProvider()
        self.app = create_app(
            provider_factory=lambda: self.provider,
            token_provider=lambda: ACCESS_TOKEN,
        )
        self.client = TestClient(self.app)
        self.client.__enter__()
        self.addCleanup(self.client.__exit__, None, None, None)

    @staticmethod
    def request_body():
        return {
            "message": "Latest question",
            "messages": [
                {"role": "user", "content": "Earlier question"},
                {"role": "assistant", "content": "Earlier actual answer"},
                {"role": "user", "content": "Latest question"},
            ],
        }

    def test_health_reports_server_engine_and_provider_state(self):
        response = self.client.get("/health")
        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {
                "server_running": True,
                "assistant_engine_initialized": True,
                "provider": "test-provider",
                "configured": True,
                "ready": True,
            },
            response.json(),
        )

    def test_chat_requires_a_valid_bearer_token(self):
        unauthorized = self.client.post("/api/chat", json=self.request_body())
        invalid = self.client.post(
            "/api/chat",
            json=self.request_body(),
            headers={"Authorization": "Bearer wrong-token"},
        )
        self.assertEqual(401, unauthorized.status_code)
        self.assertEqual(401, invalid.status_code)
        self.assertIsNone(self.provider.messages)

    def test_chat_passes_complete_history_and_returns_provider_response(self):
        response = self.client.post(
            "/api/chat",
            json=self.request_body(),
            headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
        )
        self.assertEqual(
            {"reply": "Actual AI response", "status": "success"},
            response.json(),
        )
        self.assertEqual(self.request_body()["messages"], self.provider.messages)

    def test_rejects_mismatched_message_and_history(self):
        body = self.request_body()
        body["message"] = "Not the latest message"
        response = self.client.post(
            "/api/chat",
            json=body,
            headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
        )
        self.assertEqual(422, response.status_code)
        self.assertEqual(
            {"status": "error", "error": "The chat request is invalid."},
            response.json(),
        )
        self.assertIsNone(self.provider.messages)

    def test_provider_failure_returns_service_unavailable_without_fake_reply(self):
        self.provider.error = AIProviderError("The AI provider request timed out.")
        response = self.client.post(
            "/api/chat",
            json=self.request_body(),
            headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
        )
        self.assertEqual(503, response.status_code)
        self.assertEqual(
            {
                "status": "error",
                "error": "The AI provider request timed out.",
            },
            response.json(),
        )

    def test_rejects_oversized_request_body(self):
        app = create_app(
            provider_factory=lambda: FakeProvider(),
            token_provider=lambda: ACCESS_TOKEN,
        )
        app.add_middleware(RequestSizeLimitMiddleware, max_bytes=32)
        with TestClient(app) as client:
            response = client.post(
                "/api/chat",
                content=json.dumps(self.request_body()),
                headers={
                    "Authorization": f"Bearer {ACCESS_TOKEN}",
                    "Content-Type": "application/json",
                },
            )
        self.assertEqual(413, response.status_code)
        self.assertEqual("error", response.json()["status"])

    def test_generates_token_outside_workspace_and_reuses_it(self):
        with tempfile.TemporaryDirectory() as directory:
            token_path = Path(directory) / "friday" / "server-token"
            with patch.dict(
                "os.environ",
                {
                    "FRIDAY_SERVER_TOKEN": "",
                    "FRIDAY_SERVER_TOKEN_FILE": str(token_path),
                },
                clear=False,
            ):
                first = get_server_access_token()
                second = get_server_access_token()

            self.assertEqual(first, second)
            self.assertGreaterEqual(len(first), 32)
            self.assertEqual(first, token_path.read_text(encoding="utf-8").strip())


if __name__ == "__main__":
    unittest.main()
