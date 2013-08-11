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

import com.google.appengine.labs.repackaged.com.google.common.collect.ImmutableMap;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.SignedBytes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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
public abstract class Pattern implements Comparable<Pattern> {

  private static final Splitter COLON_SPLITTER = Splitter.on(':');
  private static final Splitter COLON_SPLITTER_2 = COLON_SPLITTER.limit(2);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();
  private static final Splitter STAR_SPLITTER_2 = Splitter.on('*').limit(2);

  private final Type type;

  Pattern(Type type) {
    this.type = type;
  }

  public final Type getType() {
    return type;
  }

  public Appendable appendTo(Appendable a) throws IOException {
    a.append(type.getName()).append(':');
    appendGutsTo(a);
    return a;
  }

  public StringBuilder appendTo(StringBuilder sb) {
    try {
      appendTo((Appendable) sb);
      return sb;
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static Pattern fromString(String patternString) {
    Iterator<String> pieces = COLON_SPLITTER_2.split(patternString).iterator();
    Type type = Type.fromName(pieces.next());
    String params = pieces.next();
    switch (type) {
      case CONFLICT:
        return conflict(UnitCategory.fromString(params));
      case BARRED_LOCATION:
        return barredLocation(PeerMetrics.fromString(params));
      case BARRED_NUMERAL:
        return barredNumeral(UnitCategory.fromString(params));
      case FORCED_LOCATION:
        return forcedLocation(UnitCategory.fromString(params));
      case FORCED_NUMERAL:
        return forcedNumeral(PeerMetrics.fromString(params));
      case OVERLAP:
        return overlap(UnitCategory.fromString(params));
      case LOCKED_SET:
        return LockedSet.fromString(params);
      case IMPLICATION:
        return Implication.fromString(params);
      default:
        throw new IllegalArgumentException(patternString);
    }
  }

  public static Appendable appendTo(Appendable a, Iterable<? extends Pattern> patterns) throws IOException {
    return Joiner.on(',').appendTo(a, patterns);
  }

  public static Appendable appendTo(Appendable a, Multiset<? extends Pattern> patterns) throws IOException {
    boolean one = false;
    for (Multiset.Entry<? extends Pattern> entry : patterns.entrySet()) {
      if (one) a.append(',');
      else one = true;
      entry.getElement().appendTo(a);
      a.append('*').append(String.valueOf(entry.getCount()));
    }
    return a;
  }

  public static List<Pattern> listFromString(String s) {
    ImmutableList.Builder<Pattern> builder = ImmutableList.builder();
    for (String piece : COMMA_SPLITTER.split(s)) {
      builder.add(fromString(piece));
    }
    return builder.build();
  }

  public static Multiset<Pattern> multisetFromString(String s) {
    ImmutableMultiset.Builder<Pattern> builder = ImmutableMultiset.builder();
    for (String piece : COMMA_SPLITTER.split(s)) {
      Iterator<String> iter = STAR_SPLITTER_2.split(piece).iterator();
      builder.setCount(fromString(iter.next()), Integer.parseInt(iter.next()));
    }
    return builder.build();
  }

  public enum Type {
    CONFLICT("c"),
    BARRED_LOCATION("bl"),
    BARRED_NUMERAL("bn"),
    FORCED_LOCATION("fl"),
    FORCED_NUMERAL("fn"),
    OVERLAP("o"),
    LOCKED_SET("s"),
    IMPLICATION("i");

    private final String name;
    private static final ImmutableMap<String, Type> byName;
    static {
      ImmutableMap.Builder<String, Type> builder = ImmutableMap.builder();
      for (Type t : values())
        builder.put(t.name, t);
      byName = builder.build();
    }

    private Type(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public static Type fromName(String name) {
      Type answer = byName.get(name);
      if (answer == null) throw new IllegalArgumentException();
      return answer;
    }
  };

  @Override public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  @Override public int compareTo(Pattern that) {
    int answer = this.type.compareTo(that.type);
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();

  protected abstract Appendable appendGutsTo(Appendable a) throws IOException;
  protected abstract int compareToGuts(Pattern that);

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

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return a.append(category.toString());
    }

    @Override protected int compareToGuts(Pattern p) {
      UnitBased that = (UnitBased) p;
      return this.category.compareTo(that.category);
    }
  }

  /**
   * For patterns that require enumerating the numerals that affect a particular
   * location, this class counts those adjacent numerals in detail.
   */
  public static final class PeerMetrics implements Comparable<PeerMetrics> {
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

    public Appendable appendTo(Appendable a) throws IOException {
      for (int count : blockCounts) a.append(String.valueOf(count));
      a.append(':');
      appendCoordinatesTo(line1Coordinates, a);
      a.append(':');
      appendCoordinatesTo(line2Coordinates, a);
      return a;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      try {
        appendTo(sb);
      } catch (IOException impossible) {
        throw new AssertionError(impossible);
      }
      return sb.toString();
    }

    private void appendCoordinatesTo(byte[] coords, Appendable a) throws IOException {
      for (int coord : coords) {
        if (coord == 0) a.append('-');
        else if (coord > 0) a.append(String.valueOf(coord));
        else a.append((char)('a' - coord - 1));  // -1 => a, -2 => b, etc
      }
    }

    public static PeerMetrics fromString(String s) {
      return fromPieces(COLON_SPLITTER.split(s).iterator());
    }

    private static PeerMetrics fromPieces(Iterator<String> pieces) {
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

    @Override public int compareTo(PeerMetrics that) {
      Comparator<byte[]> comp = SignedBytes.lexicographicalComparator();
      return ComparisonChain.start()
          .compare(this.blockCounts, that.blockCounts, comp)
          .compare(this.line1Coordinates, that.line1Coordinates, comp)
          .compare(this.line2Coordinates, that.line2Coordinates, comp)
          .result();
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

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return metrics.appendTo(a);
    }

    @Override protected int compareToGuts(Pattern p) {
      PeerMetricsBased that = (PeerMetricsBased) p;
      return this.metrics.compareTo(that.metrics);
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

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return super.appendGutsTo(a)
          .append(':')
          .append(String.valueOf(setSize))
          .append(':')
          .append(isNaked ? 'n' : 'h');
    }

    public static LockedSet fromString(String s) {
      return fromPieces(COLON_SPLITTER.split(s).iterator());
    }

    private static LockedSet fromPieces(Iterator<String> pieces) {
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

  /**
   * A collection of antecedent patterns leading to a consequent pattern.
   */
  public static class Implication extends Pattern {
    private final List<Pattern> antecedents;
    private final Pattern consequent;

    public Implication(Collection<? extends Pattern> antecedents, Pattern consequent) {
      super(Type.IMPLICATION);
      checkArgument(antecedents.size() > 0);
      Pattern[] a = antecedents.toArray(new Pattern[antecedents.size()]);
      Arrays.sort(a);
      for (Pattern p : a) {
        checkNotNull(p);
        checkArgument(p.getType() != Type.IMPLICATION);
      }
      this.antecedents = Arrays.asList(a);
      this.consequent = checkNotNull(consequent);
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof Implication)) return false;
      Implication that = (Implication) o;
      return this.antecedents.equals(that.antecedents)
          && this.consequent.equals(that.consequent);
    }

    @Override public int hashCode() {
      return Objects.hashCode(antecedents, consequent);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      Joiner.on('+').appendTo(a, antecedents).append('=');
      return consequent.appendTo(a);
    }

    private static final Splitter EQUALS_SPLITTER_2 = Splitter.on('=').limit(2);
    private static final Splitter PLUS_SPLITTER = Splitter.on('+');

    public static Implication fromString(String s) {
      Iterator<String> iter = EQUALS_SPLITTER_2.split(s).iterator();
      String as = iter.next();
      String c = iter.next();
      List<Pattern> antecedents = Lists.newArrayList();
      for (String a : PLUS_SPLITTER.split(as))
        antecedents.add(Pattern.fromString(a));
      return new Implication(antecedents, Pattern.fromString(c));
    }

    @Override protected int compareToGuts(Pattern p) {
      Implication that = (Implication) p;
      Ordering<Iterable<Pattern>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, ordering)
          .compare(this.consequent, that.consequent)
          .result();
    }
  }

  public static Implication implication(Collection<? extends Pattern> antecedents, Pattern consequent) {
    return new Implication(antecedents, consequent);
  }
}
