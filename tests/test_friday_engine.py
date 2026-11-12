import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

import friday_engine
from friday_engine import (
    AIProviderError,
    OpenAICompatibleProvider,
    generate_response,
    initialize_assistant,
    reset_provider_for_tests,
)


class FakeHttpResponse:
    def __init__(self, payload, status=200):
        self.payload = payload
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None

    def read(self, _limit=-1):
        return self.payload


class FridayEngineTests(unittest.TestCase):
    def setUp(self):
        reset_provider_for_tests()

    def tearDown(self):
        reset_provider_for_tests()

    def test_initializes_one_provider_from_host_configuration(self):
        environment = {
            "FRIDAY_AI_PROVIDER": "openai_compatible",
            "FRIDAY_AI_BASE_URL": "https://provider.example/v1",
            "FRIDAY_AI_MODEL": "model-name",
            "FRIDAY_AI_API_KEY": "host-only-test-key",
            "FRIDAY_AI_TIMEOUT_SECONDS": "9",
        }
        with patch.dict("os.environ", environment, clear=True):
            provider = initialize_assistant()
            self.assertIs(provider, initialize_assistant())

        self.assertIsInstance(provider, OpenAICompatibleProvider)
        self.assertEqual("model-name", provider.model)
        self.assertEqual(9, provider.timeout_seconds)
        self.assertTrue(provider.configured)

    def test_generates_actual_provider_content_with_full_history(self):
        messages = [
            {"role": "user", "content": "Earlier question"},
            {"role": "assistant", "content": "Earlier generated answer"},
            {"role": "user", "content": "Follow up"},
        ]
        captured_request = []
        response_body = json.dumps(
            {"choices": [{"message": {"content": "Actual generated answer"}}]}
        ).encode()

        def fake_urlopen(request, timeout):
            captured_request.append((request, timeout))
            return FakeHttpResponse(response_body)

        with patch.dict(
            "os.environ",
            {
                "FRIDAY_AI_BASE_URL": "https://provider.example/v1",
                "FRIDAY_AI_MODEL": "model-name",
                "FRIDAY_AI_API_KEY": "host-only-test-key",
            },
            clear=True,
        ), patch("friday_engine.urlopen", side_effect=fake_urlopen):
            response = generate_response(messages)

        self.assertEqual("Actual generated answer", response)
        request, timeout = captured_request[0]
        self.assertEqual("https://provider.example/v1/chat/completions", request.full_url)
        self.assertEqual("Bearer host-only-test-key", request.get_header("Authorization"))
        self.assertEqual(messages, json.loads(request.data)["messages"])
        self.assertEqual(30, timeout)

    def test_rejects_missing_or_non_user_latest_turn(self):
        with self.assertRaises(ValueError):
            generate_response([])
        with self.assertRaises(ValueError):
            generate_response([{"role": "assistant", "content": "response"}])

    def test_unconfigured_provider_does_not_return_a_simulated_response(self):
        provider = OpenAICompatibleProvider(
            base_url="https://provider.example/v1",
            model="",
            api_key="",
            timeout_seconds=30,
        )

        with self.assertRaisesRegex(AIProviderError, "not configured"):
            provider.generate([{"role": "user", "content": "Hello"}])

    def test_provider_http_error_is_safe_and_does_not_expose_credentials(self):
        provider = OpenAICompatibleProvider(
            base_url="https://provider.example/v1",
            model="model-name",
            api_key="secret-test-key",
            timeout_seconds=2,
        )
        error = HTTPError(
            "https://provider.example/v1/chat/completions",
            401,
            "unauthorized",
            {},
            None,
        )
        with patch("friday_engine.urlopen", side_effect=error):
            with self.assertRaises(AIProviderError) as raised:
                provider.generate([{"role": "user", "content": "Hello"}])

        self.assertNotIn("secret-test-key", str(raised.exception))
        self.assertIn("rejected", str(raised.exception))

    def test_invalid_provider_payload_is_reported(self):
        provider = OpenAICompatibleProvider(
            base_url="https://provider.example/v1",
            model="model-name",
            api_key="secret-test-key",
            timeout_seconds=2,
        )
        with patch("friday_engine.urlopen", return_value=FakeHttpResponse(b'{"choices": []}')):
            with self.assertRaisesRegex(AIProviderError, "unexpected response"):
                provider.generate([{"role": "user", "content": "Hello"}])

    def test_plain_http_provider_url_is_restricted_to_loopback(self):
        self.assertFalse(
            OpenAICompatibleProvider(
                base_url="http://provider.example/v1",
                model="model",
                api_key="secret",
                timeout_seconds=2,
            ).configured
        )
        self.assertTrue(
            OpenAICompatibleProvider(
                base_url="http://127.0.0.1:8080/v1",
                model="model",
                api_key="secret",
                timeout_seconds=2,
            ).configured
        )


if __name__ == "__main__":
    unittest.main()
