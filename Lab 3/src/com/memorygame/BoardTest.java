package com.memorygame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive unit tests for the Board ADT.
 * Tests all game rules and edge cases.
 */
public class BoardTest {

    private Board board;
    private List<String> cards;

    /**
     * Creates a fresh 3x3 board before each test.
     * Board layout:
     * 🦄 🌈 🦄
     * 🌈 🎯 🎯
     * 🦄 🌈 🎯
     */
    @BeforeEach
    public void setUp() {
        cards = new ArrayList<>();
        cards.add("🦄");
        cards.add("🌈");
        cards.add("🦄");
        cards.add("🌈");
        cards.add("🎯");
        cards.add("🎯");
        cards.add("🦄");
        cards.add("🌈");
        cards.add("🎯");

        board = new Board(3, 3, cards);
    }

    // ============ CONSTRUCTOR TESTS ============

    @Test
    public void testConstructor() {
        assertNotNull(board);
        int[] dims = board.getDimensions();
        assertEquals(3, dims[0]);
        assertEquals(3, dims[1]);
    }

    @Test
    public void testInitialState() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(CardState.FACE_DOWN, board.getState(i, j));
            }
        }
    }

    // ============ RULE 1: FIRST FLIP TESTS ============

    @Test
    public void testRule1B_FlipFaceDownCard() {
        board.flip(0, 0, "player1");

        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 0));
        assertEquals("player1", board.getController(0, 0));
        assertEquals("🦄", board.getCard(0, 0));
        assertTrue(board.hasFirstCard("player1"));
    }

    @Test
    public void testRule1C_FlipUncontrolledCard() {
        // Flip a card to make it uncontrolled
        board.flip(0, 0, "player1");
        board.flip(0, 1, "player1");
        board.prepareForNextMove("player1");

        // Now it's face-down after cleanup
        // Flip it again with player2
        board.flip(0, 0, "player2");

        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 0));
        assertEquals("player2", board.getController(0, 0));
    }

    // ============ RULE 2: SECOND FLIP TESTS ============

    @Test
    public void testRule2D_MatchingCards() {
        board.flip(0, 0, "player1");
        board.flip(0, 2, "player1");

        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 0));
        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 2));
        assertEquals("player1", board.getController(0, 0));
        assertEquals("player1", board.getController(0, 2));
        assertFalse(board.hasFirstCard("player1"));
    }

    @Test
    public void testRule2E_NonMatchingCards() {
        board.flip(0, 0, "player1");
        board.flip(0, 1, "player1");

        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 0));
        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 1));
        assertNull(board.getController(0, 0));
        assertNull(board.getController(0, 1));
    }

    @Test
    public void testRule2C_FlipFaceDownCard() {
        board.flip(0, 0, "player1");
        board.flip(1, 1, "player1");

        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(1, 1));
        assertEquals("🎯", board.getCard(1, 1));
    }

    // ============ RULE 3: CLEANUP TESTS ============

    @Test
    public void testRule3A_MatchedPairRemoved() {
        board.flip(0, 0, "player1");
        board.flip(0, 2, "player1");
        board.prepareForNextMove("player1");

        assertEquals(CardState.NONE, board.getState(0, 0));
        assertEquals(CardState.NONE, board.getState(0, 2));
    }

    @Test
    public void testRule3B_UnmatchedTurnFaceDown() {
        board.flip(0, 0, "player1");
        board.flip(0, 1, "player1");

        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 0));
        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 1));

        board.prepareForNextMove("player1");

        assertEquals(CardState.FACE_DOWN, board.getState(0, 0));
        assertEquals(CardState.FACE_DOWN, board.getState(0, 1));
    }

    // ============ MULTI-PLAYER TESTS ============

    @Test
    public void testMultiPlayer_TwoPlayersSimultaneous() {
        board.flip(0, 0, "player1");
        board.flip(1, 1, "player2");

        assertEquals("player1", board.getController(0, 0));
        assertEquals("player2", board.getController(1, 1));
        assertTrue(board.hasFirstCard("player1"));
        assertTrue(board.hasFirstCard("player2"));
    }

    @Test
    public void testMultiPlayer_PlayersMatchIndependently() {
        board.flip(0, 0, "player1");
        board.flip(0, 2, "player1");

        board.flip(1, 1, "player2");
        board.flip(1, 2, "player2");

        assertEquals("player1", board.getController(0, 0));
        assertEquals("player1", board.getController(0, 2));
        assertEquals("player2", board.getController(1, 1));
        assertEquals("player2", board.getController(1, 2));
    }

    // ============ LOOK METHOD TESTS ============

    @Test
    public void testLook_InitialBoard() {
        String state = board.look("player1");
        assertTrue(state.startsWith("3x3"));
        assertTrue(state.contains("down"));
    }

    @Test
    public void testLook_ShowsControlledCards() {
        board.flip(0, 0, "player1");
        String state = board.look("player1");
        assertTrue(state.contains("my 🦄"));
    }

    // ============ EDGE CASE TESTS ============

    @Test
    public void testEdgeCase_HasFirstCardInitially() {
        assertFalse(board.hasFirstCard("player1"));
    }

    @Test
    public void testEdgeCase_HasFirstCardAfterFlip() {
        board.flip(0, 0, "player1");
        assertTrue(board.hasFirstCard("player1"));
    }

    @Test
    public void testEdgeCase_FirstCardClearedAfterSecondFlip() {
        board.flip(0, 0, "player1");
        assertTrue(board.hasFirstCard("player1"));

        board.flip(0, 1, "player1");
        assertFalse(board.hasFirstCard("player1"));
    }
}
