#!/usr/bin/env python3
"""Bridge between Content-Length framed stdio and line-delimited JSON stdio.

Some MCP servers (including Python SDK stdio_server) use newline-delimited JSON
messages, while some clients use Content-Length framed JSON-RPC.
This shim translates both directions.
"""

from __future__ import annotations

import re
import os
import subprocess
import sys
import threading
from typing import Optional

HEADER_RE = re.compile(br"(?im)^content-length:\s*(\d+)\s*$")


def _read_framed_message(stdin_fd: int, carry: bytearray) -> Optional[bytes]:
    while True:
        idx = carry.find(b"\r\n\r\n")
        sep_len = 4
        if idx < 0:
            idx = carry.find(b"\n\n")
            sep_len = 2

        if idx >= 0:
            header = bytes(carry[:idx])
            match = HEADER_RE.search(header)
            if not match:
                # Drop malformed header block and continue scanning.
                del carry[: idx + sep_len]
                continue

            size = int(match.group(1))
            total = idx + sep_len + size
            if len(carry) < total:
                pass
            else:
                body = bytes(carry[idx + sep_len : total])
                del carry[:total]
                return body

        chunk = os.read(stdin_fd, 4096)
        if not chunk:
            return None
        carry.extend(chunk)


def _client_to_server(child_stdin):
    carry = bytearray()
    stdin_fd = sys.stdin.fileno()
    try:
        while True:
            msg = _read_framed_message(stdin_fd, carry)
            if msg is None:
                break
            child_stdin.write(msg + b"\n")
            child_stdin.flush()
    finally:
        try:
            child_stdin.close()
        except Exception:
            pass


def _server_to_client(child_stdout):
    stdout_buf = sys.stdout.buffer
    for line in iter(child_stdout.readline, b""):
        body = line.strip()
        if not body:
            continue
        header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
        stdout_buf.write(header)
        stdout_buf.write(body)
        stdout_buf.flush()


def _stderr_passthrough(child_stderr):
    for chunk in iter(lambda: child_stderr.read(4096), b""):
        if not chunk:
            break
        sys.stderr.buffer.write(chunk)
        sys.stderr.buffer.flush()


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: mcp_frame_bridge.py <server-cmd> [args...]", file=sys.stderr)
        return 2

    child = subprocess.Popen(
        sys.argv[1:],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    assert child.stdin is not None
    assert child.stdout is not None
    assert child.stderr is not None

    t_in = threading.Thread(target=_client_to_server, args=(child.stdin,), daemon=True)
    t_out = threading.Thread(target=_server_to_client, args=(child.stdout,), daemon=True)
    t_err = threading.Thread(target=_stderr_passthrough, args=(child.stderr,), daemon=True)

    t_in.start()
    t_out.start()
    t_err.start()

    code = child.wait()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
