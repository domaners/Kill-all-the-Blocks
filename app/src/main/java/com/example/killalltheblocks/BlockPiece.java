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
        pieces.add(new BlockPiece("Single", new int[][]{{0, 0}}, 0xffef5350));
        pieces.add(new BlockPiece("Two Vertical", new int[][]{{0, 0}, {1, 0}}, 0xffab47bc));
        pieces.add(new BlockPiece("Two Horizontal", new int[][]{{0, 0}, {0, 1}}, 0xff5c6bc0));
        pieces.add(new BlockPiece("Three Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}}, 0xff29b6f6));
        pieces.add(new BlockPiece("Three Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}}, 0xff26a69a));
        pieces.add(new BlockPiece("Four Vertical", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}, 0xff66bb6a));
        pieces.add(new BlockPiece("Four Horizontal", new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}}, 0xffffca28));
        pieces.add(new BlockPiece("Square 2x2", new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}}, 0xffff7043));
        pieces.add(new BlockPiece("L Small", new int[][]{{0, 0}, {1, 0}, {1, 1}}, 0xff8d6e63));
        pieces.add(new BlockPiece("L Mirror", new int[][]{{0, 1}, {1, 0}, {1, 1}}, 0xff78909c));
        pieces.add(new BlockPiece("L Tall", new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}}, 0xffec407a));
        pieces.add(new BlockPiece("L Tall Mirror", new int[][]{{0, 1}, {1, 1}, {2, 0}, {2, 1}}, 0xff7e57c2));
        pieces.add(new BlockPiece("T", new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 1}}, 0xff42a5f5));
        pieces.add(new BlockPiece("Z", new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 2}}, 0xff26c6da));
        pieces.add(new BlockPiece("S", new int[][]{{0, 1}, {0, 2}, {1, 0}, {1, 1}}, 0xff9ccc65));
        pieces.add(new BlockPiece("Plus", new int[][]{{0, 1}, {1, 0}, {1, 1}, {1, 2}, {2, 1}}, 0xffffd54f));
        return Collections.unmodifiableList(pieces);
    }
}
