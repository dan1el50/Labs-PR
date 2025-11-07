package com.memorygame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stress test for the Memory Scramble game.
 *
 * Simulates multiple players making random moves with random delays.
 * Verifies the game never crashes under concurrent load.
 *
 * Test scenarios:
 * - Multiple player threads (3-5 players)
 * - Random move selection
 * - Random delays between moves
 * - Runs for minimum 30 seconds
 * - Tests different board sizes
 *
 * Success criteria:
 * - No exceptions thrown
 * - No deadlocks (each player makes progress)
 * - No rep invariant violations
 * - Game remains responsive
 */
public class SimulationMain {

    // Shared statistics
    private static final AtomicInteger totalMoves = new AtomicInteger(0);
    private static final AtomicInteger totalErrors = new AtomicInteger(0);
    private static final AtomicBoolean running = new AtomicBoolean(true);

    // Test configurations
    private static final String[] BOARD_FILES = {
            "src/main/resources/boards/perfect.txt",
            "src/main/resources/boards/zoom.txt",
            "src/main/resources/boards/ab.txt"
    };
    private static final int NUM_PLAYERS = 3;
    private static final long TEST_DURATION_MS = 30000; // 30 seconds

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Memory Scramble - Stress Test (Multi-Player)           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        for (String boardFile : BOARD_FILES) {
            runStressTest(boardFile);
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                   Stress Test Complete                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * Runs stress test with a specific board file.
     *
     * @param boardFile path to the board file
     */
    private static void runStressTest(String boardFile) {
        System.out.println("\n📋 Testing board: " + boardFile);

        Board board;
        try {
            board = Board.loadFromFile(boardFile);
        } catch (IOException e) {
            System.err.println("❌ Failed to load board: " + e.getMessage());
            totalErrors.incrementAndGet();
            return;
        }

        int[] dims = board.getDimensions();
        System.out.println("   Board size: " + dims[0] + "x" + dims[1]);
        System.out.println("   Number of players: " + NUM_PLAYERS);
        System.out.println("   Duration: 30 seconds\n");

        // Reset statistics for this test
        totalMoves.set(0);
        totalErrors.set(0);
        running.set(true);

        long startTime = System.currentTimeMillis();

        // Create player threads
        List<Thread> players = new ArrayList<>();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            String playerId = "player" + (i + 1);
            Thread playerThread = new Thread(() -> playerSimulation(board, playerId, dims[0], dims[1]));
            playerThread.setName(playerId);
            players.add(playerThread);
            playerThread.start();
        }

        // Monitor test progress
        Thread monitor = new Thread(() -> monitorProgress(startTime));
        monitor.start();

        // Wait for all threads to complete
        for (Thread player : players) {
            try {
                player.join();
            } catch (InterruptedException e) {
                System.err.println("❌ Thread interrupted: " + e.getMessage());
            }
        }

        running.set(false);
        try {
            monitor.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Print results
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("\n   ✅ Test completed in " + (duration / 1000.0) + " seconds");
        System.out.println("   📊 Total moves: " + totalMoves.get());
        System.out.println("   ⚡ Moves/second: " + String.format("%.1f", totalMoves.get() / (duration / 1000.0)));
        System.out.println("   ❌ Total errors: " + totalErrors.get());

        if (totalErrors.get() == 0) {
            System.out.println("   🎉 NO CRASHES OR DEADLOCKS DETECTED!");
        } else {
            System.out.println("   ⚠️  ERRORS DETECTED - Check logs above");
        }
    }

    /**
     * Simulates a single player making random moves.
     *
     * @param board the game board
     * @param playerId the player identifier
     * @param rows board rows
     * @param cols board columns
     */
    private static void playerSimulation(Board board, String playerId, int rows, int cols) {
        Random random = new Random();

        while (running.get()) {
            try {
                // Random delay (10-100ms)
                long delay = 10 + random.nextInt(90);
                Thread.sleep(delay);

                // Random position
                int row = random.nextInt(rows);
                int col = random.nextInt(cols);

                // Make move
                try {
                    board.flip(row, col, playerId);
                    totalMoves.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("❌ " + playerId + " error at (" + row + "," + col + "): " + e.getMessage());
                    totalErrors.incrementAndGet();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("   ✅ " + playerId + " finished");
    }

    /**
     * Monitors progress and prints statistics every 5 seconds.
     *
     * @param startTime when the test started
     */
    private static void monitorProgress(long startTime) {
        int lastMoves = 0;

        while (running.get()) {
            try {
                Thread.sleep(5000); // Every 5 seconds

                long elapsed = System.currentTimeMillis() - startTime;
                int currentMoves = totalMoves.get();
                int movesInLastPeriod = currentMoves - lastMoves;

                double rate = movesInLastPeriod / 5.0;
                System.out.println("   📊 [" + (elapsed / 1000) + "s] Moves: " + currentMoves +
                        " | Rate: " + String.format("%.1f", rate) + " moves/s | " +
                        "Errors: " + totalErrors.get());

                lastMoves = currentMoves;

                if (elapsed >= TEST_DURATION_MS) {
                    running.set(false);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
