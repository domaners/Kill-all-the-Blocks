package com.example.killalltheblocks;

import java.util.Arrays;
import java.util.Random;

public class GameEngine {
    public static final int BOARD_SIZE = 8;
    public static final int PIECE_SLOTS = 3;
    public static final int NO_SELECTION = -1;

    private final boolean[][] board = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final Random random;
    private final BlockPiece[] tray = new BlockPiece[PIECE_SLOTS];
    private int score;
    private int selectedSlot = NO_SELECTION;
    private long finishedDurationMillis;

    public GameEngine() {
        this(new Random());
    }

    GameEngine(Random random) {
        this.random = random;
        refillTray();
    }

    public boolean[][] copyBoard() {
        boolean[][] copy = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(board[row], 0, copy[row], 0, BOARD_SIZE);
        }
        return copy;
    }

    public BlockPiece getPiece(int slot) {
        if (slot < 0 || slot >= tray.length) {
            return null;
        }
        return tray[slot];
    }

    public int getScore() {
        return score;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public long getFinishedDurationMillis() {
        return finishedDurationMillis;
    }

    public void setFinishedDurationMillis(long finishedDurationMillis) {
        this.finishedDurationMillis = finishedDurationMillis;
    }

    public boolean selectSlot(int slot) {
        if (slot < 0 || slot >= tray.length || tray[slot] == null) {
            return false;
        }
        selectedSlot = slot;
        return true;
    }

    public boolean placeSelected(int row, int col) {
        if (selectedSlot == NO_SELECTION) {
            return false;
        }
        return placePiece(selectedSlot, row, col);
    }

    public boolean placePiece(int trayIndex, int row, int col) {
        if (trayIndex < 0 || trayIndex >= tray.length || tray[trayIndex] == null) {
            return false;
        }
        BlockPiece piece = tray[trayIndex];
        if (!canPlace(piece, row, col)) {
            return false;
        }
        for (BlockPiece.Cell cell : piece.getCells()) {
            board[row + cell.row][col + cell.col] = true;
        }
        int placedCells = piece.getCellCount();
        int clearedLines = clearCompletedLines();
        score += placedCells + (clearedLines * clearedLines * 10);
        tray[trayIndex] = null;
        selectedSlot = NO_SELECTION;
        if (isTrayEmpty()) {
            refillTray();
        }
        return true;
    }

    public boolean canPlace(BlockPiece piece, int row, int col) {
        if (piece == null) {
            return false;
        }
        for (BlockPiece.Cell cell : piece.getCells()) {
            int targetRow = row + cell.row;
            int targetCol = col + cell.col;
            if (targetRow < 0 || targetRow >= BOARD_SIZE || targetCol < 0 || targetCol >= BOARD_SIZE) {
                return false;
            }
            if (board[targetRow][targetCol]) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnyMove() {
        for (BlockPiece piece : tray) {
            if (piece == null) {
                continue;
            }
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (canPlace(piece, row, col)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void reset() {
        for (boolean[] row : board) {
            Arrays.fill(row, false);
        }
        score = 0;
        selectedSlot = NO_SELECTION;
        finishedDurationMillis = 0L;
        refillTray();
    }

    void setCellForTest(int row, int col, boolean filled) {
        board[row][col] = filled;
    }

    void setPieceForTest(int slot, BlockPiece piece) {
        tray[slot] = piece;
        if (selectedSlot == slot && piece == null) {
            selectedSlot = NO_SELECTION;
        }
    }

    private boolean isTrayEmpty() {
        for (BlockPiece piece : tray) {
            if (piece != null) {
                return false;
            }
        }
        return true;
    }

    private int clearCompletedLines() {
        boolean[] rowsToClear = new boolean[BOARD_SIZE];
        boolean[] colsToClear = new boolean[BOARD_SIZE];
        int lineCount = 0;

        for (int row = 0; row < BOARD_SIZE; row++) {
            boolean complete = true;
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (!board[row][col]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                rowsToClear[row] = true;
                lineCount++;
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            boolean complete = true;
            for (int row = 0; row < BOARD_SIZE; row++) {
                if (!board[row][col]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                colsToClear[col] = true;
                lineCount++;
            }
        }

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (rowsToClear[row] || colsToClear[col]) {
                    board[row][col] = false;
                }
            }
        }

        return lineCount;
    }

    private void refillTray() {
        selectedSlot = NO_SELECTION;
        for (int i = 0; i < tray.length; i++) {
            tray[i] = BlockPiece.random(random);
        }
    }
}
