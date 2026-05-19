package com.example.killalltheblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BlockPiece {
    public static class Cell {
        public final int row;
        public final int col;

        public Cell(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private String name;
    private final Cell[] cells;
    private int color;
    public enum Tier { EASY, MEDIUM, HARD }
    private Tier tier;
    private int baseScore = -1; // -1 means use default logic

    public BlockPiece(String name, int[][] coords, int color, Tier tier) {
        this.name = name;
        this.cells = new Cell[coords.length];
        for (int i = 0; i < coords.length; i++) {
            this.cells[i] = new Cell(coords[i][0], coords[i][1]);
        }
        this.color = color;
        this.tier = tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public void setBaseScore(int baseScore) {
        this.baseScore = baseScore;
    }

    public int getBaseScore() {
        return baseScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockPiece that = (BlockPiece) o;
        return java.util.Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name);
    }

    public String getName() {
        return name;
    }

    public Cell[] getCells() {
        return cells;
    }

    public int getColor() {
        return color;
    }

    public Tier getTier() {
        return tier;
    }

    public int getCellCount() {
        return cells.length;
    }

    public int getWidth() {
        int max = 0;
        for (Cell cell : cells) {
            max = Math.max(max, cell.col);
        }
        return max + 1;
    }

    public int getHeight() {
        int max = 0;
        for (Cell cell : cells) {
            max = Math.max(max, cell.row);
        }
        return max + 1;
    }

    public static BlockPiece random(Random random) {
        List<BlockPiece> all = standardPieces();
        return all.get(random.nextInt(all.size()));
    }

    public static BlockPiece fromName(String name) {
        if (name == null) return null;
        for (BlockPiece piece : standardPieces()) {
            if (piece.getName().equals(name)) {
                return piece;
            }
        }
        return null;
    }

    public static List<BlockPiece> standardPieces() {
        List<BlockPiece> pieces = new ArrayList<>();
        // EASY
        pieces.add(new BlockPiece("Single", new int[][]{{0, 0}}, 0xffff1744, Tier.MEDIUM));
        pieces.add(new BlockPiece("Two Vertical", new int[][]{{0, 0}, {1, 0}}, 0xffd500f9, Tier.EASY));
        pieces.add(new BlockPiece("Two Horizontal", new int[][]{{0, 0}, {0, 1}}, 0xff304ffe, Tier.EASY));
        pieces.add(new BlockPiece("Three Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}}, 0xff00b0ff, Tier.EASY));
        pieces.add(new BlockPiece("Three Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}}, 0xff00c853, Tier.EASY));
        pieces.add(new BlockPiece("L Small", new int[][]{{0, 0}, {1, 0}, {1, 1}}, 0xff795548, Tier.EASY));
        pieces.add(new BlockPiece("L Mirror", new int[][]{{0, 1}, {1, 0}, {1, 1}}, 0xff546e7a, Tier.EASY));

        // MEDIUM
        pieces.add(new BlockPiece("Four Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}, 0xff64dd17, Tier.MEDIUM));
        pieces.add(new BlockPiece("Four Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}}, 0xffffd600, Tier.MEDIUM));
        pieces.add(new BlockPiece("Square 2x2", new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}}, 0xffff6d00, Tier.MEDIUM));
        pieces.add(new BlockPiece("Rectangle 2x3", new int[][]{
                {0, 0}, {0, 1}, {0, 2},
                {1, 0}, {1, 1}, {1, 2}}, 0xff3d5afe, Tier.MEDIUM));
        pieces.add(new BlockPiece("Rectangle 3x2", new int[][]{
                {0, 0}, {0, 1},
                {1, 0}, {1, 1},
                {2, 0}, {2, 1}}, 0xfff44336, Tier.MEDIUM));
        pieces.add(new BlockPiece("L Tall", new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}}, 0xffff4081, Tier.MEDIUM));
        pieces.add(new BlockPiece("L Tall Mirror", new int[][]{{0, 1}, {1, 1}, {2, 0}, {2, 1}}, 0xff651fff, Tier.MEDIUM));
        pieces.add(new BlockPiece("T Down", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 1}}, 0xff2979ff, Tier.MEDIUM));
        pieces.add(new BlockPiece("T Up", new int[][]{{1, 0}, {1, 1}, {1, 2}, {0, 1}}, 0xff2979ff, Tier.MEDIUM));
        pieces.add(new BlockPiece("T Right", new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}}, 0xff2979ff, Tier.MEDIUM));
        pieces.add(new BlockPiece("T Left", new int[][]{{0, 1}, {1, 1}, {2, 1}, {1, 0}}, 0xff2979ff, Tier.MEDIUM));
        pieces.add(new BlockPiece("Z", new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 2}}, 0xff00b8d4, Tier.HARD));
        pieces.add(new BlockPiece("S", new int[][]{{0, 1}, {0, 2}, {1, 0}, {1, 1}}, 0xff76ff03, Tier.HARD));

        // HARD
        pieces.add(new BlockPiece("Five Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}}, 0xffffff00, Tier.HARD));
        pieces.add(new BlockPiece("Square 3x3", new int[][]{
                {0, 0}, {0, 1}, {0, 2},
                {1, 0}, {1, 1}, {1, 2},
                {2, 0}, {2, 1}, {2, 2}}, 0xfff50057, Tier.HARD));
        pieces.add(new BlockPiece("L 3x3", new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}}, 0xffff3d00, Tier.MEDIUM));
        pieces.add(new BlockPiece("L 3x3 Flip Horizontal", new int[][]{{0, 2}, {1, 2}, {2, 0}, {2, 1}, {2, 2}}, 0xffaa00ff, Tier.HARD));
        pieces.add(new BlockPiece("L 3x3 Flip Vertical", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 0}, {2, 0}}, 0xff00e5ff, Tier.HARD));
        pieces.add(new BlockPiece("L 3x3 Flip Both", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}}, 0xff00e676, Tier.HARD));
        pieces.add(new BlockPiece("Two Diagonal Down", new int[][]{{0, 0}, {1, 1}}, 0xff00e5ff, Tier.MEDIUM));
        pieces.add(new BlockPiece("Two Diagonal Up", new int[][]{{0, 1}, {1, 0}}, 0xff1de9b6, Tier.MEDIUM));
        pieces.add(new BlockPiece("Three Diagonal Down", new int[][]{{0, 0}, {1, 1}, {2, 2}}, 0xffc6ff00, Tier.HARD));
        pieces.add(new BlockPiece("Three Diagonal Up", new int[][]{{0, 2}, {1, 1}, {2, 0}}, 0xffffab00, Tier.HARD));

        return Collections.unmodifiableList(pieces);
    }
}
