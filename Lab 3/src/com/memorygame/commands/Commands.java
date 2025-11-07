package com.memorygame.commands;

import com.memorygame.Board;

/**
 * Commands module for the Memory Scramble game.
 *
 * This module is the ONLY interface that the HTTP server should call.
 * It translates HTTP requests into Board operations and provides
 * responses in the format required by the specification.
 *
 * Specification:
 * - GET /look/:player → returns current board state for player
 * - GET /flip/:player/:row,:col → flips card at (row, col) for player
 *
 * Safety from Rep Exposure:
 * - Board is private and not returned to caller
 * - All responses are formatted as strings
 */
public class Commands {
    private final Board board;

    /**
     * Constructs the Commands module with a given board.
     *
     * Precondition:
     * - board is not null
     *
     * Postcondition:
     * - Commands instance is ready to handle requests
     *
     * @param board the game board (must not be null)
     */
    public Commands(Board board) {
        if (board == null) {
            throw new IllegalArgumentException("Board must not be null");
        }
        this.board = board;
    }

    /**
     * Returns the current board state for the given player.
     *
     * Specification:
     * - Endpoint: GET /look/:player
     * - Returns full board state visible to this player
     *
     * Precondition:
     * - player must be a non-empty string
     *
     * Postcondition:
     * - Returns board state formatted as "rows x cols" followed by card states
     * - Each card state is "none", "down", "my CARD", or "up CARD"
     *
     * @param player the player ID
     * @return board state string
     * @throws IllegalArgumentException if player is null or empty
     */
    public String look(String player) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }
        return board.look(player);
    }

    /**
     * Flips a card at the given position for the player.
     *
     * Specification:
     * - Endpoint: GET /flip/:player/:row,:col
     * - Attempts to flip card, may block if controlled by another player
     *
     * Precondition:
     * - player must be a non-empty string
     * - row and col must be valid indices (0 <= row < rows, 0 <= col < cols)
     *
     * Postcondition:
     * - Card state is updated according to game rules
     * - Returns updated board state or error message
     * - May block if card is controlled by another player (blocking uses Object.wait())
     *
     * @param player the player ID
     * @param row the row index
     * @param col the column index
     * @return updated board state or error message
     * @throws IllegalArgumentException if player is null/empty or position is invalid
     */
    public String flip(String player, int row, int col) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException("Invalid position");
        }

        try {
            // Only prepare board if player is starting a new turn (no first card stored)
            // This checks if the player has a first card waiting for second flip
            if (!board.hasFirstCard(player)) {
                // Starting a new turn - cleanup from previous turn
                board.prepareForNextMove(player);
            }

            // Make the flip
            board.flip(row, col, player);

            // Return current board state
            return board.look(player);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Resets the board for a fresh game.
     *
     * Specification:
     * - Endpoint: GET /reset/:player
     * - Resets all cards to face-down
     *
     * Precondition:
     * - player must be a non-empty string
     *
     * Postcondition:
     * - Board is reset to initial state
     * - Returns fresh board state
     *
     * @param player the player ID
     * @return fresh board state
     * @throws IllegalArgumentException if player is null or empty
     */
    public String reset(String player) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }

        board.resetBoard();
        return board.look(player);
    }

}
