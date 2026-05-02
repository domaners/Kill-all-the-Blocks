package com.example.killalltheblocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class GameEngineTest {
    @Test
    public void startsWithThreePieces() {
        GameEngine engine = new GameEngine(new Random(1));

        for (int slot = 0; slot < GameEngine.PIECE_SLOTS; slot++) {
            assertNotNull(engine.getPiece(slot));
        }
        assertEquals(GameEngine.NO_SELECTION, engine.getSelectedSlot());
        assertTrue(engine.hasAnyMove());
    }

    @Test
    public void placePieceAddsCellScoreAndClearsSlot() {
        GameEngine engine = new GameEngine(new Random(2));
        BlockPiece piece = engine.getPiece(0);

        assertTrue(engine.placePiece(0, 0, 0));

        assertEquals(piece.getCellCount(), engine.getScore());
        assertFalse(engine.canPlace(piece, 0, 0));
        assertEquals(null, engine.getPiece(0));
    }

    @Test
    public void completedRowIsClearedAndBonusIsAwarded() {
        GameEngine engine = new GameEngine(new Random(3));
        BlockPiece single = new BlockPiece("Single", new int[][]{{0, 0}}, 0xffffffff);
        for (int col = 0; col < GameEngine.BOARD_SIZE - 1; col++) {
            engine.setCellForTest(0, col, true);
        }
        engine.setPieceForTest(0, single);

        assertTrue(engine.placePiece(0, 0, GameEngine.BOARD_SIZE - 1));

        assertEquals(11, engine.getScore());
        boolean[][] board = engine.copyBoard();
        for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
            assertFalse(board[0][col]);
        }
    }

    @Test
    public void reportsNoMoveWhenRemainingPieceCannotFit() {
        GameEngine engine = new GameEngine(new Random(4));
        BlockPiece domino = new BlockPiece("Domino", new int[][]{{0, 0}, {0, 1}}, 0xffffffff);
        for (int row = 0; row < GameEngine.BOARD_SIZE; row++) {
            for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
                engine.setCellForTest(row, col, (row + col) % 2 == 0);
            }
        }
        engine.setPieceForTest(0, domino);
        engine.setPieceForTest(1, null);
        engine.setPieceForTest(2, null);

        assertFalse(engine.hasAnyMove());
    }
}
