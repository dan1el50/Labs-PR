#!/usr/bin/env python3
"""
Concurrent Testing Script
Tests the performance difference between single-threaded and multithreaded servers.
"""
import socket
import time
import threading
from datetime import datetime


def make_request(host, port, path, request_num, results):
    """Make a single HTTP GET request and record timing."""
    start_time = time.time()

    try:
        # Create socket
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.settimeout(15)

        # Connect
        client_socket.connect((host, port))

        # Send HTTP GET request
        request = f"GET {path} HTTP/1.1\r\nHost: {host}\r\n\r\n"
        client_socket.sendall(request.encode('utf-8'))

        # Receive response (at least the headers)
        response = b''
        while True:
            chunk = client_socket.recv(4096)
            if not chunk:
                break
            response += chunk
            # For testing, we just need to receive the response
            if b'\r\n\r\n' in response:
                break

        client_socket.close()

        end_time = time.time()
        elapsed = end_time - start_time

        # Parse status code
        status_line = response.split(b'\r\n')[0].decode('utf-8', errors='ignore')
        status_code = status_line.split()[1] if len(status_line.split()) > 1 else 'Unknown'

        results[request_num] = {
            'success': True,
            'status': status_code,
            'time': elapsed,
            'start': start_time,
            'end': end_time
        }

        print(f"Request #{request_num}: Status {status_code}, Time: {elapsed:.2f}s")

    except Exception as e:
        end_time = time.time()
        elapsed = end_time - start_time

        results[request_num] = {
            'success': False,
            'error': str(e),
            'time': elapsed,
            'start': start_time,
            'end': end_time
        }

        print(f"Request #{request_num}: FAILED - {e}")


def test_server(host, port, path, num_requests, server_type):
    """Test server with concurrent requests."""
    print(f"\n{'=' * 60}")
    print(f"Testing {server_type} Server")
    print(f"{'=' * 60}")
    print(f"Making {num_requests} concurrent requests to http://{host}:{port}{path}")

    results = {}
    threads = []

    # Record overall start time
    overall_start = time.time()

    # Create and start all threads
    for i in range(num_requests):
        thread = threading.Thread(
            target=make_request,
            args=(host, port, path, i + 1, results)
        )
        threads.append(thread)
        thread.start()

    # Wait for all threads to complete
    for thread in threads:
        thread.join()

    # Record overall end time
    overall_end = time.time()
    total_time = overall_end - overall_start

    # Calculate statistics
    successful = sum(1 for r in results.values() if r['success'])
    failed = num_requests - successful

    if successful > 0:
        avg_request_time = sum(r['time'] for r in results.values() if r['success']) / successful
        min_time = min(r['time'] for r in results.values() if r['success'])
        max_time = max(r['time'] for r in results.values() if r['success'])
    else:
        avg_request_time = min_time = max_time = 0

    # Print results
    print(f"\n{'=' * 60}")
    print(f"Results for {server_type} Server")
    print(f"{'=' * 60}")
    print(f"Total time: {total_time:.2f}s")
    print(f"Successful requests: {successful}/{num_requests}")
    print(f"Failed requests: {failed}")

    return {
        'total_time': total_time,
        'successful': successful,
        'failed': failed,
    }


def main():
    host = 'localhost'
    port = 8000
    path = '/'  # Request the root directory listing
    num_requests = 10


    # Test the server
    results = test_server(host, port, path, num_requests, "Current")

if __name__ == '__main__':
    main()
