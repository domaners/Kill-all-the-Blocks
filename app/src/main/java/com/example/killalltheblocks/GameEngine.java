package com.example.killalltheblocks;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GameEngine {
    public static final int BOARD_SIZE = 8;
    public static final int PIECE_SLOTS = 3;
    public static final int NO_SELECTION = -1;

    private final boolean[][] board = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final int[][] boardColors = new int[BOARD_SIZE][BOARD_SIZE];
    private final Random random;
    private final BlockPiece[] tray = new BlockPiece[PIECE_SLOTS];
    private int score;
    private int selectedSlot = NO_SELECTION;
    private long finishedDurationMillis;
    private int lastClearedLines;
    private final boolean[] lastClearedRows = new boolean[BOARD_SIZE];
    private final boolean[] lastClearedCols = new boolean[BOARD_SIZE];

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

    public int[][] copyBoardColors() {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(boardColors[row], 0, copy[row], 0, BOARD_SIZE);
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

    public int getLastClearedLines() {
        return lastClearedLines;
    }

    public boolean[] getLastClearedRowsCopy() {
        return Arrays.copyOf(lastClearedRows, lastClearedRows.length);
    }

    public boolean[] getLastClearedColsCopy() {
        return Arrays.copyOf(lastClearedCols, lastClearedCols.length);
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
        clearLastClearedLines();
        for (BlockPiece.Cell cell : piece.getCells()) {
            board[row + cell.row][col + cell.col] = true;
            boardColors[row + cell.row][col + cell.col] = piece.getColor();
        }
        int clearedLines = clearCompletedLines();
        lastClearedLines = clearedLines;
        int lineMultiplier = Math.max(1, clearedLines);
        score += piece.getCellCount() + (clearedLines * clearedLines * 10 * lineMultiplier);
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

    public int countAvailablePlacements() {
        int placements = 0;
        for (BlockPiece piece : tray) {
            if (piece == null) {
                continue;
            }
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (canPlace(piece, row, col)) {
                        placements++;
                    }
                }
            }
        }
        return placements;
    }

    boolean hasSequentialTrayPlacement() {
        BlockPiece[] pieces = new BlockPiece[PIECE_SLOTS];
        System.arraycopy(tray, 0, pieces, 0, tray.length);
        return canPlaceAll(pieces);
    }

    public void reset() {
        for (boolean[] row : board) {
            Arrays.fill(row, false);
        }
        for (int[] row : boardColors) {
            Arrays.fill(row, 0);
        }
        score = 0;
        selectedSlot = NO_SELECTION;
        finishedDurationMillis = 0L;
        clearLastClearedLines();
        refillTray();
    }

    public String encodeBoard() {
        StringBuilder builder = new StringBuilder(BOARD_SIZE * BOARD_SIZE);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                builder.append(board[row][col] ? '1' : '0');
            }
        }
        return builder.toString();
    }

    public String encodeBoardColors() {
        StringBuilder builder = new StringBuilder();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(boardColors[row][col]);
            }
        }
        return builder.toString();
    }

    public void restoreState(String encodedBoard, String[] pieceNames, int score, int selectedSlot) {
        restoreState(encodedBoard, null, pieceNames, score, selectedSlot);
    }

    public void restoreState(String encodedBoard, String encodedColors, String[] pieceNames, int score, int selectedSlot) {
        if (encodedBoard == null || encodedBoard.length() != BOARD_SIZE * BOARD_SIZE
                || pieceNames == null || pieceNames.length != PIECE_SLOTS) {
            reset();
            return;
        }
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                char value = encodedBoard.charAt(row * BOARD_SIZE + col);
                board[row][col] = value == '1';
                boardColors[row][col] = board[row][col] ? 0xff2563eb : 0;
            }
        }
        restoreBoardColors(encodedColors);
        for (int slot = 0; slot < PIECE_SLOTS; slot++) {
            tray[slot] = BlockPiece.fromName(pieceNames[slot]);
        }
        this.score = Math.max(0, score);
        this.selectedSlot = selectedSlot >= 0 && selectedSlot < PIECE_SLOTS && tray[selectedSlot] != null
                ? selectedSlot
                : NO_SELECTION;
        finishedDurationMillis = 0L;
        clearLastClearedLines();
        if (isTrayEmpty()) {
            refillTray();
        }
    }

    public String[] getPieceNames() {
        String[] pieceNames = new String[PIECE_SLOTS];
        for (int slot = 0; slot < PIECE_SLOTS; slot++) {
            pieceNames[slot] = tray[slot] == null ? "" : tray[slot].getName();
        }
        return pieceNames;
    }

    void setCellForTest(int row, int col, boolean filled) {
        board[row][col] = filled;
        boardColors[row][col] = filled ? 0xff2563eb : 0;
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

    private void clearLastClearedLines() {
        lastClearedLines = 0;
        Arrays.fill(lastClearedRows, false);
        Arrays.fill(lastClearedCols, false);
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
                lastClearedRows[row] = true;
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
                lastClearedCols[col] = true;
                lineCount++;
            }
        }

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (rowsToClear[row] || colsToClear[col]) {
                    board[row][col] = false;
                    boardColors[row][col] = 0;
                }
            }
        }

        return lineCount;
    }

    private void refillTray() {
        selectedSlot = NO_SELECTION;
        List<BlockPiece> pieces = BlockPiece.standardPieces();
        for (int attempt = 0; attempt < 500; attempt++) {
            BlockPiece[] candidate = new BlockPiece[PIECE_SLOTS];
            for (int i = 0; i < candidate.length; i++) {
                candidate[i] = pieces.get(random.nextInt(pieces.size()));
            }
            if (canPlaceAll(candidate)) {
                System.arraycopy(candidate, 0, tray, 0, tray.length);
                return;
            }
        }

        BlockPiece single = BlockPiece.fromName("Single");
        for (int i = 0; i < tray.length; i++) {
            tray[i] = single;
        }
    }

    private boolean canPlaceAll(BlockPiece[] pieces) {
        boolean[] used = new boolean[PIECE_SLOTS];
        boolean[][] boardCopy = copyBoard();
        return canPlaceAllRecursive(boardCopy, pieces, used, 0);
    }

    private boolean canPlaceAllRecursive(boolean[][] candidateBoard, BlockPiece[] pieces, boolean[] used, int depth) {
        if (depth == pieces.length) {
            return true;
        }
        for (int pieceIndex = 0; pieceIndex < pieces.length; pieceIndex++) {
            if (used[pieceIndex]) {
                continue;
            }
            BlockPiece piece = pieces[pieceIndex];
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (canPlaceOnBoard(candidateBoard, piece, row, col)) {
                        boolean[][] nextBoard = copyBoard(candidateBoard);
                        placeOnBoard(nextBoard, piece, row, col);
                        clearCompletedLines(nextBoard);
                        used[pieceIndex] = true;
                        if (canPlaceAllRecursive(nextBoard, pieces, used, depth + 1)) {
                            used[pieceIndex] = false;
                            return true;
                        }
                        used[pieceIndex] = false;
                    }
                }
            }
        }
        return false;
    }

    private static boolean[][] copyBoard(boolean[][] source) {
        boolean[][] copy = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(source[row], 0, copy[row], 0, BOARD_SIZE);
        }
        return copy;
    }

    private static boolean canPlaceOnBoard(boolean[][] targetBoard, BlockPiece piece, int row, int col) {
        if (piece == null) {
            return false;
        }
        for (BlockPiece.Cell cell : piece.getCells()) {
            int targetRow = row + cell.row;
            int targetCol = col + cell.col;
            if (targetRow < 0 || targetRow >= BOARD_SIZE || targetCol < 0 || targetCol >= BOARD_SIZE) {
                return false;
            }
            if (targetBoard[targetRow][targetCol]) {
                return false;
            }
        }
        return true;
    }

    private static void placeOnBoard(boolean[][] targetBoard, BlockPiece piece, int row, int col) {
        for (BlockPiece.Cell cell : piece.getCells()) {
            targetBoard[row + cell.row][col + cell.col] = true;
        }
    }

    private static int clearCompletedLines(boolean[][] targetBoard) {
        boolean[] rowsToClear = new boolean[BOARD_SIZE];
        boolean[] colsToClear = new boolean[BOARD_SIZE];
        int lineCount = 0;

        for (int row = 0; row < BOARD_SIZE; row++) {
            boolean complete = true;
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (!targetBoard[row][col]) {
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
                if (!targetBoard[row][col]) {
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
                    targetBoard[row][col] = false;
                }
            }
        }
        return lineCount;
    }

    private void restoreBoardColors(String encodedColors) {
        if (encodedColors == null || encodedColors.length() == 0) {
            return;
        }
        String[] values = encodedColors.split(",");
        if (values.length != BOARD_SIZE * BOARD_SIZE) {
            return;
        }
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int index = row * BOARD_SIZE + col;
                try {
                    boardColors[row][col] = board[row][col] ? Integer.parseInt(values[index]) : 0;
                } catch (NumberFormatException ignored) {
                    boardColors[row][col] = board[row][col] ? 0xff2563eb : 0;
                }
            }
        }
    }
}
