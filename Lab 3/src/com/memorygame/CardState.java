package com.memorygame;

/**
 * Enumeration representing the possible states of a card in Memory Scramble.
 *
 * A card can be in one of four states:
 * - NONE: No card exists at this position (removed after matching)
 * - FACE_DOWN: Card exists but is not visible to any player
 * - FACE_UP_CONTROLLED: Card is visible and controlled by a specific player
 * - FACE_UP_UNCONTROLLED: Card is visible but not controlled by any player
 *
 * Representation Invariant:
 * - All enum values are non-null (enforced by Java enum semantics)
 * - Enum is immutable (no mutable state)
 *
 * Abstraction Function:
 * AF(state) = The visibility and control status of a card at a position on the board
 *   - NONE → position has no card (matched pair removed)
 *   - FACE_DOWN → card exists but hidden from all players
 *   - FACE_UP_CONTROLLED → card is visible and exclusively controlled by one player
 *   - FACE_UP_UNCONTROLLED → card is visible but available for any player to control
 *
 * Safety from Rep Exposure:
 * - Enum values are immutable and cannot be modified
 * - No mutable fields exist in this enum
 * - All enum constants are public and final by Java enum semantics
 * - Thread-safe: enums are inherently thread-safe in Java
 *
 * Thread Safety:
 * This enum is immutable and thread-safe. Multiple threads can safely
 * read enum values without synchronization.
 */
public enum CardState {
    /**
     * Represents a position where no card exists (removed after matching).
     */
    NONE,

    /**
     * Represents a card that is face-down (not visible to any player).
     */
    FACE_DOWN,

    /**
     * Represents a card that is face-up and controlled by a specific player.
     * The controlling player's ID is stored separately in Board.playerControl.
     */
    FACE_UP_CONTROLLED,

    /**
     * Represents a card that is face-up but not controlled by any player.
     * Any player can attempt to control this card.
     */
    FACE_UP_UNCONTROLLED
}
