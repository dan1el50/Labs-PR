#!/usr/bin/env python3
"""
HTTP File Server
Serves files from a specified directory with support for HTML, PNG, and PDF files.
Handles directory listings for nested directories.
"""

import os
import sys
import socket
from pathlib import Path
from urllib.parse import unquote


def get_content_type(file_path):
    """Determine the content type based on file extension."""
    ext = Path(file_path).suffix.lower()
    content_types = {
        '.html': 'text/html',
        '.htm': 'text/html',
        '.png': 'image/png',
        '.pdf': 'application/pdf'
    }
    return content_types.get(ext, 'application/octet-stream')


def generate_directory_listing(dir_path, url_path):
    """Generate HTML page for directory listing."""
    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Directory listing for {url_path}</title>
    <style>
        body {{
            font-family: Arial, sans-serif;
            margin: 40px;
            background-color: #f5f5f5;
        }}
        h1 {{
            border-bottom: 2px solid #333;
            padding-bottom: 10px;
        }}
        ul {{
            list-style-type: none;
            padding: 0;
        }}
        li {{
            padding: 8px;
            margin: 5px 0;
            background-color: white;
            border-radius: 4px;
        }}
        a {{
            text-decoration: none;
            color: #0066cc;
        }}
        a:hover {{
            text-decoration: underline;
        }}
        .folder {{
            font-weight: bold;
        }}
    </style>
</head>
<body>
    <h1>Directory listing for {url_path}</h1>
    <hr>
    <ul>
"""

    # Add parent directory link if not at root
    if url_path != '/':
        parent_path = '/'.join(url_path.rstrip('/').split('/')[:-1]) or '/'
        html += f'        <li class="folder"><a href="{parent_path}">📂 Parent Directory</a></li>\n'

    try:
        entries = sorted(os.listdir(dir_path))

        # Separate directories and files
        dirs = []
        files = []

        for entry in entries:
            full_path = os.path.join(dir_path, entry)
            if os.path.isdir(full_path):
                dirs.append(entry)
            else:
                files.append(entry)

        # List directories first
        for directory in dirs:
            link = f"{url_path.rstrip('/')}/{directory}/"
            html += f'        <li class="folder"><a href="{link}">📂 {directory}/</a></li>\n'

        # Then list files
        for file in files:
            link = f"{url_path.rstrip('/')}/{file}"
            html += f'        <li><a href="{link}">📜 {file}</a></li>\n'

    except Exception as e:
        html += f'        <li>Error reading directory: {str(e)}</li>\n'

    html += """    </ul>
    <hr>
</body>
</html>
"""
    return html


def handle_request(client_socket, base_dir):
    """Handle a single HTTP request."""
    try:
        # Receive the request
        request = client_socket.recv(4096).decode('utf-8')

        if not request:
            return

        # Parse the request line
        lines = request.split('\r\n')
        request_line = lines[0]
        parts = request_line.split()

        if len(parts) < 2:
            send_response(client_socket, 400, 'text/plain', b'Bad Request')
            return

        method = parts[0]
        url_path = unquote(parts[1])

        print(f"Request: {method} {url_path}")

        if method != 'GET':
            send_response(client_socket, 405, 'text/plain', b'Method Not Allowed')
            return

        # Handle root path
        if url_path == '/':
            url_path = '/index.html'
            # If index.html doesn't exist, show directory listing
            index_path = os.path.join(base_dir, 'index.html')
            if not os.path.exists(index_path):
                html = generate_directory_listing(base_dir, '/')
                send_response(client_socket, 200, 'text/html', html.encode('utf-8'))
                return

        # Remove leading slash and construct file path
        relative_path = url_path.lstrip('/')
        file_path = os.path.join(base_dir, relative_path)

        # Normalize path to prevent directory traversal attacks
        file_path = os.path.normpath(file_path)
        base_dir_abs = os.path.abspath(base_dir)
        file_path_abs = os.path.abspath(file_path)

        if not file_path_abs.startswith(base_dir_abs):
            send_response(client_socket, 403, 'text/plain', b'Forbidden')
            return

        # Check if path exists
        if not os.path.exists(file_path):
            send_response(client_socket, 404, 'text/html',
                          b'<html><body><h1>404 Not Found</h1><p>The requested file was not found.</p></body></html>')
            return

        # If it's a directory, generate listing
        if os.path.isdir(file_path):
            html = generate_directory_listing(file_path, url_path)
            send_response(client_socket, 200, 'text/html', html.encode('utf-8'))
            return

        # NEW: Check if file extension is supported
        ext = Path(file_path).suffix.lower()
        supported_extensions = ['.html', '.htm', '.png', '.pdf']

        if ext not in supported_extensions:
            send_response(client_socket, 404, 'text/html',
                          b'<html><body><h1>404 Not Found</h1><p>File type not supported. Server only supports HTML, PNG, and PDF files.</p></body></html>')
            return

        # Read and send the file
        content_type = get_content_type(file_path)

        with open(file_path, 'rb') as f:
            content = f.read()

        send_response(client_socket, 200, content_type, content)

    except Exception as e:
        print(f"Error handling request: {e}")
        try:
            send_response(client_socket, 500, 'text/plain',
                          f'Internal Server Error: {str(e)}'.encode('utf-8'))
        except:
            pass


def send_response(client_socket, status_code, content_type, body):
    """Send an HTTP response."""
    status_messages = {
        200: 'OK',
        400: 'Bad Request',
        403: 'Forbidden',
        404: 'Not Found',
        405: 'Method Not Allowed',
        500: 'Internal Server Error'
    }

    status_message = status_messages.get(status_code, 'Unknown')

    response = f"HTTP/1.1 {status_code} {status_message}\r\n"
    response += f"Content-Type: {content_type}\r\n"
    response += f"Content-Length: {len(body)}\r\n"
    response += "Connection: close\r\n"
    response += "\r\n"

    client_socket.sendall(response.encode('utf-8'))
    client_socket.sendall(body)


def main():
    if len(sys.argv) != 2:
        print("Usage: python server.py <directory>")
        sys.exit(1)

    base_dir = sys.argv[1]

    if not os.path.isdir(base_dir):
        print(f"Error: {base_dir} is not a directory")
        sys.exit(1)

    host = '0.0.0.0'
    port = 8000

    # Create socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)

        print(f"Server listening on {host}:{port}")
        print(f"Serving directory: {os.path.abspath(base_dir)}")
        print("Press Ctrl+C to stop")

        while True:
            client_socket, client_address = server_socket.accept()
            print(f"\nConnection from {client_address}")

            handle_request(client_socket, base_dir)
            client_socket.close()

    except KeyboardInterrupt:
        print("\nShutting down server...")
    finally:
        server_socket.close()


if __name__ == '__main__':
    main()
