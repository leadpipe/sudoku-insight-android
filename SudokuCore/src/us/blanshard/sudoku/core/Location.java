package us.blanshard.sudoku.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A location on a Sudoku grid.
 *
 * @author Luke Blanshard
 */
public final class Location implements Comparable<Location> {

  /** The number of distinct locations. */
  public static final int COUNT = 81;

  public final Row row;
  public final Column column;
  public final Block block;

  /** The singleton subsets of this location within its 3 units: row, column, and block. */
  public final Map<Unit.Type, UnitSubset> unitSubsets;

  /** The 20 locations in all the units, not counting this one. */
  public final List<Location> peers;

  /** A number in the range [0, COUNT). */
  public final int index;

  public static Location of(Row row, Column column) {
    return of(row.index * 9 + column.index);
  }

  public static Location of(int rowNumber, int columnNumber) {
    return ofIndices(rowNumber - 1, columnNumber - 1);
  }

  public static Location ofIndices(int rowIndex, int columnIndex) {
    return of(rowIndex * 9 + columnIndex);
  }

  public static Location of(int index) {
    return instances[index];
  }

  /** All locations. */
  public static final List<Location> ALL;

  @Override public int compareTo(Location that) {
    return this.index - that.index;
  }

  @Override public String toString() {
    return String.format("(%d, %d)", row.number, column.number);
  }

  static Iterator<Location> iterator(byte[] indices) {
    return new Iter(indices);
  }

  private static class Iter implements Iterator<Location> {
    private final byte[] indices;
    private int next;
    private Iter(byte[] indices) {
      this.indices = indices;
    }
    @Override public boolean hasNext() {
      return next < indices.length;
    }
    @Override public Location next() {
      return of(indices[next++]);
    }
    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private Location(int index) {
    this.index = index;
    this.row = Row.ofIndex(index / 9);
    this.column = Column.ofIndex(index % 9);
    this.block = Block.ofIndex(index / 27 * 3 + index % 9 / 3);
    Map<Unit.Type, UnitSubset> subsets = Maps.newEnumMap(Unit.Type.class);
    subsets.put(Unit.Type.ROW, UnitSubset.of(row, this));
    subsets.put(Unit.Type.COLUMN, UnitSubset.of(column, this));
    subsets.put(Unit.Type.BLOCK, UnitSubset.of(block, this));
    this.unitSubsets = Collections.unmodifiableMap(subsets);
    this.peersArray = new Location[20];  // Filled in later, see static block below.
    this.peers = Collections.unmodifiableList(Arrays.asList(peersArray));
  }

  private final Location[] peersArray;
  private static final Location[] instances;
  static {
    instances = new Location[81];
    for (int i = 0; i < 81; ++i) {
      instances[i] = new Location(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
    for (Location loc : instances) {
      Set<Location> peers = Sets.newLinkedHashSet();
      peers.addAll(loc.row);
      peers.addAll(loc.column);
      peers.addAll(loc.block);
      peers.remove(loc);
      peers.toArray(loc.peersArray);
    }
  }
}
