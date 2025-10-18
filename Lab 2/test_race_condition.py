#!/usr/bin/env python3
"""
Race Condition Testing Script
Tests whether the server's counter has race conditions.
"""
import socket
import threading
import time
import re
from datetime import datetime


def make_request(host, port, path, request_num, results):
    """Make a single HTTP GET request."""
    try:
        # Create socket
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.settimeout(5)

        # Connect
        client_socket.connect((host, port))

        # Send HTTP GET request
        request = f"GET {path} HTTP/1.1\r\nHost: {host}\r\n\r\n"
        client_socket.sendall(request.encode('utf-8'))

        # Receive response
        response = b''
        while True:
            chunk = client_socket.recv(4096)
            if not chunk:
                break
            response += chunk
            if b'\r\n\r\n' in response:
                break

        client_socket.close()

        results[request_num] = {'success': True}

    except Exception as e:
        results[request_num] = {'success': False, 'error': str(e)}


def get_counter_value(host, port, base_dir_path, target_file):
    """Fetch the directory listing and extract counter for target file."""
    try:
        # Create socket
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.settimeout(5)

        # Connect
        client_socket.connect((host, port))

        # Request directory listing
        request = f"GET / HTTP/1.1\r\nHost: {host}\r\n\r\n"
        client_socket.sendall(request.encode('utf-8'))

        # Receive full response
        response = b''
        while True:
            chunk = client_socket.recv(4096)
            if not chunk:
                break
            response += chunk

        client_socket.close()

        # Parse HTML to find counter value
        html = response.decode('utf-8', errors='ignore')

        # Look for the target file and its hit count
        # Pattern: <td><a href="/filename">filename</a></td><td>123</td>
        pattern = rf'<a href="/{re.escape(target_file)}">{re.escape(target_file)}</a></td><td>(\d+)</td>'
        match = re.search(pattern, html)

        if match:
            return int(match.group(1))
        else:
            print(f"Warning: Could not find counter for {target_file} in HTML")
            return None

    except Exception as e:
        print(f"Error fetching counter: {e}")
        return None


def test_race_condition(host, port, target_file, num_requests):
    """Test for race conditions by making concurrent requests."""

    print(f"Target: http://{host}:{port}/{target_file}")
    print(f"Number of requests: {num_requests}")

    results = {}
    threads = []

    # Record start time
    start_time = time.time()

    # Create and start all threads
    print(f"Sending {num_requests} concurrent requests to /{target_file}...")
    for i in range(num_requests):
        thread = threading.Thread(
            target=make_request,
            args=(host, port, f'/{target_file}', i + 1, results)
        )
        threads.append(thread)
        thread.start()

    # Wait for all threads to complete
    for thread in threads:
        thread.join()

    end_time = time.time()
    elapsed = end_time - start_time

    # Check results
    successful = sum(1 for r in results.values() if r.get('success', False))
    failed = num_requests - successful

    print(f"Done! ({elapsed:.2f}s)")
    print(f"Successful requests: {successful}/{num_requests}")
    if failed > 0:
        print(f"Failed requests: {failed}")

    # Now fetch the counter value
    time.sleep(0.5)  # Brief pause to let server finish processing

    counter_value = get_counter_value(host, port, '.', target_file)

    print(f"\n{'=' * 60}")
    print(f"Results")
    print(f"{'=' * 60}")
    print(f"Expected counter: {num_requests}")
    if counter_value is not None:
        print(f"Actual counter: {counter_value}")
        lost_counts = num_requests - counter_value
        print(f"Lost counts: {lost_counts}")

    return counter_value


def main():
    host = 'localhost'
    port = 8000
    target_file = 'Task2.pdf'
    num_requests = 100  # Number of concurrent requests

    print("Race Condition Testing Tool")
    print("=" * 60)

    # Run the test
    counter = test_race_condition(host, port, target_file, num_requests)

if __name__ == '__main__':
    main()
