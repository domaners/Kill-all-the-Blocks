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
        BlockPiece piece = new BlockPiece("Test Single", new int[][]{{0, 0}}, 0xff123456);
        engine.setPieceForTest(0, piece);

        assertTrue(engine.placePiece(0, 0, 0));

        assertEquals(piece.getCellCount(), engine.getScore());
        assertFalse(engine.canPlace(piece, 0, 0));
        assertEquals(null, engine.getPiece(0));
        assertEquals(piece.getColor(), engine.copyBoardColors()[0][0]);
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
        assertEquals(1, engine.getLastClearedLines());
        assertTrue(engine.getLastClearedRowsCopy()[0]);
        boolean[][] board = engine.copyBoard();
        for (int col = 0; col < GameEngine.BOARD_SIZE; col++) {
            assertFalse(board[0][col]);
        }
    }

    @Test
    public void simultaneousLinesApplyScoreMultiplier() {
        GameEngine engine = new GameEngine(new Random(7));
        BlockPiece domino = new BlockPiece("Domino", new int[][]{{0, 0}, {0, 1}}, 0xffffffff);
        for (int col = 0; col < GameEngine.BOARD_SIZE - 2; col++) {
            engine.setCellForTest(0, col, true);
        }
        for (int row = 1; row < GameEngine.BOARD_SIZE - 1; row++) {
            engine.setCellForTest(row, GameEngine.BOARD_SIZE - 1, true);
        }
        engine.setCellForTest(GameEngine.BOARD_SIZE - 1, GameEngine.BOARD_SIZE - 1, true);
        engine.setPieceForTest(0, domino);

        assertTrue(engine.placePiece(0, 0, GameEngine.BOARD_SIZE - 2));

        assertEquals(2, engine.getLastClearedLines());
        assertTrue(engine.getLastClearedRowsCopy()[0]);
        assertTrue(engine.getLastClearedColsCopy()[GameEngine.BOARD_SIZE - 1]);
        assertEquals(82, engine.getScore());
    }

    @Test
    public void countsAvailablePlacementsForCurrentTray() {
        GameEngine engine = new GameEngine(new Random(10));

        assertTrue(engine.countAvailablePlacements() > 0);
    }

    @Test
    public void requestedLargeShapesAreAvailable() {
        assertNotNull(BlockPiece.fromName("Five Horizontal"));
        assertNotNull(BlockPiece.fromName("L 3x3"));
        assertNotNull(BlockPiece.fromName("L 3x3 Flip Horizontal"));
        assertNotNull(BlockPiece.fromName("L 3x3 Flip Vertical"));
        assertNotNull(BlockPiece.fromName("L 3x3 Flip Both"));
        assertNotNull(BlockPiece.fromName("Upside Down T Tall"));
    }

    @Test
    public void newTrayCanBePlacedSequentiallyOnCurrentBoard() {
        for (int seed = 0; seed < 50; seed++) {
            GameEngine engine = new GameEngine(new Random(seed));

            assertTrue(engine.hasSequentialTrayPlacement());
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

    @Test
    public void restoresEncodedBoardPiecesAndSelection() {
        GameEngine original = new GameEngine(new Random(5));
        original.selectSlot(1);
        original.placeSelected(0, 0);
        original.selectSlot(2);

        GameEngine restored = new GameEngine(new Random(6));
        restored.restoreState(
                original.encodeBoard(),
                original.getPieceNames(),
                original.getScore(),
                original.getSelectedSlot());

        assertEquals(original.getScore(), restored.getScore());
        assertEquals(original.encodeBoard(), restored.encodeBoard());
        assertEquals(original.getSelectedSlot(), restored.getSelectedSlot());
        assertEquals(original.getPiece(0), restored.getPiece(0));
        assertEquals(original.getPiece(1), restored.getPiece(1));
        assertEquals(original.getPiece(2), restored.getPiece(2));
    }

    @Test
    public void restoresPlacedBlockColors() {
        GameEngine original = new GameEngine(new Random(8));
        BlockPiece piece = new BlockPiece("Test Single", new int[][]{{0, 0}}, 0xffabcdef);
        original.setPieceForTest(0, piece);
        assertTrue(original.placePiece(0, 0, 0));

        GameEngine restored = new GameEngine(new Random(9));
        restored.restoreState(
                original.encodeBoard(),
                original.encodeBoardColors(),
                original.getPieceNames(),
                original.getScore(),
                original.getSelectedSlot());

        assertEquals(piece.getColor(), restored.copyBoardColors()[0][0]);
    }
}
