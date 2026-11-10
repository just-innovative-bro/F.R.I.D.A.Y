"""Authenticated FastAPI server for the F.R.I.D.A.Y. Android conversation client."""

from __future__ import annotations

import argparse
import hmac
import json
import logging
import os
import secrets
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated, Callable, Literal

from fastapi import FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, model_validator

from friday_engine import AIProvider, AIProviderError, initialize_assistant, provider_health

LOGGER = logging.getLogger("friday.server")
MAX_REQUEST_BYTES = 1_000_000
MAX_MESSAGES = 100
MAX_MESSAGE_LENGTH = 16_000


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)

    @model_validator(mode="after")
    def content_must_not_be_blank(self) -> ChatMessage:
        if not self.content.strip():
            raise ValueError("Message content must not be blank.")
        return self


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    message: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    messages: list[ChatMessage] = Field(min_length=1, max_length=MAX_MESSAGES)

    @model_validator(mode="after")
    def latest_user_message_must_match(self) -> ChatRequest:
        if not self.message.strip():
            raise ValueError("Message must not be blank.")
        latest = self.messages[-1]
        if latest.role != "user" or latest.content != self.message:
            raise ValueError("message must match the latest user entry in messages.")
        return self


class ChatResponse(BaseModel):
    reply: str
    status: Literal["success"] = "success"


class RequestSizeLimitMiddleware:
    def __init__(self, app: Callable, max_bytes: int = MAX_REQUEST_BYTES) -> None:
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope: dict, receive: Callable, send: Callable) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = {key.lower(): value for key, value in scope.get("headers", [])}
        content_length = headers.get(b"content-length")
        if content_length:
            try:
                if int(content_length) > self.max_bytes:
                    await self._reject(send)
                    return
            except ValueError:
                await self._reject(send)
                return

        chunks: list[bytes] = []
        size = 0
        more_body = True
        while more_body:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            size += len(chunk)
            if size > self.max_bytes:
                await self._reject(send)
                return
            chunks.append(chunk)
            more_body = message.get("more_body", False)

        body = b"".join(chunks)
        replayed = False

        async def replay_body() -> dict:
            nonlocal replayed
            if not replayed:
                replayed = True
                return {"type": "http.request", "body": body, "more_body": False}
            return {"type": "http.disconnect"}

        await self.app(scope, replay_body, send)

    @staticmethod
    async def _reject(send: Callable) -> None:
        body = json.dumps(
            {"status": "error", "error": "The request body is too large."}
        ).encode("utf-8")
        await send(
            {
                "type": "http.response.start",
                "status": 413,
                "headers": [
                    (b"content-type", b"application/json"),
                    (b"content-length", str(len(body)).encode("ascii")),
                ],
            }
        )
        await send({"type": "http.response.body", "body": body})


def _token_file_path() -> Path:
    configured = os.getenv("FRIDAY_SERVER_TOKEN_FILE", "").strip()
    if configured:
        return Path(configured).expanduser()
    local_app_data = os.getenv("LOCALAPPDATA")
    if local_app_data:
        return Path(local_app_data) / "F.R.I.D.A.Y" / "server-token"
    config_home = os.getenv("XDG_CONFIG_HOME")
    if config_home:
        return Path(config_home).expanduser() / "friday" / "server-token"
    return Path.home() / ".config" / "friday" / "server-token"


def get_server_access_token() -> str:
    configured = os.getenv("FRIDAY_SERVER_TOKEN", "").strip()
    if configured:
        if len(configured) < 32:
            raise RuntimeError("FRIDAY_SERVER_TOKEN must contain at least 32 characters.")
        return configured

    token_file = _token_file_path()
    token_file.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(
            token_file,
            os.O_CREAT | os.O_EXCL | os.O_WRONLY,
            0o600,
        )
    except FileExistsError:
        for _ in range(20):
            token = token_file.read_text(encoding="utf-8").strip()
            if token:
                if len(token) < 32:
                    raise RuntimeError("The saved F.R.I.D.A.Y. access token is invalid.")
                return token
            time.sleep(0.05)
        raise RuntimeError("The saved F.R.I.D.A.Y. access token is empty.")
    token = secrets.token_urlsafe(32)
    with os.fdopen(descriptor, "w", encoding="utf-8") as token_file_handle:
        token_file_handle.write(token)
        token_file_handle.flush()
        os.fsync(token_file_handle.fileno())
    return token


def _load_environment() -> None:
    try:
        from dotenv import load_dotenv
    except ImportError:
        return

    if os.getenv("LOCALAPPDATA"):
        config_file = Path(os.environ["LOCALAPPDATA"]) / "F.R.I.D.A.Y" / "server.env"
    elif os.getenv("XDG_CONFIG_HOME"):
        config_file = Path(os.environ["XDG_CONFIG_HOME"]) / "friday" / "server.env"
    else:
        config_file = Path.home() / ".config" / "friday" / "server.env"
    load_dotenv(config_file, override=False)


def create_app(
    provider_factory: Callable[[], AIProvider] = initialize_assistant,
    token_provider: Callable[[], str] = get_server_access_token,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.ai_provider = provider_factory()
        app.state.assistant_engine_initialized = True
        yield

    app = FastAPI(title="F.R.I.D.A.Y. Assistant API", version="1.0", lifespan=lifespan)
    app.add_middleware(RequestSizeLimitMiddleware)

    @app.exception_handler(RequestValidationError)
    async def invalid_request_handler(
        _request: Request,
        _exception: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={"status": "error", "error": "The chat request is invalid."},
        )

    def authenticate(authorization: str | None) -> JSONResponse | None:
        expected = token_provider()
        scheme, separator, supplied = (authorization or "").partition(" ")
        if (
            not separator
            or scheme.lower() != "bearer"
            or not supplied
            or not hmac.compare_digest(expected, supplied)
        ):
            return _unauthorized()
        return None

    @app.get("/health")
    def health(request: Request) -> dict[str, bool | str]:
        provider = request.app.state.ai_provider
        return {
            "server_running": True,
            "assistant_engine_initialized": request.app.state.assistant_engine_initialized,
            **provider_health(provider),
        }

    @app.post("/api/chat", response_model=ChatResponse)
    def chat(
        payload: ChatRequest,
        request: Request,
        authorization: Annotated[str | None, Header()] = None,
    ) -> ChatResponse | JSONResponse:
        authentication_error = authenticate(authorization)
        if authentication_error is not None:
            return authentication_error

        provider: AIProvider = request.app.state.ai_provider
        try:
            reply = provider.generate([message.model_dump() for message in payload.messages])
        except AIProviderError as error:
            LOGGER.warning("AI provider request failed: %s", error)
            return JSONResponse(
                status_code=503,
                content={"status": "error", "error": str(error)},
            )
        return ChatResponse(reply=reply)

    return app


def _unauthorized() -> JSONResponse:
    return JSONResponse(
        status_code=401,
        content={"status": "error", "error": "A valid bearer token is required."},
    )


app = create_app()


def main() -> None:
    _load_environment()
    parser = argparse.ArgumentParser(description="Run the F.R.I.D.A.Y. LAN assistant API.")
    parser.add_argument(
        "--show-token",
        action="store_true",
        help="Print the local Android access token; do not share it.",
    )
    arguments = parser.parse_args()
    if arguments.show_token:
        print(get_server_access_token())
        return

    try:
        import uvicorn
    except ImportError as error:
        raise SystemExit("Install backend dependencies with requirements-backend.txt.") from error

    host = os.getenv("FRIDAY_HOST", "0.0.0.0")
    try:
        port = int(os.getenv("FRIDAY_PORT", "8765"))
    except ValueError as error:
        raise SystemExit("FRIDAY_PORT must be a valid TCP port.") from error
    if not 1 <= port <= 65535:
        raise SystemExit("FRIDAY_PORT must be between 1 and 65535.")
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    _load_environment()
    main()
