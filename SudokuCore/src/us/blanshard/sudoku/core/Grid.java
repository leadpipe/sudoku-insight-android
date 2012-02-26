/*
Copyright 2011 Google Inc.

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

import static us.blanshard.sudoku.core.Numeral.numeral;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An immutable Sudoku grid: each location may have a numeral set, the class is
 * a Map from Location to Numeral.  The nested Builder class is a mutable
 * version of the grid.  It accepts any numeral at any location: it does not
 * enforce the constraints of the game.
 *
 * @author Luke Blanshard
 */
public final class Grid extends AbstractMap<Location, Numeral> implements Map<Location, Numeral> {

  private final byte[] squares;

  private Grid(byte[] squares) {
    this.squares = squares;
  }

  public static final Grid BLANK = new Grid(new byte[81]);

  /** Returns a new Builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a mutable version of this grid. */
  public Builder asBuilder() {
    return new Builder(this);
  }

  /** Possible states for a Sudoku grid. */
  public enum State {
    INCOMPLETE,  // Not all filled in, but nothing that is filled in breaks the rules.
    BROKEN,      // Something that's filled in breaks the rules.
    SOLVED;      // Completely filled in, no rule violations.
  }

  public State getState() {
    if (getBrokenLocations().size() > 0)
      return State.BROKEN;
    return size() < 81 ? State.INCOMPLETE : State.SOLVED;
  }

  public boolean isSolved() {
    return getState() == State.SOLVED;
  }

  /**
   * Returns locations that have duplicate values for some unit.
   */
  public Set<Location> getBrokenLocations() {
    Set<Location> answer = new LocSet();
    for (Unit unit : Unit.allUnits()) {
      int bits = 0;
      for (Location loc : unit) {
        Numeral num = get(loc);
        if (num != null) {
          if ((bits & num.bit) != 0) {
            answer.add(loc);
            for (Location firstLoc : unit)
              if (get(firstLoc) == num) {
                answer.add(firstLoc);
                break;
              }
          }
          bits |= num.bit;
        }
      }
    }
    return answer;
  }

  public static final class Builder {
    private Grid grid;
    private boolean built;

    private Builder() {
      this(BLANK);
    }

    private Builder(Grid grid) {
      this.grid = grid;
      this.built = true;
    }

    private Grid grid() {
      if (built) {
        Grid grid = new Grid(this.grid.squares.clone());
        this.grid = grid;
        this.built = false;
      }
      return this.grid;
    }

    /** Returns an immutable snapshot of this grid. */
    public Grid build() {
      built = true;
      return grid;
    }

    /** Resets the grid to empty. */
    public Builder clear() {
      return reset(BLANK);
    }

    /** Resets this builder to match the given grid. */
    public Builder reset(Grid grid) {
      this.grid = grid;
      built = true;
      return this;
    }

    /** Tells whether the grid has a mapping for the given location. */
    public boolean containsKey(Location loc) {
      return grid.containsKey(loc);
    }

    /** Returns the numeral mapped to the given location, or null. */
    public Numeral get(Location loc) {
      return grid.get(loc);
    }

    /** Sets the numeral for the given location. */
    public Builder put(Location key, Numeral value) {
      grid().squares[key.index] = (byte) value.number;
      return this;
    }

    /** Erases the given location. */
    public Builder remove(Location loc) {
      grid().squares[loc.index] = 0;
      return this;
    }

    /** Sets all the locations given. */
    public Builder putAll(Collection<Entry<Location, Numeral>> entries) {
      for (Entry<Location, Numeral> e : entries)
        put(e.getKey(), e.getValue());
      return this;
    }

    /** Returns the number of squares filled in. */
    public int size() {
      return grid.size();
    }

    /** Returns the assignments set in this builder. */
    public Set<Entry<Location, Numeral>> entrySet() {
      return grid.entrySet();
    }
  }

  @Override public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override public boolean containsKey(Object key) {
    if (key instanceof Location) {
      return containsKey((Location) key);
    }
    return false;
  }

  public boolean containsKey(Location loc) {
    return squares[loc.index] > 0;
  }

  @Override public boolean containsValue(Object value) {
    if (value instanceof Numeral) {
      int num = ((Numeral) value).number;
      for (int square : squares) {
        if (square == num)
          return true;
      }
    }
    return false;
  }

  @Override public Set<Entry<Location, Numeral>> entrySet() {
    return new EntrySet();
  }

  @Override public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof Grid)) return false;
    Grid that = (Grid) object;
    return Arrays.equals(this.squares, that.squares);
  }

  @Override public Numeral get(Object key) {
    return get((Location) key);
  }

  public Numeral get(Location loc) {
    return numeral(squares[loc.index]);
  }

  @Override public int hashCode() {
    return Arrays.hashCode(squares);
  }

  @Override public Numeral put(Location key, Numeral value) {
    throw new UnsupportedOperationException();
  }

  @Override public Numeral remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override public int size() {
    int answer = 0;
    for (int square : squares) {
      if (square > 0) ++answer;
    }
    return answer;
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Row row : Row.ALL) {
      for (Location loc : row) {
        if (containsKey(loc)) sb.append(' ').append(get(loc).number);
        else sb.append(" .");
        if (loc.column.number == 3 || loc.column.number == 6)
          sb.append(" |");
      }
      sb.append('\n');
      if (row.number == 3 || row.number == 6)
        sb.append("-------+-------+-------\n");
    }
    return sb.toString();
  }

  /**
   * Generates a string of 81 characters with dots for unset locations and
   * digits for set ones.
   */
  public String toFlatString() {
    StringBuilder sb = new StringBuilder();
    for (byte square : squares)
      sb.append(square == 0 ? '.' : (char) ('0' + square));
    return sb.toString();
  }

  /**
   * Ignores all characters except digits and periods, requires there to be 81
   * total.
   */
  public static Grid fromString(String s) {
    Builder builder = new Builder();
    int index = 0;
    for (char c : s.toCharArray()) {
      if (c >= '1' && c <= '9')
        builder.put(Location.of(index++), Numeral.of(c - '0'));
      else if (c == '0' || c == '.')
        ++index;
    }
    if (index != 81) {
      throw new IllegalArgumentException(
          String.format("Grid.fromString requires 81 locations, got %d in %s", index, s));
    }
    return builder.build();
  }

  private class EntrySet extends AbstractSet<Entry<Location, Numeral>>
      implements Set<Entry<Location, Numeral>> {

    @Override public boolean add(Entry<Location, Numeral> entry) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean addAll(Collection<? extends Entry<Location, Numeral>> entries) {
      throw new UnsupportedOperationException();
    }

    @Override public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override public boolean contains(Object object) {
      if (object instanceof Entry) {
        Entry<?, ?> entry = (Entry<?, ?>) object;
        return Grid.this.containsKey(entry.getKey())
            && Grid.this.get(entry.getKey()).equals(entry.getValue());
      }
      return false;
    }

    @Override public boolean isEmpty() {
      return Grid.this.isEmpty();
    }

    @Override public Iterator<Entry<Location, Numeral>> iterator() {
      return new Iter();
    }

    @Override public boolean remove(Object object) {
      throw new UnsupportedOperationException();
    }

    @Override public int size() {
      return Grid.this.size();
    }
  }

  private class Iter implements Iterator<Entry<Location, Numeral>> {
    private int nextIndex;

    private Iter() {
      stepIndex();
    }

    @Override public boolean hasNext() {
      return nextIndex < 81 && squares[nextIndex] > 0;
    }

    @Override public Entry<Location, Numeral> next() {
      if (nextIndex >= 81)
        throw new NoSuchElementException();
      GridEntry answer = new GridEntry(nextIndex++);
      stepIndex();
      return answer;
    }

    private void stepIndex() {
      while (nextIndex < 81 && squares[nextIndex] == 0)
        ++nextIndex;
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class GridEntry implements Entry<Location, Numeral> {
    private final int index;
    private GridEntry(int index) {
      this.index = index;
    }

    @Override public Location getKey() {
      return Location.of(index);
    }

    @Override public Numeral getValue() {
      return Grid.this.get(getKey());
    }

    @Override public Numeral setValue(Numeral num) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof Entry)) return false;
      Entry<?, ?> that = (Entry<?, ?>) o;
      return this.getKey().equals(that.getKey())
          && this.getValue().equals(that.getValue());
    }

    @Override public int hashCode() {
      return getKey().hashCode() ^ getValue().hashCode();
    }

    @Override public String toString() {
      return getKey() + "=" + getValue();
    }
  }
}
