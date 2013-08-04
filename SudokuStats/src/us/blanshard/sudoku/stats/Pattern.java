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
package us.blanshard.sudoku.stats;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.insight.Insight.Type;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.SignedBytes;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

import javax.annotation.concurrent.Immutable;

/**
 * A set of classes that categorize atomic insights by the pattern that must be
 * discerned in the Sudoku board to find them.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Pattern {

  private static final Splitter COLON_SPLITTER = Splitter.on(':');

  private final Type type;

  Pattern(Type type) {
    this.type = type;
  }

  public final Type getType() {
    return type;
  }

  @Override public final String toString() {
    return typeNames.inverse().get(type) + ':' + toStringGuts();
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();

  public static Pattern fromString(String patternString) {
    Iterator<String> pieces = COLON_SPLITTER.split(patternString).iterator();
    Type type = typeNames.get(pieces.next());
    switch (type) {
      case CONFLICT:
        return conflict(UnitCategory.fromPieces(pieces));
      case BARRED_LOCATION:
        return barredLocation(PeerMetrics.fromPieces(pieces));
      case BARRED_NUMERAL:
        return barredNumeral(UnitCategory.fromPieces(pieces));
      case FORCED_LOCATION:
        return forcedLocation(UnitCategory.fromPieces(pieces));
      case FORCED_NUMERAL:
        return forcedNumeral(PeerMetrics.fromPieces(pieces));
      case OVERLAP:
        return overlap(UnitCategory.fromPieces(pieces));
      case LOCKED_SET:
        return LockedSet.fromPieces(pieces);
      default:
        throw new IllegalArgumentException(patternString);
    }
  }

  protected abstract String toStringGuts();

  private static final ImmutableBiMap<String, Type> typeNames =
      ImmutableBiMap.<String, Type>builder()
          .put("c", Type.CONFLICT)
          .put("bl", Type.BARRED_LOCATION)
          .put("bn", Type.BARRED_NUMERAL)
          .put("fl", Type.FORCED_LOCATION)
          .put("fn", Type.FORCED_NUMERAL)
          .put("o", Type.OVERLAP)
          .put("s", Type.LOCKED_SET)
          .build();

  static int countOpenLocs(Grid grid, UnitSubset locs) {
    int numOpenLocs = 0;
    for (Location loc : locs)
      if (!grid.containsKey(loc))
        ++numOpenLocs;
    return numOpenLocs;
  }

  static boolean isImplicit(Grid grid, Location loc, Numeral num) {
    for (Location peer : loc.peers)
      if (grid.get(peer) == num) return false;
    return true;
  }

  static int countImplicit(Grid grid, NumSet nums, UnitSubset locs, int max) {
    int numImplicit = 0;
    for (Location loc : locs)
      for (Numeral num : nums)
        if (isImplicit(grid, loc, num)) {
          ++numImplicit;
          if (numImplicit >= max) return max;
        }
    return numImplicit;
  }

  /**
   * Many patterns refer to {@linkplain Unit units} on the Sudoku board; a given
   * pattern is generally easier to see for a block than for a row or column, so
   * we differentiate them with this enumeration.
   */
  public enum UnitCategory {
    BLOCK('b'), LINE('l');

    public static UnitCategory forUnit(Unit unit) {
      return unit.getType() == Unit.Type.BLOCK ? BLOCK : LINE;
    }

    public static UnitCategory value(int ordinal) {
      return VALUES[ordinal];
    }

    public static UnitCategory fromString(String s) {
      checkArgument(s.length() == 1);
      switch (s.charAt(0)) {
        case 'b': return BLOCK;
        case 'l': return LINE;
        default:
          throw new IllegalArgumentException();
      }
    }

    public static UnitCategory fromPieces(Iterator<String> pieces) {
      return fromString(Iterators.getOnlyElement(pieces));
    }

    @Override public String toString() {
      return String.valueOf(id);
    }

    private final char id;
    private static final UnitCategory[] VALUES = values();

    private UnitCategory(char id) {
      this.id = id;
    }
  }

  /**
   * For simple patterns that rely entirely on the category of unit they pertain
   * to, a common base class.
   */
  public static abstract class UnitBased extends Pattern {
    private final UnitCategory category;

    UnitBased(Type type, UnitCategory category) {
      super(type);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    @Override public boolean equals(Object o) {
      if (o == null) return false;
      if (o == this) return true;
      return o.getClass() == this.getClass()
          && ((UnitBased) o).category == this.category;
    }

    @Override public int hashCode() {
      return getClass().hashCode() ^ category.hashCode();
    }

    @Override protected String toStringGuts() {
      return category.toString();
    }
  }

  /**
   * For patterns that require enumerating the numerals that affect a particular
   * location, this class counts those adjacent numerals in detail.
   */
  public static final class PeerMetrics {
    /**
     * For each possible distance between two locations within a block, the
     * number of filled locations at that distance from the location in
     * question.  The distances are: 1, 1+1, 2, 2+1, and 2+2.
     */
    private final byte[] blockCounts;

    /**
     * For the numerals <b>not</b> in the block, the signed distance from the
     * location in question where each numeral lies along one of the location's
     * peer lines, ordered from most positive to most negative. Numerals not in
     * either the block nor this line are represented as zero, and they are
     * ordered last. There are particular rules for deciding which line is
     * counted as line 1 and which line 2, and which direction from the location
     * counts as positive and which negative, such that two similar patterns
     * always are counted in the same way.
     */
    private final byte[] line1Coordinates;

    /**
     * The same, but for the other line.  For line 2, the order matches that of
     * line 1, meaning that index 0 of both arrays refers to the coordinate of
     * the same numeral along both lines.
     */
    private final byte[] line2Coordinates;

    PeerMetrics(byte[] blockCounts, byte[] line1Coordinates, byte[] line2Coordinates) {
      this.blockCounts = blockCounts;
      this.line1Coordinates = line1Coordinates;
      this.line2Coordinates = line2Coordinates;
    }

    public byte[] getBlockCounts() {
      return blockCounts.clone();
    }

    public byte[] getLine1Coordinates() {
      return line1Coordinates.clone();
    }

    public byte[] getLine2Coordinates() {
      return line2Coordinates.clone();
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int count : blockCounts) sb.append(count);
      sb.append(':');
      appendCoordinates(line1Coordinates, sb);
      sb.append(':');
      appendCoordinates(line2Coordinates, sb);
      return sb.toString();
    }

    private void appendCoordinates(byte[] coords, StringBuilder sb) {
      for (int coord : coords) {
        if (coord == 0) sb.append('-');
        else if (coord > 0) sb.append(coord);
        else sb.append((char)('a' - coord - 1));  // -1 => a, -2 => b, etc
      }
    }

    public static PeerMetrics fromString(String s) {
      return fromPieces(COLON_SPLITTER.split(s).iterator());
    }

    public static PeerMetrics fromPieces(Iterator<String> pieces) {
      String counts = pieces.next();
      String line1 = pieces.next();
      String line2 = Iterators.getOnlyElement(pieces);
      checkArgument(counts.length() == 5, "bad counts: %s", counts);
      checkArgument(line1.length() == line2.length(), "mismatched lines: %s vs %s", line1, line2);
      byte[] blockCounts = new byte[5];
      for (int i = 0; i < counts.length(); ++i)
        blockCounts[i] = (byte) (counts.charAt(i) - '0');
      byte[] line1Coordinates = new byte[line1.length()];
      byte[] line2Coordinates = new byte[line1.length()];
      for (int i = 0; i < line1.length(); ++i) {
        line1Coordinates[i] = fromCoordinate(line1.charAt(i));
        line2Coordinates[i] = fromCoordinate(line2.charAt(i));
      }
      return new PeerMetrics(blockCounts, line1Coordinates, line2Coordinates);
    }

    private static byte fromCoordinate(char c) {
      if (c == '-') return 0;
      if (c >= '0' && c <= '9') return (byte) (c - '0');
      if (c >= 'a' && c <= 'h') return (byte) ('a' - c - 1);
      throw new IllegalArgumentException();
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof PeerMetrics)) return false;
      PeerMetrics that = (PeerMetrics) o;
      return Arrays.equals(this.blockCounts, that.blockCounts)
          && Arrays.equals(this.line1Coordinates, that.line1Coordinates)
          && Arrays.equals(this.line2Coordinates, that.line2Coordinates);
    }

    @Override public int hashCode() {
      return Arrays.hashCode(blockCounts)
          + Arrays.hashCode(line1Coordinates)
          + Arrays.hashCode(line2Coordinates);
    }
  }

  /**
   * Calculates the peer metrics for a location in a grid.
   */
  public static PeerMetrics peerMetrics(Grid grid, Location loc) {
    byte[] blockCounts = new byte[5];
    for (Location l2 : loc.block)
      if (l2 != loc && grid.containsKey(l2)) {
        int index;
        int i1 = Math.abs(l2.row.index - loc.row.index);
        int i2 = Math.abs(l2.column.index - loc.column.index);
        if (i1 == 0) index = i2 == 1 ? 0 : 2;
        else if (i2 == 0) index = i1 == 1 ? 0 : 2;
        else if (i1 == 1 && i2 == 1) index = 1;
        else if (i1 == 2 && i2 == 2) index = 4;
        else index = 3;
        ++blockCounts[index];
      }

    NumSet remaining = loc.block.getMissing(grid);
    BiMap<Numeral, Integer> rowCoordinates = HashBiMap.create();
    BiMap<Numeral, Integer> colCoordinates = HashBiMap.create();
    for (Location l2 : loc.row) {
      Numeral num = grid.get(l2);
      if (num != null && remaining.contains(num)) {
        int diff = l2.column.index - loc.column.index;
        rowCoordinates.put(num, diff);
      }
    }
    for (Location l2 : loc.column) {
      Numeral num = grid.get(l2);
      if (num != null && remaining.contains(num)) {
        int diff = l2.row.index - loc.row.index;
        colCoordinates.put(num, diff);
      }
    }

    boolean isRowLine1 = isRowLine1(rowCoordinates, colCoordinates);
    BiMap<Numeral, Integer> line1 = isRowLine1 ? rowCoordinates : colCoordinates;
    BiMap<Numeral, Integer> line2 = isRowLine1 ? colCoordinates : rowCoordinates;
    boolean negate1 = shouldNegateLine(line1.inverse(), line2);
    boolean negate2 = shouldNegateLine(line2.inverse(), line1);

    byte[] line1Coordinates = new byte[remaining.size()];
    byte[] line2Coordinates = new byte[remaining.size()];
    int index = 0;
    for (Map.Entry<Integer, Numeral> e : invertAndSort(line1, negate1).entrySet()) {
      line1Coordinates[index] = (byte) e.getKey().intValue();
      if (line2.containsKey(e.getValue())) {
        int coord = line2.get(e.getValue());
        line2Coordinates[index] = (byte) (negate2 ? -coord : coord);
        line2.remove(e.getValue());
      }
      ++index;
    }
    for (Integer coord : invertAndSort(line2, negate2).keySet())
      line2Coordinates[index++] = (byte) coord.intValue();

    return new PeerMetrics(blockCounts, line1Coordinates, line2Coordinates);
  }

  private static boolean isRowLine1(
      Map<Numeral, Integer> rowCoordinates, Map<Numeral, Integer> colCoordinates) {
    if (rowCoordinates.size() > colCoordinates.size()) return true;
    if (rowCoordinates.size() < colCoordinates.size()) return false;

    byte[] rowDistances = toOrderedAbsoluteBytes(rowCoordinates.values());
    byte[] colDistances = toOrderedAbsoluteBytes(colCoordinates.values());
    return SignedBytes.lexicographicalComparator().compare(rowDistances, colDistances) >= 0;
  }

  private static boolean shouldNegateLine(Map<Integer, Numeral> line, Map<Numeral, Integer> opp) {
    byte[] bytes = toBytes(line.keySet());
    Arrays.sort(bytes);  // Sorts negatives first
    for (int i = 0; i < bytes.length; ++i) {
      int j = bytes.length - 1 - i;
      // Larger distances win:
      if (bytes[j] > -bytes[i]) return false;
      if (bytes[j] < -bytes[i]) return true;
    }
    for (int i = 0; true; ++i) {
      int j = bytes.length - 1 - i;
      if (i > j) break;
      // Larger distances on the opposite line win:
      Numeral ni = line.get((int) bytes[i]);
      Numeral nj = line.get((int) bytes[j]);
      Integer oi = opp.get(ni);
      Integer oj = opp.get(nj);
      if (oj != null && oi == null) return false;
      if (oj == null && oi != null) return true;
      if (oj != null) {
        if (Math.abs(oj) > Math.abs(oi)) return false;
        if (Math.abs(oj) < Math.abs(oi)) return true;
      }
    }
    return false;
  }

  private static byte[] toBytes(Collection<Integer> ints) {
    byte[] answer = new byte[ints.size()];
    int index = 0;
    for (int i : ints)
      answer[index++] = (byte) i;
    return answer;
  }

  private static byte[] toOrderedAbsoluteBytes(Collection<Integer> ints) {
    byte[] answer = toBytes(ints);
    for (int i = 0; i < answer.length; ++i)
      if (answer[i] < 0) answer[i] = (byte) -answer[i];
    Collections.sort(Bytes.asList(answer), Ordering.natural().reverse());
    return answer;
  }

  private static SortedMap<Integer, Numeral> invertAndSort(
      Map<Numeral, Integer> line, boolean negate) {
    SortedMap<Integer, Numeral> sorted = Maps.newTreeMap(Ordering.natural().reverse());
    for (Map.Entry<Numeral, Integer> e : line.entrySet())
      sorted.put(negate ? -e.getValue() : e.getValue(), e.getKey());
    return sorted;
  }

  /**
   * For patterns that rely on peer metrics, a common base class.
   */
  public static abstract class PeerMetricsBased extends Pattern {
    private final PeerMetrics metrics;

    PeerMetricsBased(Type type, PeerMetrics metrics) {
      super(type);
      this.metrics = metrics;
    }

    public PeerMetrics getMetrics() {
      return metrics;
    }

    @Override public boolean equals(Object o) {
      if (o == null) return false;
      if (o == this) return true;
      return o.getClass() == this.getClass()
          && ((PeerMetricsBased) o).metrics.equals(this.metrics);
    }

    @Override public int hashCode() {
      return getClass().hashCode() ^ metrics.hashCode();
    }

    @Override protected String toStringGuts() {
      return metrics.toString();
    }
  }

  /**
   * The patterns for an actual conflict on the board: one pattern instance per
   * unit category.
   */
  public static final class Conflict extends UnitBased {

    private Conflict(UnitCategory category) {
      super(Type.CONFLICT, category);
    }

    static final Conflict BLOCK = new Conflict(UnitCategory.BLOCK);
    static final Conflict LINE = new Conflict(UnitCategory.LINE);
  }

  public static Conflict conflict(UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ? Conflict.BLOCK : Conflict.LINE;
  }

  public static Conflict conflict(Unit unit) {
    return conflict(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, metrics);
    }
  }

  public static BarredLoc barredLocation(PeerMetrics metrics) {
    return new BarredLoc(metrics);
  }

  public static BarredLoc barredLocation(Grid grid, Location loc) {
    return barredLocation(peerMetrics(grid, loc));
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.
   */
  public static final class BarredNum extends UnitBased {
    private BarredNum(UnitCategory category) {
      super(Type.BARRED_NUMERAL, category);
    }

    static final BarredNum BLOCK = new BarredNum(UnitCategory.BLOCK);
    static final BarredNum LINE = new BarredNum(UnitCategory.LINE);
  }

  public static BarredNum barredNumeral(UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ? BarredNum.BLOCK : BarredNum.LINE;
  }

  public static BarredNum barredNumeral(Unit unit) {
    return barredNumeral(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit.
   */
  public static final class ForcedLoc extends UnitBased {
    private ForcedLoc(UnitCategory category) {
      super(Type.FORCED_LOCATION, category);
    }

    static final ForcedLoc BLOCK = new ForcedLoc(UnitCategory.BLOCK);
    static final ForcedLoc LINE = new ForcedLoc(UnitCategory.LINE);
  }

  public static ForcedLoc forcedLocation(UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ? ForcedLoc.BLOCK : ForcedLoc.LINE;
  }

  public static ForcedLoc forcedLocation(Unit unit) {
    return forcedLocation(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, metrics);
    }
  }

  public static ForcedNum forcedNumeral(PeerMetrics metrics) {
    return new ForcedNum(metrics);
  }

  public static ForcedNum forcedNumeral(Grid grid, Location loc) {
    return new ForcedNum(peerMetrics(grid, loc));
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.
   */
  public static final class Overlap extends UnitBased {
    private Overlap(UnitCategory category) {
      super(Type.OVERLAP, category);
    }

    static final Overlap BLOCK = new Overlap(UnitCategory.BLOCK);
    static final Overlap LINE = new Overlap(UnitCategory.LINE);
  }

  public static Overlap overlap(UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ? Overlap.BLOCK : Overlap.LINE;
  }

  public static Overlap overlap(Unit unit) {
    return overlap(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.
   */
  public static final class LockedSet extends UnitBased {
    private final int setSize;
    private final boolean isNaked;

    LockedSet(UnitCategory category, int setSize, boolean isNaked) {
      super(Type.LOCKED_SET, category);
      this.setSize = setSize;
      this.isNaked = isNaked;
    }

    public int getSetSize() {
      return setSize;
    }

    public boolean isNaked() {
      return isNaked;
    }

    @Override public boolean equals(Object o) {
      if (super.equals(o)) {
        LockedSet that = (LockedSet) o;
        return this.setSize == that.setSize
            && this.isNaked == that.isNaked;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), setSize, isNaked);
    }

    @Override protected String toStringGuts() {
      return super.toStringGuts() + ':' + setSize + ':' + (isNaked ? 'n' : 'h');
    }

    public static LockedSet fromPieces(Iterator<String> pieces) {
      UnitCategory category = UnitCategory.fromString(pieces.next());
      int setSize = Integer.parseInt(pieces.next());
      String s = Iterators.getOnlyElement(pieces);
      checkArgument(s.length() == 1);
      boolean isNaked;
      switch (s.charAt(0)) {
        case 'n': isNaked = true;  break;
        case 'h': isNaked = false; break;
        default: throw new IllegalArgumentException(s);
      }
      return lockedSet(category, setSize, isNaked);
    }
  }

  public static LockedSet lockedSet(UnitCategory category, int setSize, boolean isNaked) {
    return new LockedSet(category, setSize, isNaked);
  }
}
