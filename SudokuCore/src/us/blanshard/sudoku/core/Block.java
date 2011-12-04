package us.blanshard.sudoku.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One block of a Sudoku grid, numbered from 1 to 9 left to right, top to
 * bottom.
 *
 * @author Luke Blanshard
 */
public final class Block extends Unit {

  /** The block number, in the range 1..9. */
  public final int number;

  /** The block index, one less than the block number. */
  public final int index;

  public static Block of(int number) {
    return instances[number - 1];
  }

  public static Block ofIndex(int index) {
    return instances[index];
  }

  public static Block ofIndices(int rowIndex, int columnIndex) {
    return instances[rowIndex * 3 + columnIndex];
  }

  public int rowIndex() {
    return index / 3;
  }

  public int columnIndex() {
    return index % 3;
  }

  /** All the blocks. */
  public static final List<Block> ALL;

  @Override public int unitIndex() {
    return 18 + index;
  }

  @Override public boolean contains(Location loc) {
    return loc.index / 27 == rowIndex()
        && loc.index % 9 / 3  == columnIndex();
  }

  @Override public Type getType() {
    return Type.BLOCK;
  }

  @Override public String toString() {
    return "B" + number;
  }

  private Block(int index) {
    this.index = index;
    this.number = index + 1;
    int ul = index / 3 * 27 + index % 3 * 3;
    System.arraycopy(new byte[] {
      (byte) (ul + 0),  (byte) (ul + 1),  (byte) (ul + 2),
      (byte) (ul + 9),  (byte) (ul + 10), (byte) (ul + 11),
      (byte) (ul + 18), (byte) (ul + 19), (byte) (ul + 20),
    }, 0, this.locations, 0, 9);
  }

  private static final Block[] instances;
  static {
    instances = new Block[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Block(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
