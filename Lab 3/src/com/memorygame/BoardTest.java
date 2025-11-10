package com.memorygame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive unit tests for the Board ADT.
 * Tests all game rules and edge cases.
 *
 * Updated to match MIT 6.102 Problem Set 4 specification:
 * - look() returns format with dimensions header and one card per line
 * - flip() returns "OK" or "ERROR: ..." strings
 * - Coordinates are 0-based (0,0) to (rows-1, cols-1)
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
        String result = board.flip(0, 0, "player1");
        assertEquals("OK", result, "Flip should return OK");
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
        String result = board.flip(0, 0, "player2");
        assertEquals("OK", result);
        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 0));
        assertEquals("player2", board.getController(0, 0));
    }

    // ============ RULE 2: SECOND FLIP TESTS ============

    @Test
    public void testRule2D_MatchingCards() {
        String result1 = board.flip(0, 0, "player1");
        assertEquals("OK", result1);
        String result2 = board.flip(0, 2, "player1");
        assertEquals("OK", result2);

        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 0));
        assertEquals(CardState.FACE_UP_CONTROLLED, board.getState(0, 2));
        assertEquals("player1", board.getController(0, 0));
        assertEquals("player1", board.getController(0, 2));
        assertFalse(board.hasFirstCard("player1"));
    }

    @Test
    public void testRule2E_NonMatchingCards() {
        String result1 = board.flip(0, 0, "player1");
        assertEquals("OK", result1);
        String result2 = board.flip(0, 1, "player1");
        assertEquals("OK", result2);

        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 0));
        assertEquals(CardState.FACE_UP_UNCONTROLLED, board.getState(0, 1));
        assertNull(board.getController(0, 0));
        assertNull(board.getController(0, 1));
    }

    @Test
    public void testRule2C_FlipFaceDownCard() {
        board.flip(0, 0, "player1");
        String result = board.flip(1, 1, "player1");
        assertEquals("OK", result);

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
        assertTrue(state.startsWith("3x3\n"), "Should start with dimensions");
        assertTrue(state.contains("down"), "Should show face-down cards");

        // Count lines: should be 1 (header) + 9 (cards) = 10 lines total
        long lineCount = state.lines().count();
        assertEquals(10, lineCount, "Should have 10 lines for 3x3 board");
    }

    @Test
    public void testLook_ShowsControlledCards() {
        board.flip(0, 0, "player1");
        String state = board.look("player1");
        assertTrue(state.contains("my 🦄"), "Player should see their controlled card as 'my'");
    }

    @Test
    public void testLook_ShowsOtherPlayerCards() {
        board.flip(0, 0, "player1");
        String state = board.look("player2");
        assertTrue(state.contains("up 🦄"), "Other players should see controlled cards as 'up'");
    }

    @Test
    public void testLook_ShowsNoneForRemovedCards() {
        // Match a pair
        board.flip(0, 0, "player1");
        board.flip(0, 2, "player1");
        board.prepareForNextMove("player1");

        String state = board.look("player1");
        assertTrue(state.contains("none"), "Should show 'none' for removed cards");

        // Count 'none' occurrences - should be 2 (both cards in matched pair)
        long noneCount = state.lines().filter(line -> line.equals("none")).count();
        assertEquals(2, noneCount, "Should have 2 'none' entries for matched pair");
    }

    @Test
    public void testLook_FormatCorrect() {
        board.flip(0, 0, "player1");
        String state = board.look("player1");

        // Check format structure
        String[] lines = state.split("\n");
        assertEquals(10, lines.length, "Should have 10 lines total");
        assertEquals("3x3", lines[0], "First line should be dimensions");

        // Line 1 (position 0,0) should be "my 🦄" since player1 controls it
        assertEquals("my 🦄", lines[1], "First card should be 'my 🦄'");

        // Other positions should be "down"
        assertEquals("down", lines[2], "Position (0,1) should be 'down'");
    }

    // ============ ERROR HANDLING TESTS ============

    @Test
    public void testFlip_InvalidPosition() {
        String result = board.flip(-1, 0, "player1");
        assertTrue(result.startsWith("ERROR"), "Negative row should return ERROR");

        result = board.flip(0, -1, "player1");
        assertTrue(result.startsWith("ERROR"), "Negative column should return ERROR");

        result = board.flip(3, 0, "player1");
        assertTrue(result.startsWith("ERROR"), "Row >= rows should return ERROR");

        result = board.flip(0, 3, "player1");
        assertTrue(result.startsWith("ERROR"), "Column >= cols should return ERROR");
    }

    @Test
    public void testFlip_ValidPosition() {
        String result = board.flip(0, 0, "player1");
        assertEquals("OK", result, "Valid flip should return OK");

        result = board.flip(2, 2, "player2");
        assertEquals("OK", result, "Valid flip should return OK");
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
        assertFalse(board.hasFirstCard("player1"), "Second flip should clear first card tracker");
    }

    @Test
    public void testEdgeCase_MultiplePlayersIndependentFirstCards() {
        board.flip(0, 0, "player1");
        board.flip(1, 1, "player2");
        board.flip(2, 2, "player3");

        assertTrue(board.hasFirstCard("player1"));
        assertTrue(board.hasFirstCard("player2"));
        assertTrue(board.hasFirstCard("player3"));
    }

    @Test
    public void testEdgeCase_CompleteGame() {
        // Match all pairs
        board.flip(0, 0, "player1"); // 🦄
        board.flip(0, 2, "player1"); // 🦄
        board.prepareForNextMove("player1");

        board.flip(0, 1, "player1"); // 🌈
        board.flip(1, 0, "player1"); // 🌈
        board.prepareForNextMove("player1");

        board.flip(1, 1, "player1"); // 🎯
        board.flip(1, 2, "player1"); // 🎯
        board.prepareForNextMove("player1");

        // All cards should be removed
        String state = board.look("player1");
        long noneCount = state.lines().filter(line -> line.equals("none")).count();
        assertEquals(6, noneCount, "Should have 6 'none' entries for 3 matched pairs");
    }
}
