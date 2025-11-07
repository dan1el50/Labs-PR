package com.memorygame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable Board ADT for the Memory Scramble game.
 *
 * Thread-safe implementation using concurrent data structures.
 *
 * Rep Invariant:
 * - rows > 0, cols > 0
 * - board.length == rows, every board[i].length == cols
 * - playerControl only contains entries for FACE_UP_CONTROLLED cards
 * - No two players control the same card
 * - All card identifiers are non-empty strings
 *
 * Abstraction Function:
 * - Maps (row, col) to a card state and optionally controlled by player
 * - playerControl maps position "row,col" -> player ID for face-up controlled cards
 *
 * Safety from Rep Exposure:
 * - All fields are private
 * - No mutable references returned to caller
 * - All public methods are synchronized
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
     * Gets board dimensions.
     *
     * @return [rows, cols]
     */
    public int[] getDimensions() {
        return new int[]{rows, cols};
    }

    /**
     * Gets the card identifier at position (row, col).
     *
     * Precondition:
     * - 0 <= row < rows and 0 <= col < cols
     *
     * @param row the row index
     * @param col the column index
     * @return card identifier, or null if no card exists
     */
    public synchronized String getCard(int row, int col) {
        return board[row][col];
    }

    /**
     * Gets the state of card at position (row, col).
     *
     * Precondition:
     * - 0 <= row < rows and 0 <= col < cols
     *
     * @param row the row index
     * @param col the column index
     * @return card state
     */
    public synchronized CardState getState(int row, int col) {
        return state[row][col];
    }

    /**
     * Gets the player who controls this card, or null if uncontrolled.
     *
     * @param row the row index
     * @param col the column index
     * @return player ID or null
     */
    public synchronized String getController(int row, int col) {
        return playerControl.get(row + "," + col);
    }

    /**
     * Flips a card at position (row, col) by the given player.
     *
     * Implements all game rules for flipping with timeout-based blocking.
     *
     * @param row the row index
     * @param col the column index
     * @param playerId the player ID
     */
    public synchronized void flip(int row, int col, String playerId) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IllegalArgumentException("Invalid position: (" + row + "," + col + ")");
        }

        checkRep();

        String position = row + "," + col;
        String firstCardPos = playerFirstCard.get(playerId);

        // ==================== FIRST FLIP ====================
        if (firstCardPos == null) {
            // Rule 1-A: No card exists
            if (state[row][col] == CardState.NONE) {
                checkRep();
                return;
            }

            // Rule 1-D: Card controlled by another player → TRY WITH TIMEOUT
            String controller = playerControl.get(position);
            if (controller != null && !controller.equals(playerId)) {
                // Try to wait for up to 50ms for the card to be released
                try {
                    this.wait(50);  // TIMEOUT: Don't wait forever
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    checkRep();
                    return;
                }
                // After timeout, re-check
                controller = playerControl.get(position);
                if (controller != null && !controller.equals(playerId)) {
                    // Still controlled, give up this move
                    checkRep();
                    return;
                }
            }

            // Rule 1-B: Face-down → turn face-up, player controls
            if (state[row][col] == CardState.FACE_DOWN) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                this.notifyAll();
                checkRep();
                return;
            }

            // Rule 1-C: Face-up uncontrolled → player takes control
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                this.notifyAll();
                checkRep();
                return;
            }

            // Already controlled by this player
            if (state[row][col] == CardState.FACE_UP_CONTROLLED) {
                if (playerId.equals(controller)) {
                    playerFirstCard.put(playerId, position);
                    checkRep();
                    return;
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
                return;
            }

            String firstCard = board[firstRow][firstCol];

            // Rule 2-A: No card exists
            if (state[row][col] == CardState.NONE) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return;
            }

            // Rule 2-B: Card controlled by someone
            String cardController = playerControl.get(position);
            if (cardController != null && state[row][col] == CardState.FACE_UP_CONTROLLED) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return;
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
                    return;
                }

                // Rule 2-E: Cards don't match → both uncontrolled
                else {
                    state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                    state[row][col] = CardState.FACE_UP_UNCONTROLLED;
                    playerControl.remove(firstCardPos);
                    playerFirstCard.remove(playerId);
                    this.notifyAll();
                    checkRep();
                    return;
                }
            }

            // Already face-up uncontrolled → treat as no match
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();
                checkRep();
                return;
            }
        }

        checkRep();
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
     * Checks if player has a first card waiting for second flip.
     *
     * @param playerId the player ID
     * @return true if player has first card pending
     */
    public synchronized boolean hasFirstCard(String playerId) {
        return playerFirstCard.containsKey(playerId);
    }

    /**
     * Returns the board state visible to a player.
     *
     * Format:
     * ```
     * 3x3
     * my 🦄
     * down
     * ...
     * ```
     *
     * @param playerId the player ID
     * @return string representation of board state
     */
    public synchronized String look(String playerId) {
        checkRep();

        StringBuilder sb = new StringBuilder();
        sb.append(rows).append("x").append(cols).append("\n");

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String position = i + "," + j;
                CardState cardState = state[i][j];

                if (cardState == CardState.NONE) {
                    sb.append("none\n");
                } else if (cardState == CardState.FACE_DOWN) {
                    sb.append("down\n");
                } else if (cardState == CardState.FACE_UP_CONTROLLED) {
                    String controller = playerControl.get(position);
                    if (playerId.equals(controller)) {
                        sb.append("my ").append(board[i][j]).append("\n");
                    } else {
                        sb.append("up ").append(board[i][j]).append("\n");
                    }
                } else if (cardState == CardState.FACE_UP_UNCONTROLLED) {
                    sb.append("up ").append(board[i][j]).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Resets board to initial state (all cards face-down).
     * Used for fresh game start.
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
}
