#!/usr/bin/env python3
"""
Unified HTTP File Server with multiple modes:
- single: single-threaded server
- multi: multithreaded server without counter
- race: multithreaded with naive counter (race condition)
- threadsafe: multithreaded with thread-safe counter (lock)
- ratelimit: threadsafe counter + IP rate limiting (limit ~5 req/s)

Change SERVER_MODE below to switch mode.
"""

import os
import sys
import socket
import threading
import time
from pathlib import Path
from urllib.parse import unquote
from collections import defaultdict
from datetime import datetime

# === CONFIGURE SERVER MODE HERE ===
# Change this to: "single", "multi", "race", "threadsafe", "ratelimit"
SERVER_MODE = "ratelimit"

# === Globals Used by All Modes ===

# For counter modes
file_hits = defaultdict(int)
counter_lock = threading.Lock()

# For rate limiting (ratelimit mode)
rate_limit_lock = threading.Lock()
rate_limits = defaultdict(list)  # {ip: [timestamp, ...]}


def get_content_type(file_path):
    ext = Path(file_path).suffix.lower()
    content_types = {
        '.html': 'text/html',
        '.htm': 'text/html',
        '.png': 'image/png',
        '.pdf': 'application/pdf'
    }
    return content_types.get(ext, 'application/octet-stream')


def generate_directory_listing(dir_path, url_path):
    html = f"""<!DOCTYPE html>
<html>
<head>
<title>Directory listing for {url_path}</title>
<style>
body {{ font-family: Arial, sans-serif; margin: 20px; }}
h1 {{ border-bottom: 1px solid #ccc; }}
table {{ border-collapse: collapse; width: 100%; }}
th, td {{ text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }}
th {{ background-color: #f2f2f2; }}
a {{ text-decoration: none; color: #0066cc; }}
a:hover {{ text-decoration: underline; }}
</style>
</head>
<body>
<h1>Directory listing for {url_path}</h1>
<table>
<tr><th>File / Directory</th><th>Hits</th></tr>
"""

    try:
        items = sorted(os.listdir(dir_path))
        for item in items:
            fpath = os.path.join(dir_path, item)
            if os.path.isdir(fpath):
                display_name = item + '/'
            else:
                display_name = item

            if url_path.endswith('/'):
                item_url = url_path + item
            else:
                item_url = url_path + '/' + item

            # Show hits only in applicable modes
            hits = ''
            if SERVER_MODE in ["race", "threadsafe", "ratelimit"]:
                with counter_lock:
                    hits = file_hits.get(fpath, 0)

            html += f'<tr><td><a href="{item_url}">{display_name}</a></td><td>{hits}</td></tr>\n'
    except Exception as e:
        html += f'<tr><td colspan="2">Error reading directory: {str(e)}</td></tr>\n'

    html += "</table></body></html>"

    return html


def rate_limited(ip_addr):
    # Clean timestamps older than 1s
    now = datetime.now().timestamp()
    with rate_limit_lock:
        timestamps = rate_limits[ip_addr]
        rate_limits[ip_addr] = [ts for ts in timestamps if now - ts < 1]

        if len(rate_limits[ip_addr]) >= 5:
            return True

        rate_limits[ip_addr].append(now)
        return False


def handle_client(client_socket, client_address, base_dir):
    thread_name = threading.current_thread().name
    print(f"[{thread_name}] {client_address} connected")

    try:
        request = client_socket.recv(4096).decode("utf-8", errors="ignore")
        if not request:
            return

        lines = request.split("\r\n")
        if not lines:
            send_response(client_socket, 400, "text/plain", b"Bad Request")
            return

        request_line = lines[0].split()
        if len(request_line) < 2:
            send_response(client_socket, 400, "text/plain", b"Bad Request")
            return

        method, url_path = request_line[0], unquote(request_line[1])

        # Rate limit check before any processing (ratelimit mode only)
        if SERVER_MODE == "ratelimit":
            ip = client_address[0]
            if rate_limited(ip):
                send_response(client_socket, 429, "text/plain", b"Too Many Requests")
                print(f"[{thread_name}] 429 Too Many Requests from {ip}")
                return

        if method != "GET":
            send_response(client_socket, 405, "text/plain", b"Method Not Allowed")
            return

        if url_path == "/":
            file_path = base_dir
        else:
            relative_path = url_path.lstrip("/")
            file_path = os.path.join(base_dir, relative_path)

        file_path = os.path.normpath(file_path)
        base_dir_abs = os.path.abspath(base_dir)
        file_path_abs = os.path.abspath(file_path)

        if not file_path_abs.startswith(base_dir_abs):
            send_response(client_socket, 403, "text/html", b"<!DOCTYPE html><html><body><h1>403 Forbidden</h1><p>Access denied.</p></body></html>")
            return

        if not os.path.exists(file_path):
            send_response(client_socket, 404, "text/html", b"<!DOCTYPE html><html><body><h1>404 Not Found</h1><p>File not found.</p></body></html>")
            return

        # Single-threaded mode process one request at a time with delay
        if SERVER_MODE == "single":
            time.sleep(1)

            # No concurrency, no counters or rate limits
            if os.path.isdir(file_path):
                html = generate_directory_listing(file_path, url_path)
                send_response(client_socket, 200, "text/html", html.encode("utf-8"))
                return

            ext = Path(file_path).suffix.lower()
            if ext not in [".html", ".htm", ".png", ".pdf"]:
                send_response(client_socket, 404, "text/html", b"File type not supported")
                return

            content_type = get_content_type(file_path)
            with open(file_path, "rb") as f:
                content = f.read()
            send_response(client_socket, 200, content_type, content)
            return

        # Multithreaded without counter (multi mode)
        if SERVER_MODE == "multi":
            time.sleep(1)  # simulate work

            if os.path.isdir(file_path):
                html = generate_directory_listing(file_path, url_path)
                send_response(client_socket, 200, "text/html", html.encode("utf-8"))
                return

            ext = Path(file_path).suffix.lower()
            if ext not in [".html", ".htm", ".png", ".pdf"]:
                send_response(client_socket, 404, "text/html", b"File type not supported")
                return

            content_type = get_content_type(file_path)
            with open(file_path, "rb") as f:
                content = f.read()
            send_response(client_socket, 200, content_type, content)
            return

        # Race condition counter (naive)
        if SERVER_MODE == "race":
            current_count = file_hits.get(file_path, 0)
            time.sleep(0.001)
            file_hits[file_path] = current_count + 1

        # Thread-safe counter
        if SERVER_MODE == "threadsafe" or SERVER_MODE == "ratelimit":
            with counter_lock:
                current_count = file_hits.get(file_path, 0)
                time.sleep(0.001)
                file_hits[file_path] = current_count + 1

        if os.path.isdir(file_path):
            html = generate_directory_listing(file_path, url_path)
            send_response(client_socket, 200, "text/html", html.encode("utf-8"))
            return

        ext = Path(file_path).suffix.lower()
        if ext not in [".html", ".htm", ".png", ".pdf"]:
            send_response(client_socket, 404, "text/html", b"File type not supported")
            return

        content_type = get_content_type(file_path)
        with open(file_path, "rb") as f:
            content = f.read()
        send_response(client_socket, 200, content_type, content)

    except Exception as e:
        print(f"[{thread_name}] Error: {e}")
        try:
            send_response(client_socket, 500, "text/plain", f"Internal Server Error: {e}".encode("utf-8"))
        except:
            pass
    finally:
        client_socket.close()
        print(f"[{thread_name}] Connection closed")


def send_response(client_socket, status_code, content_type, body):
    messages = {
        200: "OK",
        400: "Bad Request",
        403: "Forbidden",
        404: "Not Found",
        405: "Method Not Allowed",
        429: "Too Many Requests",
        500: "Internal Server Error",
    }
    status_message = messages.get(status_code, "Unknown")
    response = f"HTTP/1.1 {status_code} {status_message}\r\n"
    response += f"Content-Type: {content_type}\r\n"
    response += f"Content-Length: {len(body)}\r\n"
    response += "Connection: close\r\n"
    response += "\r\n"
    client_socket.sendall(response.encode("utf-8"))
    client_socket.sendall(body)


def run_single_threaded(base_dir):
    host, port = "0.0.0.0", 8000
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((host, port))
    server_socket.listen(5)
    print(f"Single-threaded HTTP Server running on http://{host}:{port}")
    print(f"Serving files from: {os.path.abspath(base_dir)}")

    try:
        while True:
            client_socket, client_address = server_socket.accept()
            handle_client(client_socket, client_address, base_dir)
    except KeyboardInterrupt:
        print("\nShutting down...")
    finally:
        server_socket.close()


def run_multithreaded(base_dir):
    host, port = "0.0.0.0", 8000
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((host, port))
    server_socket.listen(5)
    print(f"Multithreaded HTTP Server running on http://{host}:{port}")
    print(f"Serving files from: {os.path.abspath(base_dir)}")

    try:
        while True:
            client_socket, client_address = server_socket.accept()
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, client_address, base_dir),
                daemon=True,
            )
            client_thread.start()
    except KeyboardInterrupt:
        print("\nShutting down...")
    finally:
        server_socket.close()


def main():
    if len(sys.argv) != 2:
        print("Usage: python server.py <directory>")
        sys.exit(1)

    base_dir = sys.argv[1]

    if not os.path.isdir(base_dir):
        print(f"Error: {base_dir} is not a valid directory")
        sys.exit(1)

    if SERVER_MODE == "single":
        run_single_threaded(base_dir)
    else:
        run_multithreaded(base_dir)


if __name__ == "__main__":
    main()
