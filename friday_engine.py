"""Provider-backed conversation engine for the F.R.I.D.A.Y. LAN service."""

from __future__ import annotations

import json
import os
import socket
from dataclasses import dataclass
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class AIProviderError(RuntimeError):
    """A safe, user-facing provider failure."""


class AIProvider(Protocol):
    name: str

    @property
    def configured(self) -> bool: ...

    def is_ready(self) -> bool: ...

    def generate(self, messages: list[dict[str, str]]) -> str: ...


@dataclass(frozen=True)
class OpenAICompatibleProvider:
    base_url: str
    model: str
    api_key: str
    timeout_seconds: float
    name: str = "openai_compatible"

    @property
    def configured(self) -> bool:
        return bool(
            self.base_url.strip()
            and self.model.strip()
            and self.api_key.strip()
            and self.timeout_seconds > 0
            and _provider_url_is_safe(self.base_url)
        )

    def is_ready(self) -> bool:
        if not self.configured:
            return False
        request = Request(
            f"{self.base_url.rstrip('/')}/models",
            headers={"Authorization": f"Bearer {self.api_key}", "Accept": "application/json"},
            method="GET",
        )
        try:
            with urlopen(request, timeout=min(self.timeout_seconds, 3.0)) as response:
                response.read(1)
                return 200 <= response.status < 300
        except (HTTPError, URLError, TimeoutError, socket.timeout, OSError):
            return False

    def generate(self, messages: list[dict[str, str]]) -> str:
        if not self.configured:
            raise AIProviderError(
                "The AI provider is not configured. Set its base URL, model, and API key on the host."
            )

        payload = json.dumps(
            {"model": self.model, "messages": messages},
            ensure_ascii=False,
        ).encode("utf-8")
        request = Request(
            f"{self.base_url.rstrip('/')}/chat/completions",
            data=payload,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                response_body = response.read(_MAX_PROVIDER_RESPONSE_BYTES + 1)
        except HTTPError as error:
            error.close()
            if error.code in (401, 403):
                raise AIProviderError(
                    "The AI provider rejected its host-side credentials or model access."
                ) from None
            if error.code == 429:
                raise AIProviderError("The AI provider rate limit was reached.") from None
            if error.code >= 500:
                raise AIProviderError("The AI provider is temporarily unavailable.") from None
            raise AIProviderError(
                f"The AI provider rejected the request (HTTP {error.code})."
            ) from None
        except (TimeoutError, socket.timeout):
            raise AIProviderError("The AI provider request timed out.") from None
        except (URLError, OSError):
            raise AIProviderError("The AI provider could not be reached.") from None

        if len(response_body) > _MAX_PROVIDER_RESPONSE_BYTES:
            raise AIProviderError("The AI provider returned an oversized response.")
        try:
            data = json.loads(response_body)
            content = data["choices"][0]["message"]["content"]
        except (json.JSONDecodeError, KeyError, IndexError, TypeError):
            raise AIProviderError("The AI provider returned an unexpected response.") from None

        if not isinstance(content, str) or not content.strip():
            raise AIProviderError("The AI provider returned an empty response.")
        return content.strip()


_MAX_PROVIDER_RESPONSE_BYTES = 1_000_000
_provider: AIProvider | None = None


def _provider_url_is_safe(base_url: str) -> bool:
    try:
        from urllib.parse import urlparse

        parsed = urlparse(base_url.strip())
    except ValueError:
        return False
    if parsed.scheme == "https":
        return bool(parsed.hostname and parsed.username is None and parsed.password is None)
    if parsed.scheme == "http" and parsed.hostname:
        return parsed.hostname.lower() in {"localhost", "127.0.0.1", "::1"}
    return False


def initialize_assistant() -> AIProvider:
    """Initialize the configured provider once and retain it for the server lifetime."""
    global _provider
    if _provider is None:
        provider_name = os.getenv("FRIDAY_AI_PROVIDER", "openai_compatible").strip().lower()
        if provider_name != "openai_compatible":
            raise AIProviderError("FRIDAY_AI_PROVIDER must be openai_compatible.")
        try:
            timeout = float(os.getenv("FRIDAY_AI_TIMEOUT_SECONDS", "30"))
        except ValueError:
            timeout = 0
        _provider = OpenAICompatibleProvider(
            base_url=os.getenv("FRIDAY_AI_BASE_URL", "https://api.openai.com/v1").strip(),
            model=os.getenv("FRIDAY_AI_MODEL", "").strip(),
            api_key=os.getenv("FRIDAY_AI_API_KEY", "").strip(),
            timeout_seconds=timeout,
        )
    return _provider


def generate_response(messages: list[dict[str, str]]) -> str:
    """Generate an answer from the configured provider using the complete supplied history."""
    if not messages or messages[-1].get("role") != "user":
        raise ValueError("The latest conversation message must be from the user.")
    return initialize_assistant().generate(messages)


def provider_health(provider: AIProvider | None = None) -> dict[str, bool | str]:
    selected = provider or initialize_assistant()
    return {
        "provider": selected.name,
        "configured": selected.configured,
        "ready": selected.is_ready(),
    }


def reset_provider_for_tests() -> None:
    global _provider
    _provider = None
