package com.memorygame;

/**
 * Represents the state of a card on the board.
 *
 * - NONE: No card exists at this position
 * - FACE_DOWN: Card is face-down, hidden
 * - FACE_UP_UNCONTROLLED: Card is face-up but not controlled by anyone
 * - FACE_UP_CONTROLLED: Card is face-up and controlled by a player
 */
public enum CardState {
    NONE,
    FACE_DOWN,
    FACE_UP_UNCONTROLLED,
    FACE_UP_CONTROLLED
}
