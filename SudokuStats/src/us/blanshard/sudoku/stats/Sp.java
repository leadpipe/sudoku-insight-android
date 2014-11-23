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
import static java.lang.Math.max;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

/**
 * A set of classes for summarizing {@link Pattern}s.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Sp implements Comparable<Sp> {

  protected final Type type;

  Sp(Type type) {
    this.type = type;
  }

  public final Type getType() {
    return type;
  }

  public boolean isSingular() {
    return true;
  }

  public boolean isMove() {
    return false;
  }

  public int size() {
    return 1;
  }

  public Sp nub() {
    return this;
  }

  /**
   * Returns the probability that a move with this pattern will be played, on a
   * grid with the given number of open squares.
   */
  public double getProbabilityOfPlaying(int numOpen) {
    return getProbability(numOpen, directProbabilities);
  }

  protected double getAddedProbability(int numOpen) {
    return getProbability(numOpen, additiveProbabilities);
  }

  protected Sp getFallbackKey() {
    throw new UnsupportedOperationException();
  }

  private double getProbability(int numOpen, Map<Sp, double[]> map) {
    Sp key = this;
    while (!map.containsKey(key)) key = key.getFallbackKey();
    double[] ps = map.get(key);
    int index = Math.min(numOpen / 20, 2);
    return ps[index];
  }

  private static final ImmutableMap<Sp, double[]> directProbabilities;
  private static final ImmutableMap<Sp, double[]> additiveProbabilities;
  static {
    directProbabilities = null;
    additiveProbabilities = null;
  }

  /**
   * Returns the first Sp in a combination, or just this Sp outside of a
   * combination.
   */
  public Sp head() {
    return this;
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

  public static Sp fromPattern(Pattern pattern, int openCount) {
    switch (pattern.getType()) {
      case CONFLICT:
        return conflict((Pattern.Conflict) pattern);
      case BARRED_LOCATION:
        return barredLocation((Pattern.BarredLoc) pattern, openCount);
      case BARRED_NUMERAL:
        return barredNumeral((Pattern.BarredNum) pattern);
      case FORCED_LOCATION:
        return forcedLocation((Pattern.ForcedLoc) pattern);
      case FORCED_NUMERAL:
        return forcedNumeral((Pattern.ForcedNum) pattern, openCount);
      case OVERLAP:
        return overlap((Pattern.Overlap) pattern, openCount);
      case LOCKED_SET:
        return lockedSet((Pattern.LockedSet) pattern);
      case NAKED_SET:
        return nakedSet((Pattern.NakedSet) pattern);
      case IMPLICATION:
        return implication((Pattern.Implication) pattern, openCount);
      default:
        throw new IllegalArgumentException();
    }
  }

  public static Sp fromList(List<? extends Pattern> patterns, int openCount) {
    if (patterns.isEmpty()) return NONE;
    if (patterns.size() == 1) return fromPattern(patterns.get(0), openCount);
    return combination(patterns, openCount);
  }

  public enum Type {
    CONFLICT(Pattern.Type.CONFLICT),
    BARRED_LOCATION(Pattern.Type.BARRED_LOCATION),
    BARRED_NUMERAL(Pattern.Type.BARRED_NUMERAL),
    FORCED_LOCATION(Pattern.Type.FORCED_LOCATION),
    FORCED_NUMERAL(Pattern.Type.FORCED_NUMERAL),
    OVERLAP(Pattern.Type.OVERLAP),
    LOCKED_SET(Pattern.Type.LOCKED_SET),
    NAKED_SET(Pattern.Type.NAKED_SET),
    IMPLICATION(Pattern.Type.IMPLICATION),
    COMBINATION("comb"),
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
    int answer = this.type.compareTo(that.type);
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public boolean equals(Object o) {
    if (o == null) return false;
    if (o == this) return true;
    if (o.getClass() != this.getClass()) return false;
    Sp that = (Sp) o;
    return this.type == that.type;
  }

  @Override public int hashCode() {
    return type.hashCode();
  }

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
    /**
     * How many more assignments are in the fullest unit, compared to the
     * average number of assignments per unit overall, rounded down and clipped
     * at zero.
     */
    public final int deltaOverAverage;

    public PeerMetrics(Pattern.PeerMetrics that, int openCount) {
      int setInFullestUnit = 0;
      for (Unit.Type t : Unit.Type.values()) {
        int setInUnit = 0;
        int unitBit = Pattern.PeerMetrics.unitBit(t);
        for (int i = 0; i < 9; ++i) {
          byte cat = that.getLocationCategory(t, i);
          if ((cat & unitBit) != 0)
            ++setInUnit;
        }
        setInFullestUnit = max(setInFullestUnit, setInUnit);
      }
      int averageSetPerUnit = (Location.COUNT - openCount) / 9;
      this.deltaOverAverage = max(0, setInFullestUnit - averageSetPerUnit);
    }

    public Appendable appendTo(Appendable a) throws IOException {
      return a.append(String.valueOf(deltaOverAverage));
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
      return this.deltaOverAverage == that.deltaOverAverage;
    }

    @Override public int hashCode() {
      return Objects.hashCode(deltaOverAverage);
    }

    @Override public int compareTo(PeerMetrics that) {
      return ComparisonChain.start()
          // Note reversing order:
          .compare(that.deltaOverAverage, this.deltaOverAverage)
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
    Conflict(UnitCategory category) {
      super(Type.CONFLICT, category);
    }
  }

  public static Conflict conflict(Pattern.Conflict conflict) {
    return new Conflict(conflict.getCategory());
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, metrics);
    }
  }

  public static BarredLoc barredLocation(Pattern.BarredLoc barredLocation, int openCount) {
    return new BarredLoc(new PeerMetrics(barredLocation.getMetrics(), openCount));
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.
   */
  public static final class BarredNum extends UnitBased {
    BarredNum(UnitCategory category) {
      super(Type.BARRED_NUMERAL, category);
    }
  }

  public static BarredNum barredNumeral(Pattern.BarredNum barredNumeral) {
    return new BarredNum(barredNumeral.getCategory());
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit.
   */
  public static final class ForcedLoc extends UnitBased {
    ForcedLoc(UnitCategory category) {
      super(Type.FORCED_LOCATION, category);
    }
    @Override public boolean isMove() {
      return true;
    }
  }

  public static ForcedLoc forcedLocation(Pattern.ForcedLoc forcedLocation) {
    return new ForcedLoc(forcedLocation.getCategory());
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, metrics);
    }
    @Override public boolean isMove() {
      return true;
    }
  }

  public static ForcedNum forcedNumeral(Pattern.ForcedNum forcedNumeral, int openCount) {
    return new ForcedNum(new PeerMetrics(forcedNumeral.getMetrics(), openCount));
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.
   */
  public static final class Overlap extends UnitBased {
    Overlap(int openCount, UnitCategory category) {
      super(Type.OVERLAP, category);
    }
  }

  public static Overlap overlap(Pattern.Overlap overlap, int openCount) {
    return new Overlap(openCount, overlap.getCategory());
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.
   */
  public static final class LockedSet extends UnitBased {
    private final int setSize;
    private final boolean isNaked;
    private final boolean isOverlapped;

    LockedSet(UnitCategory category, int setSize, boolean isNaked, boolean isOverlapped) {
      super(Type.LOCKED_SET, category);
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

    @Override protected int compareToGuts(Sp p) {
      LockedSet that = (LockedSet) p;
      return ComparisonChain.start()
          .compare(this.setSize, that.setSize)
          .compareFalseFirst(this.isNaked, that.isNaked)
          .compareTrueFirst(this.isOverlapped, that.isOverlapped)
          .compare(this.getCategory(), that.getCategory())
          .result();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return super.appendGutsTo(a)
          .append(':')
          .append(String.valueOf(setSize))
          .append(isNaked ? 'n' : 'h')
          .append(isOverlapped ? 'o' : 'd');
    }
  }

  public static LockedSet lockedSet(Pattern.LockedSet lockedSet) {
    return new LockedSet(
        lockedSet.getCategory(), lockedSet.getSetSize(), lockedSet.isNaked(), lockedSet.isOverlapped());
  }

  /**
   * A special pattern for naked sets.
   */
  public static final class NakedSet extends Sp {
    private final UnitCategory category;
    private final int setSize;
    private final int deltaOverAverage;

    NakedSet(UnitCategory category, int setSize, int deltaOverAverage) {
      super(Type.NAKED_SET);
      this.category = category;
      this.setSize = setSize;
      this.deltaOverAverage = deltaOverAverage;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getSetSize() {
      return setSize;
    }

    public int getDeltaOverAverage() {
      return deltaOverAverage;
    }

    @Override public boolean equals(Object o) {
      if (o == null) return false;
      if (o == this) return true;
      if (o instanceof NakedSet) {
        NakedSet that = (NakedSet) o;
        return this.category == that.category
            && this.setSize == that.setSize
            /*&& this.deltaOverAverage == that.deltaOverAverage*/;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hashCode(category, setSize/*, deltaOverAverage*/);
    }

    @Override protected int compareToGuts(Sp p) {
      NakedSet that = (NakedSet) p;
      return ComparisonChain.start()
          .compare(this.setSize, that.setSize)
          .compare(this.category, that.category)
//          .compare(that.deltaOverAverage, this.deltaOverAverage)  // inverted
          .result();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return a
          .append(String.valueOf(setSize))
          .append(':')
          .append(category.toString())
//          .append(String.valueOf(deltaOverAverage))
          ;
    }
  }

  public static NakedSet nakedSet(Pattern.NakedSet set) {
    return new NakedSet(set.getCategory(), set.getSetSize(), set.getDeltaOverAverage());
  }

  /**
   * Contains the patterns of the antecedents and consequent; treated as a
   * conjunction for probabilities, and a sum for times.
   */
  public static class Implication extends Sp {
    final SortedMultiset<Sp> antecedents;
    final Sp consequent;

    private Implication(SortedMultiset<Sp> antecedents, Sp consequent) {
      super(Type.IMPLICATION);
      this.antecedents = antecedents;
      this.consequent = consequent;
    }

    @Override public boolean isSingular() {
      return false;
    }

    @Override public boolean isMove() {
      return consequent.isMove();
    }

    @Override public int size() {
      return antecedents.size() + consequent.size();
    }

    @Override public Sp nub() {
      return consequent.nub();
    }

    /**
     * Returns the Sp (implication or not) consisting of the remaining parts of
     * this implication when the given antecedent is removed.
     */
    public Sp minus(Sp antecedent) {
      checkArgument(antecedents.contains(antecedent));
      if (antecedents.size() == 1) return consequent;
      SortedMultiset<Sp> newAntecedents = TreeMultiset.create(antecedents);
      newAntecedents.remove(antecedent);
      return new Implication(newAntecedents, consequent);
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

    private static final Ordering<Iterable<Sp>> LEXICO = Ordering.natural().lexicographical();

    @Override protected int compareToGuts(Sp p) {
      Implication that = (Implication) p;
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, LEXICO)
          .compare(this.consequent, that.consequent)
          .result();
    }

    @Override public double getProbabilityOfPlaying(int numOpen) {
      double answer = consequent.getProbabilityOfPlaying(numOpen);
      for (Sp antecedent : antecedents) answer *= antecedent.getProbabilityOfPlaying(numOpen);
      return answer;
    }

    @Override public double getAddedProbability(int numOpen) {
      double answer = consequent.getAddedProbability(numOpen);
      for (Sp antecedent : antecedents) answer *= antecedent.getAddedProbability(numOpen);
      return answer;
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      Joiner.on(',').appendTo(a, Iterables.transform(antecedents.entrySet(),
          new Function<Multiset.Entry<Sp>, String>() {
        @Override public String apply(Multiset.Entry<Sp> e) {
          if (e.getCount() == 1) return e.getElement().toString();
          return e.getElement() + "(" + e.getCount() + ")";
        }
      }));
      a.append("=>");
      return consequent.appendGutsTo(a);
    }
  }

  public static Implication implication(Pattern.Implication implication, final int openCount) {
    SortedMultiset<Sp> antecedents = TreeMultiset.create();
    for (Pattern p : implication.getAntecedents())
      antecedents.add(Sp.fromPattern(p, openCount));
    Sp consequent = Sp.fromPattern(implication.getConsequent(), openCount);
    return new Implication(antecedents, consequent);
  }

  /**
   * A collection of patterns for the same assignment; treated as a disjunction
   * for probabilities, and a min for times.
   */
  public static class Combination extends Sp {
    final SortedMultiset<Sp> parts;

    private Combination(SortedMultiset<Sp> parts) {
      super(Type.COMBINATION);
      this.parts = parts;
    }


    @Override public boolean isSingular() {
      return false;
    }

    @Override public boolean isMove() {
      for (Sp part : parts)
        if (part.isMove()) return true;
      return false;
    }

    @Override public int size() {
      return parts.size();
    }

    @Override public Sp nub() {
      return parts.firstEntry().getElement();
    }

    /**
     * Returns the Sp (composite or not) consisting of the remaining parts of
     * this Composite when the given part is removed.
     */
    public Sp minus(Sp part) {
      checkArgument(parts.contains(part));
      SortedMultiset<Sp> newParts = TreeMultiset.create(parts);
      newParts.remove(part);
      if (newParts.size() == 1)
        return newParts.firstEntry().getElement();
      return new Combination(newParts);
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Combination that = (Combination) o;
      return this.parts.equals(that.parts);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), parts);
    }

    private static final Ordering<Iterable<Sp>> LEXICO = Ordering.natural().lexicographical();

    @Override protected int compareToGuts(Sp p) {
      Combination that = (Combination) p;
      return ComparisonChain.start()
          .compare(this.parts, that.parts, LEXICO)
          .result();
    }

    @Override public double getProbabilityOfPlaying(int numOpen) {
      double answer = 0;
      boolean firstPart = true;
      for (Sp part : parts) {
        if (firstPart) {
          firstPart = false;
          answer = part.getProbabilityOfPlaying(numOpen);
        } else {
          answer += part.getAddedProbability(numOpen);
        }
      }
      return answer;
    }

    @Override public Sp head() {
      return Iterables.getFirst(parts, null);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return Joiner.on(';').appendTo(a, Iterables.transform(parts.entrySet(),
          new Function<Multiset.Entry<Sp>, String>() {
        @Override public String apply(Multiset.Entry<Sp> e) {
          if (e.getCount() == 1) return e.getElement().toString();
          return e.getElement() + "*" + e.getCount();
        }
      }));
    }
  }

  public static Combination combination(List<? extends Pattern> parts, final int openCount) {
    return new Combination(
        TreeMultiset.create(Lists.transform(parts, new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p, openCount);
          }
        })));
  }

  /**
   * A special null Sp object used to collect information about patterns that
   * were not seen.
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
