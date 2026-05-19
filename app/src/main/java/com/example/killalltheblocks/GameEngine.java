package com.example.killalltheblocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GameEngine {
    public static final int BOARD_SIZE = 8;
    public static final int PIECE_SLOTS = 3;
    public static final int NO_SELECTION = -1;

    private boolean[][] board = new boolean[BOARD_SIZE][BOARD_SIZE];
    private int[][] boardColors = new int[BOARD_SIZE][BOARD_SIZE];
    private final Random random;
    private BlockPiece[] tray = new BlockPiece[PIECE_SLOTS];
    private int score;
    private int comboStreak = 0;
    private long finishedDurationMillis;
    private int lastClearedLines;
    private boolean[] lastClearedRows = new boolean[BOARD_SIZE];
    private boolean[] lastClearedCols = new boolean[BOARD_SIZE];

    public GameEngine() {
        this(new Random());
    }

    public GameEngine(Random random) {
        this.random = random;
        reset();
    }

    public boolean[][] copyBoard() {
        boolean[][] copy = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    public int[][] copyBoardColors() {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(boardColors[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    public BlockPiece getPiece(int slot) {
        if (slot >= 0 && slot < PIECE_SLOTS) {
            return tray[slot];
        }
        return null;
    }

    public int getScore() {
        return score;
    }

    public long getFinishedDurationMillis() {
        return finishedDurationMillis;
    }

    public int getLastClearedLines() {
        return lastClearedLines;
    }

    public boolean[] getLastClearedRowsCopy() {
        return lastClearedRows.clone();
    }

    public boolean[] getLastClearedColsCopy() {
        return lastClearedCols.clone();
    }

    public int getComboStreak() {
        return comboStreak;
    }

    public int getTotalLinesClearedThisTray() {
        return 0;
    }

    public int getBoardClearCount() {
        return 0;
    }

    public void setFinishedDurationMillis(long millis) {
        this.finishedDurationMillis = millis;
    }

    public boolean placePiece(int slot, int row, int col) {
        BlockPiece piece = getPiece(slot);
        if (piece == null || !canPlace(piece, row, col)) {
            return false;
        }

        // Commit shape coordinates
        for (BlockPiece.Cell cell : piece.getCells()) {
            board[row + cell.row][col + cell.col] = true;
            boardColors[row + cell.row][col + cell.col] = piece.getColor();
        }

        // Line Evaluation
        lastClearedLines = clearCompletedLines();

        // Scoring
        if (lastClearedLines > 0) {
            comboStreak++;
            int basePoints = getBasePoints(lastClearedLines);
            score += basePoints * (comboStreak);
        } else {
            comboStreak = 0;
            score += piece.getCellCount();
        }

        tray[slot] = null;
        if (isTrayEmpty()) {
            refillTray();
        }
        return true;
    }

    private int getBasePoints(int lines) {
        switch (lines) {
            case 1: return 100;
            case 2: return 200;
            case 3: return 600;
            case 4: return 1200;
            case 5: return 2000;
            default: return lines * 50; // Fallback for massive clears
        }
    }

    public boolean canPlace(BlockPiece piece, int row, int col) {
        if (piece == null) return false;
        for (BlockPiece.Cell cell : piece.getCells()) {
            int r = row + cell.row;
            int c = col + cell.col;
            if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE || board[r][c]) {
                return false;
            }
        }
        return true;
    }

    public boolean[][] predictLineClears(BlockPiece piece, int row, int col) {
        boolean[] rowsToClear = new boolean[BOARD_SIZE];
        boolean[] colsToClear = new boolean[BOARD_SIZE];
        if (!canPlace(piece, row, col)) return new boolean[][]{rowsToClear, colsToClear};

        boolean[][] tempBoard = copyBoard();
        for (BlockPiece.Cell cell : piece.getCells()) {
            tempBoard[row + cell.row][col + cell.col] = true;
        }

        for (int r = 0; r < BOARD_SIZE; r++) {
            boolean complete = true;
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!tempBoard[r][c]) { complete = false; break; }
            }
            if (complete) rowsToClear[r] = true;
        }

        for (int c = 0; c < BOARD_SIZE; c++) {
            boolean complete = true;
            for (int r = 0; r < BOARD_SIZE; r++) {
                if (!tempBoard[r][c]) { complete = false; break; }
            }
            if (complete) colsToClear[c] = true;
        }
        return new boolean[][]{rowsToClear, colsToClear};
    }

    public int countAvailablePlacements() {
        int count = 0;
        for (BlockPiece piece : tray) {
            if (piece == null) continue;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (canPlace(piece, r, c)) count++;
                }
            }
        }
        return count;
    }

    public void reset() {
        board = new boolean[BOARD_SIZE][BOARD_SIZE];
        boardColors = new int[BOARD_SIZE][BOARD_SIZE];
        score = 0;
        comboStreak = 0;
        refillTray();
    }

    private boolean isTrayEmpty() {
        for (BlockPiece p : tray) if (p != null) return false;
        return true;
    }

    private void refillTray() {
        List<BlockPiece> allPieces = BlockPiece.standardPieces();
        BlockPiece[] candidate = new BlockPiece[PIECE_SLOTS];

        for (int i = 0; i < 50; i++) { // Max attempts to find a valid triplet
            for (int s = 0; s < PIECE_SLOTS; s++) {
                candidate[s] = selectWeightedPiece(allPieces);
            }
            if (validateTriplet(candidate, board)) {
                tray = candidate;
                return;
            }
        }

        // Fallback: EASY pieces
        List<BlockPiece> easyPieces = new ArrayList<>();
        for (BlockPiece p : allPieces) if (p.getTier() == BlockPiece.Tier.EASY) easyPieces.add(p);
        for (int s = 0; s < PIECE_SLOTS; s++) {
            tray[s] = easyPieces.get(random.nextInt(easyPieces.size()));
        }
    }

    private BlockPiece selectWeightedPiece(List<BlockPiece> all) {
        double easyWeight = 100.0;
        double mediumWeight = 50.0;
        double hardWeight = 20.0;

        // Dynamic Probability Shift
        double delta = score / 2000.0; // Faster transition
        easyWeight = Math.max(5, easyWeight - delta * 40); // Drop easy pieces faster
        mediumWeight = mediumWeight + delta * 10;
        hardWeight = hardWeight + delta * 25;

        // Open-Slot Baiting
        List<String> voids = scanVoids();
        
        double totalWeight = 0;
        List<Double> weights = new ArrayList<>();
        for (BlockPiece p : all) {
            double w = 0;
            switch (p.getTier()) {
                case EASY: w = easyWeight; break;
                case MEDIUM: w = mediumWeight; break;
                case HARD: w = hardWeight; break;
            }

            // Board-clearing favor: pieces with width or height >= 3 or specific shapes
            if (score < 1000) {
                if (p.getWidth() >= 3 || p.getHeight() >= 3) {
                    w *= 2.0; // Double weight for long pieces early on
                }
                if (p.getName().contains("Single") || p.getName().contains("Two")) {
                    w *= 1.5; // Also favor small pieces to help fit
                }
            }

            if (voids.contains(p.getName())) w *= 1.5;
            weights.add(w);
            totalWeight += w;
        }

        double r = random.nextDouble() * totalWeight;
        double current = 0;
        for (int i = 0; i < all.size(); i++) {
            current += weights.get(i);
            if (r <= current) return all.get(i);
        }
        return all.get(0);
    }

    private List<String> scanVoids() {
        List<String> voids = new ArrayList<>();
        // Simple scan for 1x4 or 4x1 voids as an example
        for (int r = 0; r < BOARD_SIZE; r++) {
            int consecutive = 0;
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!board[r][c]) consecutive++;
                else {
                    if (consecutive >= 4) voids.add("Four Horizontal");
                    consecutive = 0;
                }
            }
            if (consecutive >= 4) voids.add("Four Horizontal");
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            int consecutive = 0;
            for (int r = 0; r < BOARD_SIZE; r++) {
                if (!board[r][c]) consecutive++;
                else {
                    if (consecutive >= 4) voids.add("Four Vertical");
                    consecutive = 0;
                }
            }
            if (consecutive >= 4) voids.add("Four Vertical");
        }
        return voids;
    }

    private boolean validateTriplet(BlockPiece[] triplet, boolean[][] currentBoard) {
        int[][] permutations = {{0,1,2}, {0,2,1}, {1,0,2}, {1,2,0}, {2,0,1}, {2,1,0}};
        for (int[] p : permutations) {
            if (canFitSequence(triplet[p[0]], triplet[p[1]], triplet[p[2]], currentBoard)) return true;
        }
        return false;
    }

    private boolean canFitSequence(BlockPiece p1, BlockPiece p2, BlockPiece p3, boolean[][] currentBoard) {
        for (int r1 = 0; r1 < BOARD_SIZE; r1++) {
            for (int c1 = 0; c1 < BOARD_SIZE; c1++) {
                if (canPlaceOnBoard(currentBoard, p1, r1, c1)) {
                    boolean[][] b2 = simulatePlace(currentBoard, p1, r1, c1);
                    for (int r2 = 0; r2 < BOARD_SIZE; r2++) {
                        for (int c2 = 0; c2 < BOARD_SIZE; c2++) {
                            if (canPlaceOnBoard(b2, p2, r2, c2)) {
                                boolean[][] b3 = simulatePlace(b2, p2, r2, c2);
                                for (int r3 = 0; r3 < BOARD_SIZE; r3++) {
                                    for (int c3 = 0; c3 < BOARD_SIZE; c3++) {
                                        if (canPlaceOnBoard(b3, p3, r3, c3)) return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean canPlaceOnBoard(boolean[][] b, BlockPiece p, int r, int c) {
        for (BlockPiece.Cell cell : p.getCells()) {
            int targetR = r + cell.row;
            int targetC = c + cell.col;
            if (targetR < 0 || targetR >= BOARD_SIZE || targetC < 0 || targetC >= BOARD_SIZE || b[targetR][targetC]) return false;
        }
        return true;
    }

    private boolean[][] simulatePlace(boolean[][] b, BlockPiece p, int r, int c) {
        boolean[][] next = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) System.arraycopy(b[i], 0, next[i], 0, BOARD_SIZE);
        for (BlockPiece.Cell cell : p.getCells()) next[r + cell.row][c + cell.col] = true;
        
        // Handle clears reactively in simulation too to be accurate
        boolean[] rows = new boolean[BOARD_SIZE];
        boolean[] cols = new boolean[BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            boolean full = true;
            for (int col = 0; col < BOARD_SIZE; col++) if (!next[row][col]) { full = false; break; }
            if (full) rows[row] = true;
        }
        for (int col = 0; col < BOARD_SIZE; col++) {
            boolean full = true;
            for (int row = 0; row < BOARD_SIZE; row++) if (!next[row][col]) { full = false; break; }
            if (full) cols[col] = true;
        }
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) if (rows[i] || cols[j]) next[i][j] = false;
        }
        return next;
    }

    private int clearCompletedLines() {
        boolean[] rowsToClear = new boolean[BOARD_SIZE];
        boolean[] colsToClear = new boolean[BOARD_SIZE];
        int count = 0;

        for (int r = 0; r < BOARD_SIZE; r++) {
            boolean complete = true;
            for (int c = 0; c < BOARD_SIZE; c++) if (!board[r][c]) { complete = false; break; }
            if (complete) { rowsToClear[r] = true; count++; }
        }
        for (int c = 0; c < BOARD_SIZE; c++) {
            boolean complete = true;
            for (int r = 0; r < BOARD_SIZE; r++) if (!board[r][c]) { complete = false; break; }
            if (complete) { colsToClear[c] = true; count++; }
        }

        lastClearedRows = rowsToClear;
        lastClearedCols = colsToClear;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (rowsToClear[r] || colsToClear[c]) {
                    board[r][c] = false;
                    boardColors[r][c] = 0;
                }
            }
        }
        return count;
    }

    public boolean hasAnyMove() {
        return countAvailablePlacements() > 0;
    }

    public String encodeBoardColors() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                sb.append(boardColors[r][c]).append(",");
            }
        }
        return sb.toString();
    }

    public String[] getPieceNames() {
        String[] names = new String[PIECE_SLOTS];
        for (int i = 0; i < PIECE_SLOTS; i++) {
            names[i] = tray[i] != null ? tray[i].getName() : null;
        }
        return names;
    }

    public boolean isBoardEmpty() {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c]) return false;
            }
        }
        return true;
    }

    public void restoreState(String boardData, String[] pieces, int score, int combo) {
        this.score = score;
        this.comboStreak = combo;
        if (boardData != null && boardData.length() == BOARD_SIZE * BOARD_SIZE) {
            for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
                board[i / BOARD_SIZE][i % BOARD_SIZE] = boardData.charAt(i) == '1';
            }
        }
        if (pieces != null) {
            for (int i = 0; i < PIECE_SLOTS && i < pieces.length; i++) {
                tray[i] = BlockPiece.fromName(pieces[i]);
            }
        }
    }

    public void restoreState(String boardData, String boardColorsData, String[] pieces, int score, int combo) {
        restoreState(boardData, pieces, score, combo);
        if (boardColorsData != null) {
            String[] colors = boardColorsData.split(",");
            for (int i = 0; i < BOARD_SIZE * BOARD_SIZE && i < colors.length; i++) {
                if (!colors[i].isEmpty()) {
                    boardColors[i / BOARD_SIZE][i % BOARD_SIZE] = Integer.parseInt(colors[i]);
                }
            }
        }
    }

    public String encodeBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) sb.append(board[r][c] ? "1" : "0");
        }
        return sb.toString();
    }

    public void setCellForTest(int r, int c, boolean val) {
        board[r][c] = val;
    }

    public int getSelectedSlot() {
        return NO_SELECTION;
    }

    public boolean selectSlot(int slot) {
        return true;
    }

    public boolean placeSelected(int row, int col) {
        return false;
    }

    public int getLastAppliedMultiplier() {
        return 1;
    }

    public boolean hasSequentialTrayPlacement() {
        return validateTriplet(tray, board);
    }

    public void setPieceForTest(int slot, BlockPiece piece) {
        if (slot >= 0 && slot < PIECE_SLOTS) {
            tray[slot] = piece;
        }
    }
}
