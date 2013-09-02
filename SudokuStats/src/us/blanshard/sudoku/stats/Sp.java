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

import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

import java.io.IOException;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * A set of classes for summarizing {@link Pattern}s.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Sp implements Comparable<Sp> {

  protected final Type type;
  protected final int openCountBucket;
  protected final int numAssignmentsBucket;

  private static final int OPEN_COUNT_BUCKET_SIZE = 20;
  private static final int NUM_ASSIGNMENTS_BUCKET_SIZE = 3;

  Sp(Type type, int openCount, int numAssignments) {
    this.type = type;
    this.openCountBucket = bucket(openCount, OPEN_COUNT_BUCKET_SIZE, 3);
    this.numAssignmentsBucket = bucket(numAssignments, NUM_ASSIGNMENTS_BUCKET_SIZE, 4);
  }

  static int bucket(int count, int bucketSize, int maxBuckets) {
    int bucket = count / bucketSize * bucketSize;
    return Math.min(bucket, bucketSize * (maxBuckets - 1));
  }

  public final Type getType() {
    return type;
  }

  public Appendable appendTo(Appendable a) throws IOException {
    a.append(String.valueOf(openCountBucket))
        .append(':')
        .append(String.valueOf(numAssignmentsBucket))
        .append(':')
        .append(type.getName())
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

  public static Sp fromPattern(Pattern pattern, int openCount, int numAssignments) {
    switch (pattern.getType()) {
      case CONFLICT:
        return conflict((Pattern.Conflict) pattern, openCount, numAssignments);
      case BARRED_LOCATION:
        return barredLocation((Pattern.BarredLoc) pattern, openCount, numAssignments);
      case BARRED_NUMERAL:
        return barredNumeral((Pattern.BarredNum) pattern, openCount, numAssignments);
      case FORCED_LOCATION:
        return forcedLocation((Pattern.ForcedLoc) pattern, openCount, numAssignments);
      case FORCED_NUMERAL:
        return forcedNumeral((Pattern.ForcedNum) pattern, openCount, numAssignments);
      case OVERLAP:
        return overlap((Pattern.Overlap) pattern, openCount, numAssignments);
      case LOCKED_SET:
        return lockedSet((Pattern.LockedSet) pattern, openCount, numAssignments);
      case IMPLICATION:
        return implication2((Pattern.Implication) pattern, openCount, numAssignments);
      default:
        throw new IllegalArgumentException();
    }
  }

  public static Sp fromList(List<Pattern> patterns, int openCount, int numAssignments) {
    if (patterns.isEmpty()) return NONE;
    if (patterns.size() == 1) return fromPattern(patterns.get(0), openCount, numAssignments);
    return combination2(patterns, openCount, numAssignments);
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
    IMPLICATION2(Pattern.Type.IMPLICATION),
    COMBINATION("comb"),
    COMBINATION2("comb"),
    NONE("none");

    private final String name;

    private Type(Pattern.Type patternType) {
      this.name = patternType.getName();
    }

    private Type(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  };

  @Override public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  @Override public int compareTo(Sp that) {
    int answer = ComparisonChain.start()
        .compare(this.type, that.type)
        // Backwards for #open locations
        .compare(that.openCountBucket, this.openCountBucket)
        // Backwards for #assignments
        .compare(that.numAssignmentsBucket, this.numAssignmentsBucket)
        .result();
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public boolean equals(Object o) {
    if (o == null) return false;
    if (o == this) return true;
    if (o.getClass() != this.getClass()) return false;
    Sp that = (Sp) o;
    return this.type == that.type
        && this.openCountBucket == that.openCountBucket
        && this.numAssignmentsBucket == that.numAssignmentsBucket;
  }

  @Override public int hashCode() {
    return Objects.hashCode(type, openCountBucket, numAssignmentsBucket);
  }

  protected abstract Appendable appendGutsTo(Appendable a) throws IOException;
  protected abstract int compareToGuts(Sp that);

  /**
   * For simple patterns that rely entirely on the category of unit they pertain
   * to, a common base class.
   */
  public static abstract class UnitBased extends Sp {
    private final UnitCategory category;

    UnitBased(Type type, int openCount, int numAssignments, UnitCategory category) {
      super(type, openCount, numAssignments);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      UnitBased that = (UnitBased) o;
      return this.category == that.category;
    }

    @Override public int hashCode() {
      return super.hashCode() ^ category.hashCode();
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
    private final int openInBlock;
    private final int openInLines;  // but not block
    private final boolean bothLinesRequired;

    PeerMetrics(Pattern.PeerMetrics that) {
      int openInBlock = 0;
      int openInLines = 0;
      boolean row = false, col = false;
      for (Unit.Type t : Unit.Type.values()) {
        for (int chunk = 0; chunk < 3; ++chunk) {
          int openInChunk = 0;
          boolean useChunk = true;
          for (int item = 0; item < 3; ++item) {
            int i = chunk * 3 + item;
            switch (that.getLocationCategory(t, i)) {
              case Pattern.PeerMetrics.UNSET:
                ++openInChunk;
                break;
              case Pattern.PeerMetrics.TARGET:
                useChunk = false;
                break;
              case Pattern.PeerMetrics.ROW_BIT:
                row = true;
                break;
              case Pattern.PeerMetrics.COLUMN_BIT:
                col = true;
                break;
            }
          }
          if (t == Unit.Type.BLOCK)
            openInBlock += openInChunk;
          else if (useChunk)
            openInLines += openInChunk;
        }
      }
      this.openInBlock = bucket(openInBlock, 3, 3);
      this.openInLines = bucket(openInLines, 4, 3);
      this.bothLinesRequired = row & col;
    }

    public Appendable appendTo(Appendable a) throws IOException {
      a.append(String.valueOf(openInBlock))
          .append(':')
          .append(String.valueOf(openInLines))
          .append(':')
          .append(String.valueOf(bothLinesRequired));
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
      return this.openInBlock == that.openInBlock
          && this.openInLines == that.openInLines
          && this.bothLinesRequired == that.bothLinesRequired;
    }

    @Override public int hashCode() {
      return Objects.hashCode(openInBlock, openInLines, bothLinesRequired);
    }

    @Override public int compareTo(PeerMetrics that) {
      return ComparisonChain.start()
          .compare(this.openInBlock, that.openInBlock)
          .compare(this.openInLines, that.openInLines)
          .compareFalseFirst(this.bothLinesRequired, that.bothLinesRequired)
          .result();
    }
  }

  /**
   * For patterns that rely on peer metrics, a common base class.
   */
  public static abstract class PeerMetricsBased extends Sp {
    private final PeerMetrics metrics;

    PeerMetricsBased(Type type, int openCount, int numAssignments, PeerMetrics metrics) {
      super(type, openCount, numAssignments);
      this.metrics = metrics;
    }

    public PeerMetrics getMetrics() {
      return metrics;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      PeerMetricsBased that = (PeerMetricsBased) o;
      return this.metrics.equals(that.metrics);
    }

    @Override public int hashCode() {
      return super.hashCode() ^ metrics.hashCode();
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
    Conflict(int openCount, int numAssignments, UnitCategory category) {
      super(Type.CONFLICT, openCount, numAssignments, category);
    }
  }

  public static Conflict conflict(Pattern.Conflict conflict, int openCount, int numAssignments) {
    return new Conflict(openCount, numAssignments, conflict.getCategory());
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(int openCount, int numAssignments, PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, openCount, numAssignments, metrics);
    }
  }

  public static BarredLoc barredLocation(Pattern.BarredLoc barredLocation, int openCount,
      int numAssignments) {
    return new BarredLoc(openCount, numAssignments, new PeerMetrics(barredLocation.getMetrics()));
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.
   */
  public static final class BarredNum extends UnitBased {
    BarredNum(int openCount, int numAssignments, UnitCategory category) {
      super(Type.BARRED_NUMERAL, openCount, numAssignments, category);
    }
  }

  public static BarredNum barredNumeral(Pattern.BarredNum barredNumeral, int openCount,
      int numAssignments) {
    return new BarredNum(openCount, numAssignments, barredNumeral.getCategory());
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit.
   */
  public static final class ForcedLoc extends UnitBased {
    ForcedLoc(int openCount, int numAssignments, UnitCategory category) {
      super(Type.FORCED_LOCATION, openCount, numAssignments, category);
    }
  }

  public static ForcedLoc forcedLocation(Pattern.ForcedLoc forcedLocation, int openCount,
      int numAssignments) {
    return new ForcedLoc(openCount, numAssignments, forcedLocation.getCategory());
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(int openCount, int numAssignments, PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, openCount, numAssignments, metrics);
    }
  }

  public static ForcedNum forcedNumeral(Pattern.ForcedNum forcedNumeral, int openCount,
      int numAssignments) {
    return new ForcedNum(openCount, numAssignments, new PeerMetrics(forcedNumeral.getMetrics()));
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.
   */
  public static final class Overlap extends UnitBased {
    Overlap(int openCount, int numAssignments, UnitCategory category) {
      super(Type.OVERLAP, openCount, numAssignments, category);
    }
  }

  public static Overlap overlap(Pattern.Overlap overlap, int openCount, int numAssignments) {
    return new Overlap(openCount, numAssignments, overlap.getCategory());
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.
   */
  public static final class LockedSet extends UnitBased {
    private final int setSize;
    private final boolean isNaked;

    LockedSet(int openCount, int numAssignments, UnitCategory category, int setSize, boolean isNaked) {
      super(Type.LOCKED_SET, openCount, numAssignments, category);
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

  public static LockedSet lockedSet(Pattern.LockedSet lockedSet, int openCount, int numAssignments) {
    return new LockedSet(openCount, numAssignments, lockedSet.getCategory(), lockedSet.getSetSize(), lockedSet.isNaked());
  }

  /**
   * A collection of antecedent patterns leading to a consequent pattern.
   */
  public static class Implication extends Sp {
    private final List<Sp> antecedents;
    private final Sp consequent;

    private Implication(int openCount, int numAssignments, List<Sp> antecedents, Sp consequent) {
      super(Type.IMPLICATION, openCount, numAssignments);
      this.antecedents = antecedents;
      this.consequent = consequent;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Implication that = (Implication) o;
      return this.antecedents.equals(that.antecedents)
          && this.consequent.equals(that.consequent);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), antecedents, consequent);
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

  public static Implication implication(Pattern.Implication implication, final int openCount,
      final int numAssignments) {
    return new Implication(
        openCount,
        numAssignments,
        Lists.transform(implication.getAntecedents(), new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p, openCount, numAssignments);
          }
        }), Sp.fromPattern(implication.getConsequent(), openCount, numAssignments));
  }

  /**
   * A collection of antecedent types leading to a consequent type.
   */
  public static class Implication2 extends Sp {
    private final List<Type> antecedents;
    private final Type consequent;

    private Implication2(int openCount, int numAssignments, List<Type> antecedents, Type consequent) {
      super(Type.IMPLICATION2, openCount, numAssignments);
      this.antecedents = antecedents;
      this.consequent = consequent;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Implication2 that = (Implication2) o;
      return this.antecedents.equals(that.antecedents)
          && this.consequent.equals(that.consequent);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), antecedents, consequent);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      boolean one = false;
      for (Type t : antecedents) {
        if (one) a.append('+');
        else one = true;
        a.append(t.getName());
      }
      return a.append('=').append(consequent.getName());
    }

    @Override protected int compareToGuts(Sp p) {
      Implication2 that = (Implication2) p;
      Ordering<Iterable<Type>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, ordering)
          .compare(this.consequent, that.consequent)
          .result();
    }
  }

  public static Implication2 implication2(Pattern.Implication implication, final int openCount,
      final int numAssignments) {
    return new Implication2(
        openCount,
        numAssignments,
        Lists.transform(implication.getAntecedents(), new Function<Pattern, Type>() {
          @Override public Type apply(Pattern p) {
            return Sp.fromPattern(p, openCount, numAssignments).type;
          }
        }), Sp.fromPattern(implication.getConsequent(), openCount, numAssignments).type);
  }

  /**
   * A collection of patterns for the same assignment.
   */
  public static class Combination extends Sp {
    private final List<Sp> parts;

    private Combination(int openCount, int numAssignments, List<Sp> parts) {
      super(Type.COMBINATION, openCount, numAssignments);
      this.parts = parts;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Combination that = (Combination) o;
      return this.parts.equals(that.parts);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), parts);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return Joiner.on(';').appendTo(a, parts);
    }

    @Override protected int compareToGuts(Sp p) {
      Combination that = (Combination) p;
      Ordering<Iterable<Sp>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.parts, that.parts, ordering)
          .result();
    }
  }

  public static Combination combination(List<Pattern> parts, final int openCount,
      final int numAssignments) {
    return new Combination(
        openCount,
        numAssignments, Lists.transform(parts, new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p, openCount, numAssignments);
          }
        }));
  }

  /**
   * A collection of patterns for the same assignment.
   */
  public static class Combination2 extends Sp {
    private final SortedMultiset<Type> parts;

    private Combination2(int openCount, int numAssignments, SortedMultiset<Type> parts) {
      super(Type.COMBINATION2, openCount, numAssignments);
      this.parts = parts;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Combination2 that = (Combination2) o;
      return this.parts.equals(that.parts);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), parts);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return Joiner.on(';').appendTo(a, Iterables.transform(parts.entrySet(),
          new Function<Multiset.Entry<Type>, String>() {
        @Override public String apply(Multiset.Entry<Type> e) {
          if (e.getCount() == 1) return e.getElement().getName();
          return e.getElement().getName() + '*' + e.getCount();
        }
      }));
    }

    @Override protected int compareToGuts(Sp p) {
      Combination2 that = (Combination2) p;
      Ordering<Iterable<Type>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.parts, that.parts, ordering)
          .result();
    }
  }

  public static Combination2 combination2(List<Pattern> parts, final int openCount,
      final int numAssignments) {
    return new Combination2(
        openCount,
        numAssignments, TreeMultiset.create(Lists.transform(parts, new Function<Pattern, Type>() {
          @Override public Type apply(Pattern p) {
            return Sp.fromPattern(p, openCount, numAssignments).type;
          }
        })));
  }

  /**
   * A special null Sp object used to collection information about
   * patterns that were not seen.
   */
  public static class None extends Sp {
    private None() {
      super(Type.NONE, 0, 0);
    }

    @Override public Appendable appendTo(Appendable a) throws IOException {
      a.append("none");
      return a;
    }

    @Override public boolean equals(Object o) {
      return o instanceof None;
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
