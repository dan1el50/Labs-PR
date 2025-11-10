package com.memorygame.server;

import com.memorygame.Board;
import com.memorygame.commands.Commands;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * HTTP server for the Memory Scramble game.
 *
 * Handles concurrent requests from multiple players.
 * Only calls methods from the Commands module.
 *
 * Design:
 * - Uses HttpServer with cached thread pool for concurrent requests
 * - Each request is handled in a separate thread
 * - Board synchronization happens at the Commands/Board level
 * - Safe from rep exposure: only exposes HTTP interface
 */
public class GameServer {
    private final HttpServer server;
    private final Commands commands;

    /**
     * Constructs the game server.
     *
     * Precondition:
     * - port is a valid port number
     * - board is not null
     *
     * Postcondition:
     * - Server is created but not started
     *
     * @param port the port to listen on
     * @param board the game board
     * @throws IOException if server cannot be created
     */
    public GameServer(int port, Board board) throws IOException {
        this.commands = new Commands(board);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Set up thread pool for handling concurrent requests
        server.setExecutor(Executors.newCachedThreadPool());

        // Register endpoints
        server.createContext("/", new FileHandler("src/main/resources/index.html"));
        server.createContext("/look", new LookHandler());
        server.createContext("/flip", new FlipHandler());
        server.createContext("/reset", new ResetHandler());
        server.createContext("/watch", new WatchHandler());

    }

    /**
     * Starts the server and begins accepting requests.
     *
     * Postcondition:
     * - Server is listening on the configured port
     */
    public void start() {
        server.start();
        System.out.println("Game server started on port 8000");
    }

    /**
     * Stops the server.
     *
     * Postcondition:
     * - Server stops accepting new requests
     */
    public void stop() {
        server.stop(0);
    }

    /**
     * Handler for serving static HTML files.
     */
    private class FileHandler implements HttpHandler {
        private final String filePath;

        FileHandler(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("/".equals(exchange.getRequestURI().getPath())) {
                    byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                System.err.println("Error serving file: " + e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    /**
     * Handler for /reset/:player requests.
     *
     * Path format: /reset/PLAYER_ID
     * Resets the board to initial state.
     */
    private class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                String player = parts.length > 2 ? parts[2] : "";

                String response = commands.reset(player);

                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                String error = "ERROR: " + e.getMessage();
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(400, error.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    /**
     * Handler for /look/:player requests.
     *
     * Path format: /look/PLAYER_ID
     * Returns the board state visible to the player.
     */
    private class LookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                String player = parts.length > 2 ? parts[2] : "";

                String response = commands.look(player);

                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                String error = "ERROR: " + e.getMessage();
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(400, error.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    /**
     * Handler for /flip/:player/:row,:col requests.
     *
     * Path format: /flip/PLAYER_ID/ROW,COL
     * Flips the card and returns updated board state.
     */
    private class FlipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                String player = parts.length > 2 ? parts[2] : "";
                String coords = parts.length > 3 ? parts[3] : "";
                String[] rc = coords.split(",");

                int row = Integer.parseInt(rc[0]);
                int col = Integer.parseInt(rc[1]);

                String response = commands.flip(player, row, col);

                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                String error = "ERROR: " + e.getMessage();
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(400, error.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }
    }

    private class WatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                String player = parts.length > 2 ? parts[2] : "";
                String lastState = "";

                // Allow previous board state to be sent as a query parameter ?lastState=...
                if (exchange.getRequestURI().getQuery() != null) {
                    String query = exchange.getRequestURI().getQuery();
                    String[] queries = query.split("&");
                    for (String q : queries) {
                        if (q.startsWith("lastState=")) {
                            lastState = java.net.URLDecoder.decode(q.substring(10), "UTF-8");
                        }
                    }
                }

                String response = commands.watch(player, lastState);

                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes("UTF-8"));
                os.close();
            } catch (Exception e) {
                String error = "ERROR: " + e.getMessage();
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(400, error.getBytes("UTF-8").length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes("UTF-8"));
                os.close();
            }
        }
    }


    /**
     * Main entry point to start the server.
     */
    public static void main(String[] args) throws IOException {
        Board board;

        // Choose which board to load (change this to test different boards)
        String boardFile = args.length > 0 ? args[0] : "ab.txt";

        try {
            board = Board.loadFromFile("src/main/resources/boards/" + boardFile);
            System.out.println("✓ Loaded board: " + boardFile);
        } catch (IOException e) {
            System.out.println("✗ Could not load board file: " + e.getMessage());
            System.out.println("Using default board instead...");

            // Fallback: create default board if file not found
            List<String> cards = new ArrayList<>();
            cards.add("🦄");
            cards.add("🦄");
            cards.add("🌈");
            cards.add("🌈");
            cards.add("🌈");
            cards.add("🦄");
            cards.add("🌈");
            cards.add("🦄");
            cards.add("🌈");
            board = new Board(3, 3, cards);
        }

        GameServer server = new GameServer(8000, board);
        server.start();
        System.out.println("Server running on http://localhost:8000");
    }
}
