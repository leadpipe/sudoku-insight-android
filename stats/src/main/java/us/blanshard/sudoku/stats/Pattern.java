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

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Marks;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A set of classes that categorize insights by the pattern that must be
 * discerned in the Sudoku board to find them.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Pattern implements Comparable<Pattern> {

  private static final Splitter COLON_SPLITTER = Splitter.on(':');
  private static final Splitter COLON_SPLITTER_3 = COLON_SPLITTER.limit(3);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();
  private static final Splitter SEMI_SPLITTER = Splitter.on(';').omitEmptyStrings();

  private final Type type;
  private final boolean sameNumeral;
  @Nullable private final Evaluator.Pattern evaluatorPattern;

  Pattern(Type type, boolean sameNumeral, @Nullable Evaluator.Pattern evaluatorPattern) {
    this.type = type;
    this.sameNumeral = sameNumeral;
    this.evaluatorPattern = evaluatorPattern;
  }

  public final Type getType() {
    return type;
  }

  public final boolean isSameNumeral() {
    return sameNumeral;
  }

  @Nullable public final Evaluator.Pattern getEvaluatorPattern() {
    return evaluatorPattern;
  }

  public Pattern getNub() {
    return this;
  }

  public boolean isDirectAssignment() {
    switch (type) {
      case FORCED_LOCATION:
      case FORCED_NUMERAL:
        return true;
      default:
        return false;
    }
  }

  public boolean isAssignment() {
    return getNub().isDirectAssignment();
  }

  public Appendable appendTo(Appendable a) throws IOException {
    a.append(type.getName()).append(':')
        .append(sameNumeral ? '!' : '-')
        .append(String.valueOf(evaluatorPattern == null ? -1 : evaluatorPattern.ordinal()))
        .append(':');
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
    Iterator<String> pieces = COLON_SPLITTER_3.split(patternString).iterator();
    Type type = Type.fromName(pieces.next());
    String s = pieces.next();
    boolean sameNumeral = s.charAt(0) == '!';
    int patternOrdinal = Integer.parseInt(s.substring(1));
    Evaluator.Pattern evaluatorPattern = patternOrdinal < 0 ? null : Evaluator.Pattern.values()[patternOrdinal];
    String params = pieces.next();
    switch (type) {
      case CONFLICT:
        return conflict(sameNumeral, UnitCategory.fromString(params));
      case BARRED_LOCATION:
        return barredLocation(evaluatorPattern, PeerMetrics.fromString(params));
      case BARRED_NUMERAL:
        return barredNumeral(sameNumeral, UnitCategory.fromString(params));
      case FORCED_LOCATION:
        return forcedLocation(sameNumeral, UnitCategory.fromString(params));
      case FORCED_NUMERAL:
        return forcedNumeral(sameNumeral, evaluatorPattern, PeerMetrics.fromString(params));
      case OVERLAP:
        return overlap(sameNumeral, UnitCategory.fromString(params));
      case LOCKED_SET:
        return LockedSet.fromString(sameNumeral, evaluatorPattern, params);
      case IMPLICATION:
        return Implication.fromString(params);
      default:
        throw new IllegalArgumentException(patternString);
    }
  }

  public static Appendable appendTo(Appendable a, Coll coll) throws IOException {
    return Joiner.on(',').appendTo(a, coll.patterns);
  }

  public static Appendable appendAllTo(Appendable a, Iterable<Coll> colls) throws IOException {
    boolean oneDone = false;
    for (Coll coll : colls) {
      if (oneDone) a.append(';');
      appendTo(a, coll);
      oneDone = true;
    }
    return a;
  }

  public static Coll collFromString(String s) {
    ImmutableList.Builder<Pattern> builder = ImmutableList.builder();
    for (String piece : COMMA_SPLITTER.split(s)) {
      builder.add(fromString(piece));
    }
    return new Coll(builder.build());
  }

  public static List<Coll> collsFromString(String s) {
    ImmutableList.Builder<Coll> builder = ImmutableList.builder();
    for (String piece : SEMI_SPLITTER.split(s)) {
      builder.add(collFromString(piece));
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
  }

  @Override public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  @Override public int compareTo(Pattern that) {
    int answer = ComparisonChain.start()
        .compare(this.type, that.type)
        .compareTrueFirst(this.sameNumeral, that.sameNumeral)
        .result();
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();

  protected abstract Appendable appendGutsTo(Appendable a) throws IOException;
  protected abstract int compareToGuts(Pattern that);

  public static class Coll {
    public final List<? extends Pattern> patterns;

    public Coll(List<? extends Pattern> patterns) {
      this.patterns = patterns;
    }

    public boolean areAllImplications() {
      for (Pattern p : patterns)
        if (p.type != Type.IMPLICATION) return false;
      return true;
    }
  }

  /**
   * Many patterns refer to {@linkplain Unit units} on the Sudoku board; a given
   * pattern is generally easier to see for a block than for a row or column, so
   * we differentiate them with this enumeration.
   */
  public enum UnitCategory {
    BLOCK('b'), LINE('l');

    public static UnitCategory forUnit(Unit unit) {
      return unit.type == Unit.Type.BLOCK ? BLOCK : LINE;
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

    UnitBased(Type type, boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category) {
      super(type, sameNumeral, evaluatorPattern);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    @Override public boolean equals(Object o) {
      if (o == null || o.getClass() != this.getClass()) return false;
      if (o == this) return true;
      UnitBased that = (UnitBased) o;
      return this.isSameNumeral() == that.isSameNumeral()
          && this.category == that.category;
    }

    @Override public int hashCode() {
      return Objects.hashCode(getClass(), isSameNumeral(), category);
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
   * location, this class classifies those adjacent numerals in detail.
   */
  public static final class PeerMetrics implements Comparable<PeerMetrics> {
    /**
     * For each location in the target location's units, a byte indicating the
     * category of the location. There are 27 bytes in the array, one for each
     * location in the block, row, and column (note there are overlaps). 0 means
     * the location is unset. 8 means the location is the target location. Other
     * values use the bits 1, 2, and 4 to indicate which of the block, row, and
     * column the numeral in question is set in.
     */
    private final byte[] categories;

    PeerMetrics(byte[] categories) {
      this.categories = categories;
    }

    public static final byte UNSET = 0;
    public static final byte TARGET = 8;
    public static final byte BLOCK_BIT = 1;
    public static final byte ROW_BIT = 2;
    public static final byte COLUMN_BIT = 4; // unitBit(Unit.Type.COLUMN);

    public byte[] getBlockCategories() {
      return getUnitCategories(Unit.Type.BLOCK);
    }

    public byte[] getRowCategories() {
      return getUnitCategories(Unit.Type.ROW);
    }

    public byte[] getColumnCategories() {
      return getUnitCategories(Unit.Type.COLUMN);
    }

    public byte[] getUnitCategories(Unit.Type type) {
      byte[] answer = new byte[9];
      System.arraycopy(categories, type.ordinal() * 9, answer, 0, 9);
      return answer;
    }

    public static int unitBit(Unit.Type type) {
      return 1 << type.ordinal();
    }

    /**
     * Returns the location category for the given unit and index within that
     * unit.
     */
    public byte getLocationCategory(Unit.Type type, int index) {
      return categories[type.ordinal() * 9 + index];
    }

    public Appendable appendTo(Appendable a) throws IOException {
      for (Unit.Type type : Unit.Type.values()) {
        if (type.ordinal() > 0)
          a.append(':');
        for (int index = 0; index < 9; ++index)
          a.append((char) ('0' + getLocationCategory(type, index)));
      }
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

    static void appendCoordinatesTo(byte[] coords, Appendable a) throws IOException {
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
      byte[] categories = new byte[27];
      int index = 0;
      while (pieces.hasNext()) {
        String piece = pieces.next();
        for (int j = 0; j < piece.length(); ++j)
          categories[index++] = (byte) (piece.charAt(j) - '0');
      }
      if (index != categories.length)
        throw new IllegalArgumentException();
      return new PeerMetrics(categories);
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof PeerMetrics)) return false;
      PeerMetrics that = (PeerMetrics) o;
      return Arrays.equals(this.categories, that.categories);
    }

    @Override public int hashCode() {
      return Arrays.hashCode(categories);
    }

    @Override public int compareTo(PeerMetrics that) {
      Comparator<byte[]> comp = UnsignedBytes.lexicographicalComparator();
      return comp.compare(this.categories, that.categories);
    }
  }

  /**
   * Calculates the peer metrics for a location in a grid.
   */
  public static PeerMetrics peerMetrics(Marks marks, Location loc) {
    NumSet[] unitNums = {NumSet.NONE, NumSet.NONE, NumSet.NONE};
    for (Unit.Type type : Unit.Type.values())
      for (Location peer : loc.unit(type))
        if (peer != loc && marks.hasAssignment(peer))
          unitNums[type.ordinal()] = unitNums[type.ordinal()].with(marks.get(peer));

    byte[] categories = new byte[27];
    int index = 0;
    for (Unit.Type type : Unit.Type.values())
      for (Location peer : loc.unit(type)) {
        byte category = PeerMetrics.UNSET;
        if (peer == loc)
          category = PeerMetrics.TARGET;
        else if (marks.hasAssignment(peer)) {
          Numeral numeral = marks.get(peer);
          for (Unit.Type t2 : Unit.Type.values())
            if (unitNums[t2.ordinal()].contains(numeral))
              category |= PeerMetrics.unitBit(t2);
        }
        categories[index++] = category;
      }

    return new PeerMetrics(categories);
  }

  /**
   * For patterns that rely on peer metrics, a common base class.
   */
  public static abstract class PeerMetricsBased extends Pattern {
    private final PeerMetrics metrics;

    PeerMetricsBased(Type type, boolean sameNumeral, Evaluator.Pattern evaluatorPattern, PeerMetrics metrics) {
      super(type, sameNumeral, evaluatorPattern);
      this.metrics = metrics;
    }

    public PeerMetrics getMetrics() {
      return metrics;
    }

    @Override public boolean equals(Object o) {
      if (o == null || o.getClass() != this.getClass()) return false;
      if (o == this) return true;
      PeerMetricsBased that = (PeerMetricsBased) o;
      return this.isSameNumeral() == that.isSameNumeral()
          && this.metrics.equals(that.metrics);
    }

    @Override public int hashCode() {
      return getClass().hashCode() ^ metrics.hashCode() + (isSameNumeral() ? 1 : 0);
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

    private Conflict(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category) {
      super(Type.CONFLICT, sameNumeral, evaluatorPattern, category);
    }

    static final Conflict BLOCK_SAME = new Conflict(true,  Evaluator.Pattern.CONFLICT_B, UnitCategory.BLOCK);
    static final Conflict BLOCK_DIFF = new Conflict(false, Evaluator.Pattern.CONFLICT_B, UnitCategory.BLOCK);
    static final Conflict LINE_SAME = new Conflict(true,  Evaluator.Pattern.CONFLICT_L, UnitCategory.LINE);
    static final Conflict LINE_DIFF = new Conflict(false, Evaluator.Pattern.CONFLICT_L, UnitCategory.LINE);
  }

  public static Conflict conflict(boolean sameNumeral, UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ?
        sameNumeral ? Conflict.BLOCK_SAME : Conflict.BLOCK_DIFF :
        sameNumeral ? Conflict.LINE_SAME : Conflict.LINE_DIFF;
  }

  public static Conflict conflict(boolean sameNumeral, Unit unit) {
    return conflict(sameNumeral, UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(Evaluator.Pattern evaluatorPattern, PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, true, evaluatorPattern, metrics);
    }
  }

  public static BarredLoc barredLocation(Evaluator.Pattern evaluatorPattern, PeerMetrics metrics) {
    return new BarredLoc(evaluatorPattern, metrics);
  }

  public static BarredLoc barredLocation(Evaluator.Pattern evaluatorPattern, Location loc, Marks marks) {
    return barredLocation(evaluatorPattern, peerMetrics(marks, loc));
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.
   */
  public static final class BarredNum extends UnitBased {
    private BarredNum(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category) {
      super(Type.BARRED_NUMERAL, sameNumeral, evaluatorPattern, category);
    }

    static final BarredNum BLOCK_SAME = new BarredNum(true,  Evaluator.Pattern.BARRED_NUM_B, UnitCategory.BLOCK);
    static final BarredNum BLOCK_DIFF = new BarredNum(false, Evaluator.Pattern.BARRED_NUM_B, UnitCategory.BLOCK);
    static final BarredNum LINE_SAME = new BarredNum(true,  Evaluator.Pattern.BARRED_NUM_L, UnitCategory.LINE);
    static final BarredNum LINE_DIFF = new BarredNum(false, Evaluator.Pattern.BARRED_NUM_L, UnitCategory.LINE);
  }

  public static BarredNum barredNumeral(boolean sameNumeral, UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ?
        sameNumeral ? BarredNum.BLOCK_SAME : BarredNum.BLOCK_DIFF :
        sameNumeral ? BarredNum.LINE_SAME : BarredNum.LINE_DIFF;
  }

  public static BarredNum barredNumeral(boolean sameNumeral, Unit unit) {
    return barredNumeral(sameNumeral, UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit.
   */
  public static final class ForcedLoc extends UnitBased {
    private ForcedLoc(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category) {
      super(Type.FORCED_LOCATION, sameNumeral, evaluatorPattern, category);
    }

    static final ForcedLoc BLOCK_SAME = new ForcedLoc(true,  Evaluator.Pattern.FORCED_LOC_B, UnitCategory.BLOCK);
    static final ForcedLoc BLOCK_DIFF = new ForcedLoc(false, Evaluator.Pattern.FORCED_LOC_B, UnitCategory.BLOCK);
    static final ForcedLoc LINE_SAME = new ForcedLoc(true,  Evaluator.Pattern.FORCED_LOC_L, UnitCategory.LINE);
    static final ForcedLoc LINE_DIFF = new ForcedLoc(false, Evaluator.Pattern.FORCED_LOC_L, UnitCategory.LINE);
  }

  public static ForcedLoc forcedLocation(boolean sameNumeral, UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ?
        sameNumeral ? ForcedLoc.BLOCK_SAME : ForcedLoc.BLOCK_DIFF :
        sameNumeral ? ForcedLoc.LINE_SAME : ForcedLoc.LINE_DIFF;
  }

  public static ForcedLoc forcedLocation(boolean sameNumeral, Unit unit) {
    return forcedLocation(sameNumeral, UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, sameNumeral, evaluatorPattern, metrics);
    }
  }

  public static ForcedNum forcedNumeral(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, PeerMetrics metrics) {
    return new ForcedNum(sameNumeral, evaluatorPattern, metrics);
  }

  public static ForcedNum forcedNumeral(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, Marks marks, Location loc) {
    return new ForcedNum(sameNumeral, evaluatorPattern, peerMetrics(marks, loc));
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.
   */
  public static final class Overlap extends UnitBased {
    private Overlap(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category) {
      super(Type.OVERLAP, sameNumeral, evaluatorPattern, category);
    }

    static final Overlap BLOCK_SAME = new Overlap(true,  Evaluator.Pattern.OVERLAP_B, UnitCategory.BLOCK);
    static final Overlap BLOCK_DIFF = new Overlap(false, Evaluator.Pattern.OVERLAP_B, UnitCategory.BLOCK);
    static final Overlap LINE_SAME = new Overlap(true,  Evaluator.Pattern.OVERLAP_L, UnitCategory.LINE);
    static final Overlap LINE_DIFF = new Overlap(false, Evaluator.Pattern.OVERLAP_L, UnitCategory.LINE);
  }

  public static Overlap overlap(boolean sameNumeral, UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ?
        sameNumeral ? Overlap.BLOCK_SAME : Overlap.BLOCK_DIFF :
        sameNumeral ? Overlap.LINE_SAME : Overlap.LINE_DIFF;
  }

  public static Overlap overlap(boolean sameNumeral, Unit unit) {
    return overlap(sameNumeral, UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.
   */
  public static final class LockedSet extends UnitBased {
    private final int setSize;
    private final boolean isNaked;
    private final boolean isOverlapped;

    LockedSet(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, UnitCategory category, int setSize, boolean isNaked, boolean isOverlapped) {
      super(Type.LOCKED_SET, sameNumeral, evaluatorPattern, category);
      this.setSize = setSize;
      this.isNaked = isNaked;
      this.isOverlapped = isOverlapped;
    }

    public int getSetSize() {
      return setSize;
    }

    public boolean isNaked() {
      return isNaked;
    }

    public boolean isOverlapped() {
      return isOverlapped;
    }

    @Override public boolean equals(Object o) {
      if (super.equals(o)) {
        LockedSet that = (LockedSet) o;
        return this.setSize == that.setSize
            && this.isNaked == that.isNaked
            && this.isOverlapped == that.isOverlapped;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), setSize, isNaked, isOverlapped);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return super.appendGutsTo(a)
          .append(':')
          .append(String.valueOf(setSize))
          .append(':')
          .append(isNaked ? 'n' : 'h')
          .append(':')
          .append(isOverlapped ? 'o' : 'd');
    }

    public static LockedSet fromString(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, String s) {
      return fromPieces(sameNumeral, evaluatorPattern, COLON_SPLITTER.split(s).iterator());
    }

    private static LockedSet fromPieces(boolean sameNumeral, Evaluator.Pattern evaluatorPattern, Iterator<String> pieces) {
      UnitCategory category = UnitCategory.fromString(pieces.next());
      int setSize = Integer.parseInt(pieces.next());
      String s = pieces.next();
      checkArgument(s.length() == 1);
      boolean isNaked;
      switch (s.charAt(0)) {
        case 'n': isNaked = true;  break;
        case 'h': isNaked = false; break;
        default: throw new IllegalArgumentException(s);
      }
      s = Iterators.getOnlyElement(pieces);
      checkArgument(s.length() == 1);
      boolean isOverlapped;
      switch (s.charAt(0)) {
        case 'o': isOverlapped = true;  break;
        case 'd': isOverlapped = false; break;
        default: throw new IllegalArgumentException(s);
      }
      return new LockedSet(sameNumeral, evaluatorPattern, category, setSize, isNaked, isOverlapped);
    }
  }

  /**
   * Returns the difference between the given set and all numerals assigned to
   * locations within the given unit, in the given grid.
   */
  private static NumSet minusAllInUnit(NumSet nums, Unit unit, Marks marks) {
    return nums.minus(marks.getUnassignedNumerals(unit).not());
  }

  public static Pattern lockedSet(boolean sameNumeral, us.blanshard.sudoku.insight.LockedSet set, Marks marks) {
    UnitCategory category = UnitCategory.forUnit(set.getLocations().unit);
    int setSize = set.getLocations().size();
    boolean isNaked = set.isNakedSet();

    // Overlapped means that the set arises from assignments in overlapping
    // units.
    boolean isOverlapped = false;
    if (isNaked) {
      // For naked sets, this means that the set lies in two overlapping units
      // and all numerals not in the set appear in those two units.
      Unit overlap = set.getOverlappingUnit();
      if (overlap != null) {
        NumSet remaining = NumSet.ALL.minus(set.getNumerals());
        remaining = minusAllInUnit(remaining, set.getLocations().unit, marks);
        remaining = minusAllInUnit(remaining, overlap, marks);
        isOverlapped = remaining.isEmpty();
      }
    } else {
      // For hidden sets, it means that all open squares not in the set lie in
      // an overlapping unit, and all numerals in the set appear in this unit.
      UnitSubset taken = set.getLocations();
      for (Location loc : taken.unit) {
        if (marks.hasAssignment(loc))
          taken = taken.with(loc);
      }
      UnitSubset open = taken.not();
      Unit overlap = Analyzer.findOverlappingUnit(open);
      if (overlap == null && open.size() == 1) {
        Location loc = open.get(0);
        if (category == UnitCategory.LINE) {
          overlap = loc.block;
        } else {
          isOverlapped = minusAllInUnit(set.getNumerals(), loc.row, marks).isEmpty()
              || minusAllInUnit(set.getNumerals(), loc.column, marks).isEmpty();
        }
      }
      if (overlap != null) {
        isOverlapped = minusAllInUnit(set.getNumerals(), overlap, marks).isEmpty();
      }
    }
    return new LockedSet(sameNumeral, Evaluator.Pattern.forInsight(set, marks), category, setSize, isNaked, isOverlapped);
  }

  /**
   * A collection of antecedent patterns leading to a consequent pattern.
   */
  public static class Implication extends Pattern {
    private final List<Pattern> antecedents;
    private final Pattern consequent;

    public Implication(Collection<? extends Pattern> antecedents, Pattern consequent) {
      super(Type.IMPLICATION, true, null);
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

    public List<Pattern> getAntecedents() {
      return Collections.unmodifiableList(antecedents);
    }

    public Pattern getConsequent() {
      return consequent;
    }

    @Override public Pattern getNub() {
      return consequent.getNub();
    }

    public boolean hasSimpleAntecedents() {
      Implication p = this;
      do {
        for (Pattern a : p.getAntecedents()) {
          switch (a.getType()) {
          case OVERLAP:
            break;
          case LOCKED_SET: {
            Pattern.LockedSet l = (Pattern.LockedSet) a;
            if (l.isNaked() || l.getCategory() == Pattern.UnitCategory.LINE
                || l.getSetSize() > 3)
              return false;
            break;
          }
          default:
            return false;
          }
        }
        p = p.getConsequent().getType() == Pattern.Type.IMPLICATION
          ? (Pattern.Implication) p.getConsequent()
          : null;
      } while (p != null);
      return true;
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
