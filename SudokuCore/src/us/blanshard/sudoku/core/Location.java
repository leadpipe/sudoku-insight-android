/*
Copyright 2013 Luke Blanshard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package us.blanshard.sudoku.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * A location on a Sudoku grid.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Location implements Comparable<Location> {

  /** The number of distinct locations. */
  public static final int COUNT = 81;

  public final Row row;
  public final Column column;
  public final Block block;

  /** The singleton subsets of this location within its 3 units: row, column, and block. */
  public final Map<Unit.Type, UnitSubset> unitSubsets;

  /**
   * The singleton subsets of this location within its 3 units: row, column, and block.
   * Order is not guaranteed.
   */
  public final ImmutableList<UnitSubset> unitSubsetList;

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

  public Unit unit(Unit.Type type) {
    return unitSubsets.get(type).unit;
  }

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
    UnitSubset rowSubset = UnitSubset.singleton(row, this);
    UnitSubset columnSubset = UnitSubset.singleton(column, this);
    UnitSubset blockSubset = UnitSubset.singleton(block, this);
    Map<Unit.Type, UnitSubset> subsets = Maps.newEnumMap(Unit.Type.class);
    subsets.put(Unit.Type.ROW, rowSubset);
    subsets.put(Unit.Type.COLUMN, columnSubset);
    subsets.put(Unit.Type.BLOCK, blockSubset);
    ImmutableList.Builder<UnitSubset> subsetsList = ImmutableList.builder();
    subsetsList.add(rowSubset, columnSubset, blockSubset);
    this.unitSubsets = Collections.unmodifiableMap(subsets);
    this.unitSubsetList = subsetsList.build();
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
