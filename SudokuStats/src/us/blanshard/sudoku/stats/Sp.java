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

import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A set of classes for summarizing {@link Pattern}s.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Sp implements Comparable<Sp> {

  private final Type type;

  Sp(Type type) {
    this.type = type;
  }

  public final Type getType() {
    return type;
  }

  public Appendable appendTo(Appendable a) throws IOException {
    a.append(type.getPatternType().getName()).append(':');
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

  public static Sp fromPattern(Pattern pattern) {
    switch (pattern.getType()) {
      case CONFLICT:
        return conflict((Pattern.Conflict) pattern);
      case BARRED_LOCATION:
        return barredLocation((Pattern.BarredLoc) pattern);
      case BARRED_NUMERAL:
        return barredNumeral((Pattern.BarredNum) pattern);
      case FORCED_LOCATION:
        return forcedLocation((Pattern.ForcedLoc) pattern);
      case FORCED_NUMERAL:
        return forcedNumeral((Pattern.ForcedNum) pattern);
      case OVERLAP:
        return overlap((Pattern.Overlap) pattern);
      case LOCKED_SET:
        return lockedSet((Pattern.LockedSet) pattern);
      case IMPLICATION:
        return implication((Pattern.Implication) pattern);
      default:
        throw new IllegalArgumentException();
    }
  }

  public enum Type {
    CONFLICT(Pattern.Type.CONFLICT),
    BARRED_LOCATION(Pattern.Type.BARRED_LOCATION),
    BARRED_NUMERAL(Pattern.Type.BARRED_NUMERAL),
    FORCED_LOCATION(Pattern.Type.FORCED_LOCATION),
    FORCED_NUMERAL(Pattern.Type.FORCED_NUMERAL),
    OVERLAP(Pattern.Type.OVERLAP),
    LOCKED_SET(Pattern.Type.LOCKED_SET),
    IMPLICATION(Pattern.Type.IMPLICATION),
    NONE(null);

    @Nullable private final Pattern.Type patternType;

    private Type(Pattern.Type patternType) {
      this.patternType = patternType;
    }

    public Pattern.Type getPatternType() {
      return patternType;
    }
  };

  @Override public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  @Override public int compareTo(Sp that) {
    int answer = this.type.compareTo(that.type);
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public abstract boolean equals(Object o);
  @Override public abstract int hashCode();

  protected abstract Appendable appendGutsTo(Appendable a) throws IOException;
  protected abstract int compareToGuts(Sp that);

  /**
   * For simple patterns that rely entirely on the category of unit they pertain
   * to, a common base class.
   */
  public static abstract class UnitBased extends Sp {
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

    @Override protected int compareToGuts(Sp p) {
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
     * The number of open locations in the entire grid, rounded off to a
     * bucket size.
     */
    private final int openCountBucket;

    /**
     * The number of unique numerals in the block peers.
     */
    private final int blockCount;

    /**
     * For the numerals <b>not</b> in the block, the signed distance from the
     * location in question where each numeral lies along one of the location's
     * peer lines, ordered from most positive to most negative. Numerals not in
     * either the block nor this line are represented as zero, and they are
     * ordered last.
     */
//    private final byte[] line1Coordinates;

    /**
     * The same, but for the other line.  For line 2, the order matches that of
     * line 1, meaning that index 0 of both arrays refers to the coordinate of
     * the same numeral along both lines.
     */
//    private final byte[] line2Coordinates;

    private final int line1Count;
    private final int totalLinearLength;

    PeerMetrics(Pattern.PeerMetrics that) {
      this.openCountBucket = that.getOpenCount() / 20 * 20;
//      this.line1Coordinates = that.getLine1Coordinates();
//      this.line2Coordinates = that.getLine2Coordinates();
      byte[] line1Coordinates = that.getLine1Coordinates();
      byte[] line2Coordinates = that.getLine2Coordinates();
      this.blockCount = 9 - line1Coordinates.length;
      int count = 0;
      for (byte b : line1Coordinates)
        if (b != 0) ++count;
      this.line1Count = count;
      int min1 = 0, max1 = 0, min2 = 0, max2 = 0;
      for (byte b : line1Coordinates) {
        if (b > max1) max1 = b;
        if (b < min1) min1 = b;
      }
      for (byte b : line2Coordinates) {
        if (b > max2) max2 = b;
        if (b < min2) min2 = b;
      }
      this.totalLinearLength = (max1 - min1) + (max2 - min2);
    }

    public int getOpenCountBucket() {
      return openCountBucket;
    }

    public int getBlockCount() {
      return blockCount;
    }

//    public byte[] getLine1Coordinates() {
//      return line1Coordinates.clone();
//    }
//
//    public byte[] getLine2Coordinates() {
//      return line2Coordinates.clone();
//    }

    public Appendable appendTo(Appendable a) throws IOException {
      a.append(String.valueOf(totalLinearLength)).append(':');
      a.append(String.valueOf(openCountBucket)).append(':');
      a.append(String.valueOf(blockCount)).append(':');
//      Pattern.PeerMetrics.appendCoordinatesTo(line1Coordinates, a);
//      a.append(':');
//      Pattern.PeerMetrics.appendCoordinatesTo(line2Coordinates, a);
      a.append(String.valueOf(line1Count)).append(':');
      a.append(String.valueOf(8 - blockCount - line1Count));
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

    @Override public boolean equals(Object o) {
      if (!(o instanceof PeerMetrics)) return false;
      PeerMetrics that = (PeerMetrics) o;
      return this.openCountBucket == that.openCountBucket
          && this.blockCount == that.blockCount
//          && Arrays.equals(this.line1Coordinates, that.line1Coordinates)
//          && Arrays.equals(this.line2Coordinates, that.line2Coordinates);
          && this.line1Count == that.line1Count
          && this.totalLinearLength == that.totalLinearLength;
    }

    @Override public int hashCode() {
      return Objects.hashCode(openCountBucket, blockCount, line1Count, totalLinearLength);
//          + Arrays.hashCode(line1Coordinates)
//          + Arrays.hashCode(line2Coordinates);
    }

    @Override public int compareTo(PeerMetrics that) {
//      Comparator<byte[]> comp = SignedBytes.lexicographicalComparator();
      return ComparisonChain.start()
          .compare(this.totalLinearLength, that.totalLinearLength)
          .compare(that.openCountBucket, this.openCountBucket)
          .compare(this.blockCount, that.blockCount)
          .compare(this.line1Count, that.line1Count)
//          .compare(this.line1Coordinates, that.line1Coordinates, comp)
//          .compare(this.line2Coordinates, that.line2Coordinates, comp)
          .result();
    }
  }

  /**
   * For patterns that rely on peer metrics, a common base class.
   */
  public static abstract class PeerMetricsBased extends Sp {
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

    @Override protected int compareToGuts(Sp p) {
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

  public static Conflict conflict(Pattern.Conflict conflict) {
    return conflict.getCategory() == UnitCategory.BLOCK ? Conflict.BLOCK : Conflict.LINE;
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, metrics);
    }
  }

  public static BarredLoc barredLocation(Pattern.BarredLoc barredLocation) {
    return new BarredLoc(new PeerMetrics(barredLocation.getMetrics()));
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

  public static BarredNum barredNumeral(Pattern.BarredNum barredNumeral) {
    return barredNumeral.getCategory() == UnitCategory.BLOCK ? BarredNum.BLOCK : BarredNum.LINE;
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

  public static ForcedLoc forcedLocation(Pattern.ForcedLoc forcedLocation) {
    return forcedLocation.getCategory() == UnitCategory.BLOCK ? ForcedLoc.BLOCK : ForcedLoc.LINE;
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, metrics);
    }
  }

  public static ForcedNum forcedNumeral(Pattern.ForcedNum forcedNumeral) {
    return new ForcedNum(new PeerMetrics(forcedNumeral.getMetrics()));
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

  public static Overlap overlap(Pattern.Overlap overlap) {
    return overlap.getCategory() == UnitCategory.BLOCK ? Overlap.BLOCK : Overlap.LINE;
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
  }

  public static LockedSet lockedSet(Pattern.LockedSet lockedSet) {
    return new LockedSet(lockedSet.getCategory(), lockedSet.getSetSize(), lockedSet.isNaked());
  }

  /**
   * A collection of antecedent patterns leading to a consequent pattern.
   */
  public static class Implication extends Sp {
    private final List<Sp> antecedents;
    private final Sp consequent;

    private Implication(List<Sp> antecedents, Sp consequent) {
      super(Type.IMPLICATION);
      this.antecedents = antecedents;
      this.consequent = consequent;
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

    @Override protected int compareToGuts(Sp p) {
      Implication that = (Implication) p;
      Ordering<Iterable<Sp>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, ordering)
          .compare(this.consequent, that.consequent)
          .result();
    }
  }

  public static Implication implication(Pattern.Implication implication) {
    return new Implication(
        Lists.transform(implication.getAntecedents(), new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p);
          }
        }),
        Sp.fromPattern(implication.getConsequent()));
  }

  /**
   * A special null Sp object used to collection information about
   * patterns that were not seen.
   */
  public static class None extends Sp {
    private None() {
      super(Type.NONE);
    }

    @Override public Appendable appendTo(Appendable a) throws IOException {
      a.append("none");
      return a;
    }

    @Override public boolean equals(Object o) {
      return this == o;
    }

    @Override public int hashCode() {
      return getClass().hashCode();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override protected int compareToGuts(Sp that) {
      return 0;
    }
  }

  public static final None NONE = new None();
}
