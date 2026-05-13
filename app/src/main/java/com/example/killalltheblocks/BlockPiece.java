package com.example.killalltheblocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class BlockPiece {
    static final class Cell {
        final int row;
        final int col;

        Cell(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private final String name;
    private final Cell[] cells;
    private final int color;

    BlockPiece(String name, int[][] cells, int color) {
        this.name = name;
        this.cells = new Cell[cells.length];
        for (int i = 0; i < cells.length; i++) {
            this.cells[i] = new Cell(cells[i][0], cells[i][1]);
        }
        this.color = color;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BlockPiece)) {
            return false;
        }
        return name.equals(((BlockPiece) other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    String getName() {
        return name;
    }

    Cell[] getCells() {
        return cells;
    }

    int getColor() {
        return color;
    }

    int getCellCount() {
        return cells.length;
    }

    int getWidth() {
        int max = 0;
        for (Cell cell : cells) {
            max = Math.max(max, cell.col);
        }
        return max + 1;
    }

    int getHeight() {
        int max = 0;
        for (Cell cell : cells) {
            max = Math.max(max, cell.row);
        }
        return max + 1;
    }

    static BlockPiece random(Random random) {
        List<BlockPiece> pieces = standardPieces();
        return pieces.get(random.nextInt(pieces.size()));
    }

    static BlockPiece fromName(String name) {
        if (name == null) {
            return null;
        }
        for (BlockPiece piece : standardPieces()) {
            if (piece.name.equals(name)) {
                return piece;
            }
        }
        return null;
    }

    static List<BlockPiece> standardPieces() {
        List<BlockPiece> pieces = new ArrayList<>();
        pieces.add(new BlockPiece("Single", new int[][]{{0, 0}}, 0xffff1744));
        pieces.add(new BlockPiece("Two Vertical", new int[][]{{0, 0}, {1, 0}}, 0xffd500f9));
        pieces.add(new BlockPiece("Two Horizontal", new int[][]{{0, 0}, {0, 1}}, 0xff304ffe));
        pieces.add(new BlockPiece("Three Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}}, 0xff00b0ff));
        pieces.add(new BlockPiece("Three Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}}, 0xff00c853));
        pieces.add(new BlockPiece("Four Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}, 0xff64dd17));
        pieces.add(new BlockPiece("Four Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}}, 0xffffd600));
        pieces.add(new BlockPiece("Five Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}}, 0xffffff00));
        pieces.add(new BlockPiece("Square 2x2", new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}}, 0xffff6d00));
        pieces.add(new BlockPiece("Square 3x3", new int[][]{
                {0, 0}, {0, 1}, {0, 2},
                {1, 0}, {1, 1}, {1, 2},
                {2, 0}, {2, 1}, {2, 2}}, 0xfff50057));
        pieces.add(new BlockPiece("Rectangle 2x3", new int[][]{
                {0, 0}, {0, 1}, {0, 2},
                {1, 0}, {1, 1}, {1, 2}}, 0xff3d5afe));
        pieces.add(new BlockPiece("Rectangle 3x2", new int[][]{
                {0, 0}, {0, 1},
                {1, 0}, {1, 1},
                {2, 0}, {2, 1}}, 0xfff44336));
        pieces.add(new BlockPiece("Two Diagonal Down", new int[][]{{0, 0}, {1, 1}}, 0xff00e5ff));
        pieces.add(new BlockPiece("Two Diagonal Up", new int[][]{{0, 1}, {1, 0}}, 0xff1de9b6));
        pieces.add(new BlockPiece("Three Diagonal Down", new int[][]{{0, 0}, {1, 1}, {2, 2}}, 0xffc6ff00));
        pieces.add(new BlockPiece("Three Diagonal Up", new int[][]{{0, 2}, {1, 1}, {2, 0}}, 0xffffab00));
        pieces.add(new BlockPiece("L Small", new int[][]{{0, 0}, {1, 0}, {1, 1}}, 0xff795548));
        pieces.add(new BlockPiece("L Mirror", new int[][]{{0, 1}, {1, 0}, {1, 1}}, 0xff546e7a));
        pieces.add(new BlockPiece("L Tall", new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}}, 0xffff4081));
        pieces.add(new BlockPiece("L Tall Mirror", new int[][]{{0, 1}, {1, 1}, {2, 0}, {2, 1}}, 0xff651fff));
        pieces.add(new BlockPiece("L 3x3", new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}}, 0xffff3d00));
        pieces.add(new BlockPiece("L 3x3 Flip Horizontal", new int[][]{{0, 2}, {1, 2}, {2, 0}, {2, 1}, {2, 2}}, 0xffaa00ff));
        pieces.add(new BlockPiece("L 3x3 Flip Vertical", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 0}, {2, 0}}, 0xff00e5ff));
        pieces.add(new BlockPiece("L 3x3 Flip Both", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}}, 0xff00e676));
        pieces.add(new BlockPiece("Z", new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 2}}, 0xff00b8d4));
        pieces.add(new BlockPiece("S", new int[][]{{0, 1}, {0, 2}, {1, 0}, {1, 1}}, 0xff76ff03));
        return Collections.unmodifiableList(pieces);
    }
}
