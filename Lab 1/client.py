#!/usr/bin/env python3
"""
HTTP Client
Downloads files from the HTTP server and handles different file types.
"""

import os
import sys
import socket


def parse_http_response(response_data):
    """Parse HTTP response into headers and body."""
    try:
        # Split headers and body
        header_end = response_data.find(b'\r\n\r\n')
        if header_end == -1:
            return None, None, None

        headers_raw = response_data[:header_end].decode('utf-8', errors='ignore')
        body = response_data[header_end + 4:]

        # Parse status line
        lines = headers_raw.split('\r\n')
        status_line = lines[0]
        status_parts = status_line.split(' ', 2)

        if len(status_parts) < 2:
            return None, None, None

        status_code = int(status_parts[1])

        # Parse headers
        headers = {}
        for line in lines[1:]:
            if ':' in line:
                key, value = line.split(':', 1)
                headers[key.strip().lower()] = value.strip()

        return status_code, headers, body
    except Exception as e:
        print(f"Error parsing response: {e}")
        return None, None, None


def get_content_type(headers):
    """Extract content type from headers."""
    content_type = headers.get('content-type', '')
    return content_type.split(';')[0].strip()


def make_request(host, port, path):
    """Make an HTTP GET request."""
    try:
        # Create socket
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.settimeout(10)

        # Connect to server
        client_socket.connect((host, port))

        # Create HTTP request
        request = f"GET {path} HTTP/1.1\r\n"
        request += f"Host: {host}\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"

        # Send request
        client_socket.sendall(request.encode('utf-8'))

        # Receive response
        response_data = b''
        while True:
            chunk = client_socket.recv(4096)
            if not chunk:
                break
            response_data += chunk

        client_socket.close()

        return response_data

    except socket.timeout:
        print("Error: Connection timed out")
        return None
    except ConnectionRefusedError:
        print(f"Error: Connection refused to {host}:{port}")
        return None
    except Exception as e:
        print(f"Error making request: {e}")
        return None


def main():
    # Accept either 3 or 4 arguments
    if len(sys.argv) < 4:
        print("Usage Examples:\n"
              "python client.py localhost 8000 /file.pdf\n"
              "python client.py 172.20.10.2 8000 /document1.pdf")
        sys.exit(1)

    server_host = sys.argv[1]
    server_port = int(sys.argv[2])
    url_path = sys.argv[3]
    # Use 'downloads' as default directory if not specified
    save_dir = sys.argv[4] if len(sys.argv) > 4 else 'downloads'

    # Ensure path starts with /
    if not url_path.startswith('/'):
        url_path = '/' + url_path

    print(f"Requesting: http://{server_host}:{server_port}{url_path}")
    print(f"Save directory: {save_dir}")

    # Make the request
    response_data = make_request(server_host, server_port, url_path)

    if not response_data:
        print("Failed to get response")
        sys.exit(1)

    # Parse response
    status_code, headers, body = parse_http_response(response_data)

    if status_code is None:
        print("Failed to parse response")
        sys.exit(1)

    print(f"Status Code: {status_code}")

    if status_code != 200:
        print(f"Error: Server returned status {status_code}")
        if body:
            print(body.decode('utf-8', errors='ignore'))
        sys.exit(1)

    # Get content type
    content_type = get_content_type(headers)
    print(f"Content-Type: {content_type}")

    # Handle based on content type
    if content_type == 'text/html':
        # Print HTML content
        print("\n" + "=" * 50)
        print("HTML Content:")
        print("=" * 50)
        print(body.decode('utf-8', errors='ignore'))

    elif content_type in ['image/png', 'application/pdf']:
        # Save file
        os.makedirs(save_dir, exist_ok=True)

        # Extract filename from path
        filename = os.path.basename(url_path)
        if not filename:
            filename = 'index.html' if content_type == 'text/html' else 'download'

        filepath = os.path.join(save_dir, filename)

        with open(filepath, 'wb') as f:
            f.write(body)

        print(f"\nFile saved to: {filepath}")
        print(f"Size: {len(body)} bytes")

    else:
        print(f"Unknown content type: {content_type}")
        print("Body preview:")
        print(body[:500].decode('utf-8', errors='ignore'))


if __name__ == '__main__':
    main()
