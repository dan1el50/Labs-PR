#!/usr/bin/env python3
"""
Multithreaded HTTP File Server (Lab 2)
Serves files from a specified directory with support for HTML, PNG, and PDF files.
Handles directory listings for nested directories.
Uses threading to handle multiple concurrent connections.
"""
import os
import sys
import socket
import threading
import time
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
        <tr>
            <th>File / Directory</th>
        </tr>
"""

    try:
        items = sorted(os.listdir(dir_path))
        for item in items:
            item_path = os.path.join(dir_path, item)
            if os.path.isdir(item_path):
                display_name = item + '/'
            else:
                display_name = item

            # Build URL path
            if url_path.endswith('/'):
                item_url = url_path + item
            else:
                item_url = url_path + '/' + item

            html += f'        <tr><td><a href="{item_url}">{display_name}</a></td></tr>\n'
    except Exception as e:
        html += f'        <tr><td>Error reading directory: {str(e)}</td></tr>\n'

    html += """    </table>
</body>
</html>"""

    return html


def handle_client(client_socket, client_address, base_dir):
    """Handle a single client connection in a separate thread."""
    thread_id = threading.current_thread().name
    print(f"[{thread_id}] Handling connection from {client_address}")

    try:
        # Add 1 second delay to simulate work
        time.sleep(1)

        # Receive request
        request = client_socket.recv(4096).decode('utf-8', errors='ignore')

        if not request:
            return

        # Parse request line
        lines = request.split('\r\n')
        if not lines:
            send_response(client_socket, 400, 'text/plain', b'Bad Request')
            return

        request_line = lines[0].split()
        if len(request_line) < 2:
            send_response(client_socket, 400, 'text/plain', b'Bad Request')
            return

        method = request_line[0]
        url_path = unquote(request_line[1])

        print(f"[{thread_id}] {method} {url_path}")

        # Only support GET
        if method != 'GET':
            send_response(client_socket, 405, 'text/plain', b'Method Not Allowed')
            return

        # Construct file path
        if url_path == '/':
            file_path = base_dir
        else:
            # Remove leading slash and join with base_dir
            relative_path = url_path.lstrip('/')
            file_path = os.path.join(base_dir, relative_path)

        # Normalize path and check if it's within base_dir
        file_path = os.path.normpath(file_path)
        base_dir_abs = os.path.abspath(base_dir)
        file_path_abs = os.path.abspath(file_path)

        if not file_path_abs.startswith(base_dir_abs):
            send_response(client_socket, 403, 'text/html',
                          b'<!DOCTYPE html><html><body><h1>403 Forbidden</h1><p>Access denied.</p></body></html>')
            return

        # Check if path exists
        if not os.path.exists(file_path):
            send_response(client_socket, 404, 'text/html',
                          b'<!DOCTYPE html><html><body><h1>404 Not Found</h1><p>The requested file was not found.</p></body></html>')
            return

        # If it's a directory, generate listing
        if os.path.isdir(file_path):
            html = generate_directory_listing(file_path, url_path)
            send_response(client_socket, 200, 'text/html', html.encode('utf-8'))
            return

        # Check if file extension is supported
        ext = Path(file_path).suffix.lower()
        supported_extensions = ['.html', '.htm', '.png', '.pdf']

        if ext not in supported_extensions:
            send_response(client_socket, 404, 'text/html',
                          b'<!DOCTYPE html><html><body><h1>404 Not Found</h1><p>File type not supported. Server only supports HTML, PNG, and PDF files.</p></body></html>')
            return

        # Read and send the file
        content_type = get_content_type(file_path)
        with open(file_path, 'rb') as f:
            content = f.read()

        send_response(client_socket, 200, content_type, content)

    except Exception as e:
        print(f"[{thread_id}] Error handling request: {e}")
        try:
            send_response(client_socket, 500, 'text/plain',
                          f'Internal Server Error: {str(e)}'.encode('utf-8'))
        except:
            pass
    finally:
        client_socket.close()
        print(f"[{thread_id}] Connection closed for {client_address}")


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
        print("Usage: python server_multithreaded.py <directory>")
        sys.exit(1)

    base_dir = sys.argv[1]

    if not os.path.isdir(base_dir):
        print(f"Error: {base_dir} is not a valid directory")
        sys.exit(1)

    host = '0.0.0.0'
    port = 8000

    # Create socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((host, port))
    server_socket.listen(10)

    print(f"Multithreaded HTTP Server running on http://{host}:{port}")
    print(f"Serving files from: {os.path.abspath(base_dir)}")
    print("Press Ctrl+C to stop the server")

    try:
        while True:
            # Accept connection
            client_socket, client_address = server_socket.accept()

            # Create and start a new thread for this connection
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, client_address, base_dir),
                daemon=True
            )
            client_thread.start()

    except KeyboardInterrupt:
        print("\nShutting down server...")
    finally:
        server_socket.close()


if __name__ == '__main__':
    main()
