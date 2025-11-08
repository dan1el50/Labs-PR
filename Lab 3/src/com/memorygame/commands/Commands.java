package com.memorygame.commands;

import com.memorygame.Board;

/**
 * Commands module for the Memory Scramble game.
 *
 * This module provides the application-level interface between the HTTP server
 * and the Board ADT. It translates HTTP requests into Board method calls and
 * formats Board responses for HTTP transmission.
 *
 * Representation Invariant:
 * - board != null (always references a valid Board instance)
 *
 * Abstraction Function:
 * AF(board) = A command processor that executes game operations on the shared
 *             board instance and returns results as formatted strings
 *
 * Safety from Rep Exposure:
 * - The board field is private and final
 * - Methods only return immutable Strings (board state representations)
 * - Thread safety is delegated to Board's synchronized methods
 *
 * Thread Safety:
 * Thread-safe because board mutations happen in Board (which is synchronized),
 * and no mutable state is maintained in this class.
 */
public class Commands {

    /** The shared game board instance. */
    private final Board board;

    /**
     * Constructs a Commands instance wrapping the given board.
     *
     * @param board the game board to execute commands on
     * @throws IllegalArgumentException if board is null
     */
    public Commands(Board board) {
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        this.board = board;
        checkRep();
    }

    /**
     * Executes a flip command for the given player at the specified position.
     *
     * Implements the /flip/:player/:row,:col API endpoint.
     * Handles all game logic including blocking when cards are controlled by other players.
     *
     * Precondition:
     * - player must be a non-empty string
     * - 0 <= row < board.rows and 0 <= col < board.cols
     *
     * Postcondition:
     * - Previous unmatched cards are turned face-down (Rule 3-B)
     * - Previous matched pairs are removed (Rule 3-A)
     * - Card at (row, col) is flipped according to game rules
     * - Returns string representation of updated board state
     *
     * Thread Safety:
     * Thread-safe because it delegates to Board's synchronized methods.
     * Multiple players can call this concurrently without data corruption.
     *
     * Blocking Behavior:
     * May block up to 50ms if attempting to flip a card controlled by another player (Rule 1-D).
     *
     * @param player the player ID making the move
     * @param row the row index of the card to flip
     * @param col the column index of the card to flip
     * @return string representation of the updated board state, or "ERROR: <message>"
     * @throws IllegalArgumentException if player is null/empty or position invalid
     */
    public String flip(String player, int row, int col) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException("Invalid position: (" + row + "," + col + ")");
        }

        try {
            board.prepareForNextMove(player);
            board.flip(row, col, player);
            return board.look(player);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Returns the current board state visible to the given player.
     *
     * Implements the /look/:player API endpoint.
     * Returns text representation where player's cards show as "my <card>",
     * others' cards as "up <card>", face-down as "down", and removed as "none".
     *
     * Precondition:
     * - player must be a non-empty string
     *
     * Postcondition:
     * - Board state is unchanged
     * - Returns string format: "3x3\nmy 🦄\ndown\n..."
     *
     * Thread Safety:
     * Thread-safe read-only operation. Multiple players can call concurrently.
     *
     * @param player the player ID requesting the board state
     * @return string representation of the board state visible to this player
     * @throws IllegalArgumentException if player is null or empty
     */
    public String look(String player) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }

        try {
            return board.look(player);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Resets the board to initial state (all cards face-down).
     *
     * Implements the /reset/:player API endpoint.
     * Clears all player control and turns all cards face-down.
     *
     * Precondition:
     * - player must be a non-empty string
     *
     * Postcondition:
     * - All cards are face-down (except NONE cards)
     * - No players control any cards
     * - Returns fresh board state
     *
     * Thread Safety:
     * Thread-safe but should typically be called when no active games are in progress.
     *
     * @param player the player requesting the reset
     * @return string representation of the reset board state
     * @throws IllegalArgumentException if player is null or empty
     */
    public String reset(String player) {
        if (player == null || player.isEmpty()) {
            throw new IllegalArgumentException("Player ID must not be empty");
        }

        board.resetBoard();
        return board.look(player);
    }

    /**
     * Checks the representation invariant.
     * Invariant: board != null
     */
    private void checkRep() {
        assert board != null : "Board must not be null";
    }
}
