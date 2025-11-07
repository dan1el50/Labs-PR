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
 * - playerControl properly synchronized (no two players control same card)
 * - No duplicate cards in a matching pair
 * - All card identifiers are non-empty strings
 *
 * Abstraction Function:
 * - Maps (row, col) to a card state and optionally controlled by player
 * - playerControl maps position -> player ID for face-up controlled cards
 *
 * Safety from Rep Exposure:
 * - All fields are private
 * - No mutable references returned to caller
 * - All public methods are synchronized
 */
public class Board {
    private final int rows;
    private final int cols;
    private final String[][] board;  // Card identifiers (null if no card)
    private final CardState[][] state;  // Card states
    private final Map<String, String> playerControl;  // Maps "row,col" -> playerId
    private final Map<String, String> playerFirstCard;  // Maps playerId -> "row,col"


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
     * Gets who controls the card at (row, col), if anyone.
     *
     * Precondition:
     * - 0 <= row < rows and 0 <= col < cols
     *
     * @param row the row index
     * @param col the column index
     * @return player ID if controlled, null otherwise
     */
    public synchronized String getController(int row, int col) {
        return playerControl.get(row + "," + col);
    }

    /**
     * Flips a card at position (row, col) by the given player.
     *
     * Game Rules:
     * - 1-A: No card exists → operation fails
     * - 1-B: Face-down card → turns face-up, player controls it
     * - 1-C: Face-up, uncontrolled → player takes control (stays face-up)
     * - 1-D: Face-up, controlled by another player → BLOCKS until available
     * - 2-A: No card exists → fail, lose control of first card
     * - 2-B: Card face-up and controlled → fail, lose control of first card (no blocking to prevent deadlock)
     * - 2-C: Face-down → turns face-up
     * - 2-D: Cards match → player keeps both cards (face-up, controlled)
     * - 2-E: Cards don't match → player loses both cards (face-up, uncontrolled)
     *
     * Precondition:
     * - 0 <= row < rows and 0 <= col < cols
     * - playerId is non-empty string
     * - This is called in sequence by the player
     *
     * Postcondition:
     * - Card state is updated according to rules
     * - May block if card is controlled by another player
     *
     * @param row the row index
     * @param col the column index
     * @param playerId the player making the move
     * @throws IllegalArgumentException if indices out of bounds
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
            // This is the first flip for this player

            // Rule 1-A: No card exists at this position
            if (state[row][col] == CardState.NONE) {
                checkRep();
                return;  // Operation fails, nothing happens
            }

            // Rule 1-D: Card is controlled by another player → BLOCK
            String controller = playerControl.get(position);
            while (controller != null && !controller.equals(playerId)) {
                try {
                    // Wait for the card to be released
                    this.wait();
                    // Re-check if still controlled by another player
                    controller = playerControl.get(position);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    checkRep();
                    return;
                }
            }

            // Rule 1-B: Face-down card → turn face-up, player takes control
            if (state[row][col] == CardState.FACE_DOWN) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                checkRep();
                return;
            }

            // Rule 1-C: Face-up, uncontrolled → player takes control
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                state[row][col] = CardState.FACE_UP_CONTROLLED;
                playerControl.put(position, playerId);
                playerFirstCard.put(playerId, position);
                checkRep();
                return;
            }

            // If already controlled by this player, do nothing
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
            // This is the second flip for this player
            String[] firstPos = firstCardPos.split(",");
            int firstRow = Integer.parseInt(firstPos[0]);
            int firstCol = Integer.parseInt(firstPos[1]);

            // Get the first card - it should exist and be controlled
            if (state[firstRow][firstCol] == CardState.NONE) {
                // First card was somehow removed, reset player state
                playerFirstCard.remove(playerId);
                checkRep();
                return;
            }

            String firstCard = board[firstRow][firstCol];

            // Rule 2-A: No card exists at this position
            if (state[row][col] == CardState.NONE) {
                // Lose control of first card
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();  // Wake up any blocked threads
                checkRep();
                return;
            }

            // Rule 2-B: Card is face-up and controlled by someone (including this player)
            String cardController = playerControl.get(position);
            if (cardController != null && state[row][col] == CardState.FACE_UP_CONTROLLED) {
                // Lose control of first card (no blocking on 2nd flip)
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();  // Wake up any blocked threads
                checkRep();
                return;
            }

            // Rule 2-C: Face-down card → turn face-up
            if (state[row][col] == CardState.FACE_DOWN) {
                state[row][col] = CardState.FACE_UP_UNCONTROLLED;
                String secondCard = board[row][col];

                // Rule 2-D: Cards match → player keeps both, controlled
                if (firstCard.equals(secondCard)) {
                    state[firstRow][firstCol] = CardState.FACE_UP_CONTROLLED;
                    state[row][col] = CardState.FACE_UP_CONTROLLED;
                    playerControl.put(firstCardPos, playerId);
                    playerControl.put(position, playerId);
                    playerFirstCard.remove(playerId);
                    this.notifyAll();  // Wake up any blocked threads
                    checkRep();
                    return;
                }

                // Rule 2-E: Cards don't match → both become uncontrolled
                else {
                    state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                    state[row][col] = CardState.FACE_UP_UNCONTROLLED;
                    playerControl.remove(firstCardPos);
                    playerFirstCard.remove(playerId);
                    this.notifyAll();  // Wake up any blocked threads
                    checkRep();
                    return;
                }
            }

            // If the card is already face-up and uncontrolled, treat as non-match
            if (state[row][col] == CardState.FACE_UP_UNCONTROLLED) {
                // Same as non-match: both become uncontrolled
                state[firstRow][firstCol] = CardState.FACE_UP_UNCONTROLLED;
                playerControl.remove(firstCardPos);
                playerFirstCard.remove(playerId);
                this.notifyAll();  // Wake up any blocked threads
                checkRep();
                return;
            }
        }

        checkRep();
    }

    /**
     * Gets the current board state as a string for the given player.
     *
     * Format: rows x cols, then each card's state
     * - "none" if no card
     * - "down" if face-down
     * - "my CARD_ID" if face-up and player controls it
     * - "up CARD_ID" if face-up and another player controls it
     *
     * Precondition:
     * - playerId is non-empty string
     *
     * Postcondition:
     * - Returns string representation of board state visible to player
     *
     * @param playerId the player viewing the board
     * @return string representation of board state
     */
    public synchronized String look(String playerId) {
        checkRep();
        StringBuilder sb = new StringBuilder();
        sb.append(rows).append("x").append(cols).append("\n");

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String key = i + "," + j;
                String controller = playerControl.get(key);

                if (state[i][j] == CardState.NONE) {
                    sb.append("none");
                } else if (state[i][j] == CardState.FACE_DOWN) {
                    sb.append("down");
                } else if (state[i][j] == CardState.FACE_UP_UNCONTROLLED) {
                    sb.append("my ").append(board[i][j]);
                } else if (state[i][j] == CardState.FACE_UP_CONTROLLED) {
                    if (playerId.equals(controller)) {
                        sb.append("my ").append(board[i][j]);
                    } else {
                        sb.append("up ").append(board[i][j]);
                    }
                }

                if (j < cols - 1) {
                    sb.append("\n");
                }
            }
            if (i < rows - 1) {
                sb.append("\n");
            }
        }

        checkRep();
        return sb.toString();
    }

    /**
     * Checks the representation invariants.
     *
     * Verifies:
     * - Board dimensions are positive
     * - Board arrays have correct dimensions
     * - No two players control the same card
     * - Card states are consistent with control map
     *
     * @throws AssertionError if any invariant is violated
     */
    private void checkRep() {
        assert rows > 0 && cols > 0 : "Invalid dimensions";
        assert board.length == rows : "Board rows mismatch";
        assert state.length == rows : "State rows mismatch";

        for (int i = 0; i < rows; i++) {
            assert board[i].length == cols : "Board cols mismatch at row " + i;
            assert state[i].length == cols : "State cols mismatch at row " + i;
        }

        // Verify no duplicate control entries (no two players control same card)
        Set<String> controlled = new HashSet<>(playerControl.values());
        assert controlled.size() == playerControl.size() : "Multiple players control same card";

        // Verify control map matches state
        for (Map.Entry<String, String> entry : playerControl.entrySet()) {
            String pos = entry.getKey();
            String[] parts = pos.split(",");
            int i = Integer.parseInt(parts[0]);
            int j = Integer.parseInt(parts[1]);

            // A controlled card must be face-up controlled
            assert state[i][j] == CardState.FACE_UP_CONTROLLED :
                    "Controlled card must be face-up controlled at " + pos;
        }
    }


    /**
     * Removes matched pairs that the player controls.
     * Should be called at the start of player's next move.
     *
     * Implements Rule 3-A: If previous cards matched, remove them from board.
     *
     * Precondition:
     * - playerId is non-empty string
     *
     * Postcondition:
     * - Any matched pairs controlled by this player are removed (set to NONE)
     *
     * @param playerId the player ID
     */
    public synchronized void removeMatchedPairs(String playerId) {
        checkRep();

        // Find all positions controlled by this player
        List<String> toRemove = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String position = i + "," + j;
                String controller = playerControl.get(position);

                // If this player controls it, remove it
                if (playerId.equals(controller)) {
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
            playerControl.remove(pos);
        }

        checkRep();
    }

    /**
     * Implements Rule 3-B: Before next move, unmatched pairs should turn face-down if uncontrolled.
     * This is called before a player makes their next move.
     *
     * Precondition:
     * - playerId is non-empty string
     *
     * Postcondition:
     * - Any face-up uncontrolled cards turn face-down
     *
     * @param playerId the player making the next move
     */
    public synchronized void prepareForNextMove(String playerId) {
        checkRep();

        // First remove any matched pairs
        removeMatchedPairs(playerId);

        // Then turn face-down any uncontrolled face-up cards
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
     * Precondition:
     * - playerId is non-empty string
     *
     * @param playerId the player ID
     * @return true if player has flipped a first card, false otherwise
     */
    public synchronized boolean hasFirstCard(String playerId) {
        return playerFirstCard.containsKey(playerId);
    }

    /**
     * Resets the entire board for a fresh game.
     * All cards go back to face-down and uncontrolled.
     *
     * Postcondition:
     * - All cards that exist are face-down
     * - No cards are controlled
     * - All player first cards are cleared
     */
    public synchronized void resetBoard() {
        checkRep();

        // Turn all cards back to face-down (except NONE cards)
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (state[i][j] != CardState.NONE) {
                    state[i][j] = CardState.FACE_DOWN;
                }
            }
        }

        // Clear all control
        playerControl.clear();

        // Clear all first cards
        playerFirstCard.clear();

        checkRep();
    }

}
