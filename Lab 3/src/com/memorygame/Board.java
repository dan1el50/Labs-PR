package com.memorygame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable Board ADT for the Memory Scramble game.
 *
 * THREAD SAFETY ARGUMENT
 * ======================
 *
 * This class is thread-safe using the MONITOR PATTERN with bounded waiting.
 *
 * What Threads Exist:
 * - Multiple HTTP server threads (from GameServer's thread pool)
 * - Each thread handles one client request (flip, look, reset)
 * - Threads are created by HttpServer's cached thread pool
 *
 * What Data Is Accessed:
 * - Mutable shared data: board[][], state[][], playerControl, playerFirstCard
 * - Immutable data: rows, cols (final fields)
 *
 * Thread Safety Strategy:
 *
 * 1. SYNCHRONIZATION (Monitor Pattern):
 *    - ALL public methods are synchronized on the Board instance (this)
 *    - This creates a monitor that ensures mutual exclusion
 *    - Only ONE thread can execute ANY public method at a time
 *    - Prevents race conditions on board[][], state[][]
 *
 * 2. BOUNDED WAITING (wait/notifyAll):
 *    - flip() uses wait(50) when card is controlled by another player
 *    - Timeout prevents deadlock if another player abandons their move
 *    - notifyAll() wakes waiting threads when state changes
 *    - All wait() calls are inside synchronized methods (required by Java)
 *
 * 3. THREADSAFE DATA TYPES (Optional Reinforcement):
 *    - playerControl and playerFirstCard use ConcurrentHashMap
 *    - This is redundant with synchronized but adds defense-in-depth
 *    - Even if called outside synchronized context, maps are safe
 *
 * 4. IMMUTABILITY:
 *    - rows and cols are final - never change after construction
 *    - Safe to read without synchronization (getDimensions())
 *
 * Why This Is Safe:
 *
 * - NO DATA RACES: synchronized ensures only one thread modifies state at a time
 * - NO DEADLOCK: wait() has timeout; no circular dependencies
 * - NO STARVATION: notifyAll() wakes ALL waiting threads fairly
 * - REP INVARIANT PRESERVED: checkRep() called at entry/exit of all mutators
 * - NO REP EXPOSURE: All methods return immutable Strings or primitive arrays
 *
 * Blocking Behavior (Rule 1-D):
 * - First flip on controlled card: blocks up to 50ms
 * - If still controlled after timeout, gives up (returns ERROR)
 * - This implements "try to flip" semantics without indefinite blocking
 *
 * Alternative Strategies Considered:
 * - Message passing: Would require separate thread pool and queues (overkill)
 * - Fine-grained locking: Would require locks per card (complex, error-prone)
 * - Lock-free: Would require complex CAS operations (unnecessary)
 *
 * The monitor pattern is the simplest correct approach for this problem.
 */

public class Board {
    private final int rows;
    private final int cols;
    private final String[][] board; // Card identifiers (null if no card)
    private final CardState[][] state; // Card states
    private final Map<String, String> playerControl; // Maps "row,col" -> playerId
    private final Map<String, String> playerFirstCard; // Maps playerId -> "row,col"

    /**
     * Constructs a Board with given dimensions and card layout.
     *
     * Precondition:
     * - rows > 0 and cols > 0
     * - cards.size() == rows * cols
     * - cards do not contain null elements
     *
     * Postcondition:
     * - Board is initialized with all cards face-down
     * - No player controls any card
     *
     * @param rows the number of rows (must be positive)
     * @param cols the number of columns (must be positive)
     * @param cards the card layout in row-major order (left to right, top to bottom)
     * @throws IllegalArgumentException if rows or cols is non-positive
     * @throws IllegalArgumentException if cards.size() != rows * cols
     */
    public Board(int rows, int cols, List<String> cards) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Rows and cols must be positive");
        }
        if (cards.size() != rows * cols) {
            throw new IllegalArgumentException("Cards count must equal rows * cols");
        }

        this.rows = rows;
        this.cols = cols;
        this.board = new String[rows][cols];
        this.state = new CardState[rows][cols];
        this.playerControl = new ConcurrentHashMap<>();
        this.playerFirstCard = new ConcurrentHashMap<>();

        // Initialize board from cards list
        int idx = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String card = cards.get(idx++);
                if (!card.equals("none")) {
                    board[i][j] = card;
                    state[i][j] = CardState.FACE_DOWN;
                } else {
                    board[i][j] = null;
                    state[i][j] = CardState.NONE;
                }
            }
        }
        checkRep();
    }

    /**
     * Loads a board from a file.
     *
     * File format:
     * ```
     * 3x3
     * 🦄
     * 🦄
     * 🌈
     * ...
     * ```
     *
     * Precondition:
     * - filepath points to valid readable file with correct format
     *
     * Postcondition:
     * - Returns new Board instance loaded from file
     *
     * @param filepath path to the board file
     * @return a new Board instance loaded from file
     * @throws IOException if file cannot be read
     */
    public static Board loadFromFile(String filepath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filepath));
        String[] dims = lines.get(0).split("x");
        int rows = Integer.parseInt(dims[0]);
        int cols = Integer.parseInt(dims[1]);
        List<String> cards = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String card = lines.get(i).trim();
            if (!card.isEmpty()) {
                cards.add(card);
            }
        }

        return new Board(rows, cols, cards);
    }

    /**
     * Returns the dimensions of the board.
     *
     * Precondition:
     * - None (always safe to call)
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns array [rows, cols] where rows > 0 and cols > 0
     *
     * Thread Safety:
     * This method returns immutable primitives, so it's thread-safe
     * even though it's not synchronized.
     *
     * @return array containing [rows, cols]
     */
    public int[] getDimensions() {
        return new int[]{rows, cols};
    }

    /**
     * Returns the card identifier at the specified position.
     *
     * Precondition:
     * - 0 <= row < rows
     * - 0 <= col < cols
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns card identifier (e.g., "🦄") or null if no card exists
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     *
     * @param row the row index
     * @param col the column index
     * @return card identifier or null if position is empty (NONE)
     */
    public synchronized String getCard(int row, int col) {
        return board[row][col];
    }

    /**
     * Returns the state of the card at the specified position.
     *
     * Precondition:
     * - 0 <= row < rows
     * - 0 <= col < cols
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns one of: NONE, FACE_DOWN, FACE_UP_CONTROLLED, FACE_UP_UNCONTROLLED
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     *
     * @param row the row index
     * @param col the column index
     * @return the CardState at this position
     */
    public synchronized CardState getState(int row, int col) {
        return state[row][col];
    }

    /**
     * Returns the player ID controlling the card at this position, or null.
     *
     * Precondition:
     * - 0 <= row < rows
     * - 0 <= col < cols
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns player ID string if card is controlled, null otherwise
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     * ConcurrentHashMap ensures thread-safe map access.
     *
     * @param row the row index
     * @param col the column index
     * @return player ID controlling this card, or null if uncontrolled
     */
    public synchronized String getController(int row, int col) {
        return playerControl.get(row + "," + col);
    }

    /**
     * Flips a card at position (row, col) by the given player.
     *
     * Implements all game rules for flipping with timeout-based blocking.
     *
     * Precondition:
     * - 0 <= row < rows
     * - 0 <= col < cols
     * - playerId is a non-null, non-empty string
     *
     * Postcondition:
     * - Card state is updated according to game rules
     * - Returns "ERROR: ..." if invalid position
     * - Returns "OK" if successful
     * - Rep invariant is maintained
     *
     * Thread Safety:
     * Synchronized method with timeout-based blocking for controlled cards.
     *
     * @param row the row index (0-based)
     * @param col the column index (0-based)
     * @param playerId the player ID
     * @return "OK" if successful, "ERROR: message" if invalid
     */
    public synchronized String flip(int row, int col, String playerId) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return "ERROR: Invalid position (" + row + "," + col + ")";
        }

        checkRep();
        String position = row + "," + col;
        String firstCardPos = playerFirstCard.get(playerId);

        // ==================== FIRST FLIP ====================
        if (firstCardPos == null) {
            // Rule 1-A: No card exists
            if (state[row][col] == CardState.NONE) {
                checkRep();
                return "ERROR: No card at position (" + row + "," + col + ")";
            }

            // Rule 1-D: Card controlled by another player → TRY WITH TIMEOUT
            String controller = playerControl.get(position);
            if (controller != null && !controller.equals(playerId)) {
                // Try to wait for up to 50ms for the card to be released
                try {
                    this.wait(50); // TIMEOUT: Don't wait forever
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    checkRep();
                    return "ERROR: Interrupted while waiting";
                }

                // After timeout, re-check
                controller = playerControl.get(position);
                if (controller != null && !controller.equals(playerId)) {
                    // Still controlled, give up this move
                    checkRep();
                    return "ERROR: Card controlled by another player";
                }
            }

            // Rule 1-B: Face-down → turn face-up, player controls
            if (state[row][col] == CardState.FACE_DOWN) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                this.notifyAll();
                checkRep();
                return "OK";
            }

            // Rule 1-C: Face-up uncontrolled → player takes control
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                this.notifyAll();
                checkRep();
                return "OK";
            }

            // Already controlled by this player
            if (state[row][col] == CardState.FACE_UP_CONTROLLED) {
                if (playerId.equals(controller)) {
                    playerFirstCard.put(playerId, position);
                    checkRep();
                    return "OK";
                }
            }
        }

        // ==================== SECOND FLIP ====================
        else {
            String[] firstPos = firstCardPos.split(",");
            int firstRow = Integer.parseInt(firstPos[0]);
            int firstCol = Integer.parseInt(firstPos[1]);

            // First card was somehow removed, reset
            if (state[firstRow][firstCol] == CardState.NONE) {
                playerFirstCard.remove(playerId);
                checkRep();
                return "ERROR: First card was removed";
            }

            String firstCard = board[firstRow][firstCol];

            // Rule 2-A: No card exists
            if (state[row][col] == CardState.NONE) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return "ERROR: No card at position (" + row + "," + col + ")";
            }

            // Rule 2-B: Card controlled by someone
            String cardController = playerControl.get(position);
            if (cardController != null && state[row][col] == CardState.FACE_UP_CONTROLLED) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return "ERROR: Card controlled by another player";
            }

            // Rule 2-C: Face-down → turn face-up
            if (state[row][col] == CardState.FACE_DOWN) {
                state[row][col] = CardState.FACE_UP_UNCONTROLLED;
                String secondCard = board[row][col];

                // Rule 2-D: Cards match → both controlled by player
                if (firstCard.equals(secondCard)) {
                    state[firstRow][firstCol] = CardState.FACE_UP_CONTROLLED;
                    state[row][col] = CardState.FACE_UP_CONTROLLED;
                    playerControl.put(firstCardPos, playerId);
                    playerControl.put(position, playerId);
                    playerFirstCard.remove(playerId);
                    this.notifyAll();
                    checkRep();
                    return "OK";
                }
                // Rule 2-E: Cards don't match → both uncontrolled
                else {
                    state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                    state[row][col] = CardState.FACE_UP_UNCONTROLLED;
                    playerControl.remove(firstCardPos);
                    playerFirstCard.remove(playerId);
                    this.notifyAll();
                    checkRep();
                    return "OK";
                }
            }

            // Already face-up uncontrolled → treat as no match
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return "OK";
            }
        }

        checkRep();
        return "OK";
    }


    /**
     * Removes matched pairs controlled by this player.
     * Called before next move to cleanup previous matches.
     *
     * Rule 3-A: Matched pairs are removed from board
     *
     * @param playerId the player ID
     */
    public synchronized void removeMatchedPairs(String playerId) {
        checkRep();

        List<String> toRemove = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String position = i + "," + j;
                String controller = playerControl.get(position);

                // If player controls this card, mark for removal
                if (playerId.equals(controller) && state[i][j] == CardState.FACE_UP_CONTROLLED) {
                    toRemove.add(position);
                }
            }
        }

        // Remove the matched pairs
        for (String pos : toRemove) {
            String[] parts = pos.split(",");
            int i = Integer.parseInt(parts[0]);
            int j = Integer.parseInt(parts[1]);

            board[i][j] = null;
            state[i][j] = CardState.NONE;
            playerControl.remove(pos);  // CRITICAL: Remove from control map
        }

        checkRep();
    }

    /**
     * Prepares board for player's next move.
     * Removes matched pairs and turns unmatched cards face-down.
     *
     * Rule 3-A: Remove matched pairs
     * Rule 3-B: Unmatched face-up cards turn face-down
     *
     * @param playerId the player ID
     */
    public synchronized void prepareForNextMove(String playerId) {
        checkRep();

        // First remove matched pairs
        removeMatchedPairs(playerId);

        // Then turn uncontrolled face-up cards face-down
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (state[i][j] == CardState.FACE_UP_UNCONTROLLED) {
                    state[i][j] = CardState.FACE_DOWN;
                }
            }
        }

        checkRep();
    }

    /**
     * Checks if a player has a first card waiting for a second flip.
     *
     * This is used by Commands to determine if prepareForNextMove()
     * should be called before a flip operation.
     *
     * Precondition:
     * - playerId is a non-empty string
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns true if player has made their first flip and is waiting
     *   for second flip, false otherwise
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     *
     * @param playerId the player ID to check
     * @return true if player has a first card pending, false otherwise
     */
    public synchronized boolean hasFirstCard(String playerId) {
        return playerFirstCard.containsKey(playerId);
    }

    /**
     * Returns the board state visible to a player.
     *
     * Format follows MIT spec:
     * - First line: "ROWSxCOLS"
     * - Following lines: one card state per line (row-major order)
     * - States: "none", "down", "up CARD", "my CARD"
     *
     * Example output for 3x3 board:
     * 3x3
     * down
     * down
     * my 🦄
     * down
     * up 🌈
     * down
     * none
     * down
     * down
     *
     * Precondition:
     * - playerId is a non-null, non-empty string
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns string representation conforming to MIT spec
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     *
     * @param playerId the player ID (used to distinguish "my" vs "up" cards)
     * @return string representation of board state
     */
    public synchronized String look(String playerId) {
        checkRep();
        StringBuilder sb = new StringBuilder();

        // First line: dimensions
        sb.append(rows).append("x").append(cols).append("\n");

        // Each card on its own line, row by row, left to right
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String position = i + "," + j;
                CardState cardState = state[i][j];

                if (cardState == CardState.NONE) {
                    sb.append("none");
                } else if (cardState == CardState.FACE_DOWN) {
                    sb.append("down");
                } else if (cardState == CardState.FACE_UP_CONTROLLED) {
                    String controller = playerControl.get(position);
                    if (controller != null && controller.equals(playerId)) {
                        sb.append("my ").append(board[i][j]);
                    } else {
                        sb.append("up ").append(board[i][j]);
                    }
                } else if (cardState == CardState.FACE_UP_UNCONTROLLED) {
                    sb.append("up ").append(board[i][j]);
                }
                sb.append("\n");
            }
        }

        checkRep();
        return sb.toString();
    }


    /**
     * Resets the board to initial state (all cards face-down).
     *
     * Used for starting a fresh game without creating a new Board instance.
     *
     * Precondition:
     * - None (always safe to call)
     *
     * Postcondition:
     * - All cards that exist (not NONE) are turned face-down
     * - All player control is cleared (playerControl is empty)
     * - All pending first cards are cleared (playerFirstCard is empty)
     * - Rep invariant is maintained
     *
     * Thread Safety:
     * Synchronized method - safe for concurrent access.
     * Should typically be called when no active games are in progress
     * to avoid disrupting ongoing games.
     *
     * Effects:
     * - Resets board to fresh game state
     * - Notifies waiting threads (via checkRep)
     */
    public synchronized void resetBoard() {
        checkRep();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (state[i][j] != CardState.NONE) {
                    state[i][j] = CardState.FACE_DOWN;
                }
            }
        }

        playerControl.clear();
        playerFirstCard.clear();

        checkRep();
    }

    /**
     * Checks representation invariants.
     *
     * Invariants:
     * - rows > 0, cols > 0
     * - board dimensions match rows x cols
     * - state dimensions match rows x cols
     * - playerControl only has entries for FACE_UP_CONTROLLED cards
     * - No duplicate control entries
     * - playerFirstCard keys are non-empty
     */
    /**
     * Checks representation invariants.
     */
    private void checkRep() {
        assert rows > 0 && cols > 0 : "Invalid dimensions";
        assert board.length == rows : "Board rows mismatch";
        assert state.length == rows : "State rows mismatch";

        for (int i = 0; i < rows; i++) {
            assert board[i].length == cols : "Board cols mismatch at row " + i;
            assert state[i].length == cols : "State cols mismatch at row " + i;
        }

        // Verify playerControl consistency
        Set<String> seenCards = new HashSet<>();  // Track cards (not players)
        for (String pos : playerControl.keySet()) {
            String[] parts = pos.split(",");
            int i = Integer.parseInt(parts[0]);
            int j = Integer.parseInt(parts[1]);

            // A controlled card must exist and be FACE_UP_CONTROLLED
            assert state[i][j] == CardState.FACE_UP_CONTROLLED :
                    "Card at " + pos + " is " + state[i][j] + " but in playerControl map";

            // No duplicate position entries
            assert !seenCards.contains(pos) : "Duplicate control entry for " + pos;
            seenCards.add(pos);
        }

        // Verify first cards exist
        for (String playerId : playerFirstCard.keySet()) {
            assert !playerId.isEmpty() : "Player ID must not be empty";
        }
    }

    /**
     * Blocks until the visible board state for playerId changes from lastState, or a timeout elapses.
     * Used for "watch" real-time updates.
     *
     * @param playerId the player to watch for visible changes
     * @param lastState the board state previously seen
     * @return new board state string (may be same as lastState if timeout)
     */
    public synchronized String waitForChange(String playerId, String lastState) {
        final long timeout = 20000; // 20 seconds
        final long interval = 250;  // check every 250ms
        long start = System.currentTimeMillis();

        try {
            while (look(playerId).equals(lastState)) {
                long elapsed = System.currentTimeMillis() - start;
                long remaining = timeout - elapsed;
                if (remaining <= 0) break;
                wait(Math.min(interval, remaining));
            }
        } catch (InterruptedException e) {
            // If interrupted, return current state
            Thread.currentThread().interrupt();
        }
        // Always return the latest state (may be same as lastState if timeout/interrupt)
        return look(playerId);
    }

}

/**
 * ============================================================================
 * REFLECTION ON DESIGN, TESTING, AND EXPERIENCE
 * (for MIT 6.102 PS4, Problem 5)
 * ============================================================================
 *
 * DESIGN CHOICES:
 * - Used the Monitor pattern (synchronized methods and notify/wait) for simplicity and robustness.
 * - Kept all board state and player mappings internal and private, never exposing mutable references.
 * - Used explicit thread safety arguments and checkRep for confidence in correctness.
 * - Provided clean separation of concerns (Board ADT, Commands API, HTTP server layer).
 *
 * TRADEOFFS:
 * - Synchronizing all Board methods improves safety, but could decrease throughput under heavy concurrent load (fine-grained locks not pursued due to problem scale).
 * - Using notifyAll() is less efficient than notify(), but it avoids missed signals and is simple to debug/test.
 * - Real-time watching is implemented using long-poll/blocking (not async/WebSocket); simple but potentially inefficient for highly interactive UIs.
 *
 * TESTING & DEBUGGING:
 * - Used JUnit for ADT tests, and SimulationMain for concurrency stress-testing.
 * - Tested endpoint behavior manually and via browser, verified /watch and /flip with concurrent and multi-user scenarios.
 *
 * CHALLENGES & LESSONS:
 * - Thread safety with blocking and notifyAll() required careful reasoning and incremental testing.
 * - Documenting rep invariants and thread safety arguments helped catch subtle bugs early.
 * - Coordinating endpoint spellings, query strings, and browser interaction took iterative refinement.
 *
 * POSSIBLE FUTURE IMPROVEMENTS:
 * - Use WebSockets instead of long-polling for /watch to improve UI responsiveness.
 * - Implement per-card locks if highly concurrent loads are expected.
 * - Add richer status reporting in HTTP API, e.g., JSON responses.
 *
 * OVERALL:
 * - The project reinforced the importance of separation of concerns, defensive programming, and explicit documentation in multithreaded networked systems.
 */
