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

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * A depth-first, randomized, worklist-based Sudoku solver.  This is an
 * Iterable: its iterator returns all solutions (if any) to the starting grid.
 *
 * @author Luke Blanshard
 */
public final class Solver implements Iterable<Grid> {

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, Random random, Strategy strategy) {
    return solve(start, random, strategy, 300);
  }

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, Random random, Strategy strategy, int factor) {
    return new Solver(start, random, strategy, factor).result();
  }

  /**
   * A summary of a solver's work.
   */
  public class Result {
    public final Grid start;
    public final int numSolutions;  // 0, 1, or 2 to mean >1
    public final int numSteps;  // The total number
    public final int numStepsAfterSolution;
    @Nullable public final Grid solution;  // Not null when numSolutions == 1

    public Result() {
      Iter iter = iterator();
      int count = 0;
      Grid grid = null;
      int steps = 0;
      while (iter.hasNext() && ++count <= 1) {
        grid = iter.next();
        steps = iter.getStepCount();
      }
      this.start = Solver.this.start;
      this.numSolutions = count;
      this.numSteps = iter.getStepCount();
      this.numStepsAfterSolution = count == 0 ? 0 : iter.getStepCount() - steps;
      this.solution = count == 1 ? grid : null;
    }
  }

  /**
   * This enumeration provides 3 strategies for solving a puzzle, which consist
   * of deciding where to look in the puzzle for useful choice points: just
   * locations, just unit-numeral pairs, or both.
   */
  public enum Strategy {
    LOC {
      @Override void fillList(List<ChoicePoint> list, Marks marks) {
        for (LocationChoicePoint point : locationChoicePoints) {
          if (marks.get(point.location).size() > 1)
            list.add(point);
        }
      }
    },
    UNIT {
      @Override void fillList(List<ChoicePoint> list, Marks marks) {
        for (UnitNumeralChoicePoint point : unitNumeralChoicePoints) {
          if (marks.get(point.unit, point.numeral).size() > 1)
            list.add(point);
        }
      }
    },
    ALL {
      @Override void fillList(List<ChoicePoint> list, Marks marks) {
        LOC.fillList(list, marks);
        UNIT.fillList(list, marks);
      }
    };

    /**
     * Constructs a list of choice points that have more than one possible
     * assignment remaining in the given marks.
     */
    public List<ChoicePoint> getChoicePoints(Marks marks) {
      List<ChoicePoint> list = Lists.newArrayList();
      fillList(list, marks);
      return list;
    }

    /** Adds appropriate choice points to the given list. */
    abstract void fillList(List<ChoicePoint> list, Marks marks);
  }

  private final Grid start;
  private final Random random;
  private final int factor;
  private final Marks marks;
  @Nullable private final List<ChoicePoint> choicePoints;

  public Solver(Grid start, Random random, Strategy strategy, int factor) {
    this.start = start;
    this.random = random;
    this.factor = factor;
    Marks.Builder builder = Marks.builder();
    if (builder.assignAll(start)) {
      this.choicePoints = strategy.getChoicePoints(builder.build());
      Collections.shuffle(choicePoints, random);
    } else {
      // This puzzle is not solvable.
      this.choicePoints = null;
    }
    this.marks = builder.build();
  }

  @Override public Iter iterator() {
    return new Iter();
  }

  public Result result() {
    return new Result();
  }

  public class Iter implements Iterator<Grid> {
    private boolean nextComputed;
    private Grid next;
    private int stepCount;
    private final Iterator<ChoicePoint> points;
    private final ArrayDeque<Assignment> worklist = new ArrayDeque<Assignment>();

    private Iter() {
      if (choicePoints == null) {
        // This puzzle is not solvable.
        next = null;
        nextComputed = true;
        points = null;
      } else {
        points = Iterators.cycle(choicePoints);
        if (!pushNextAssignments(marks)) {
          // This puzzle was solved by the initial assignments.
          next = marks.asGrid();
          nextComputed = true;
        }
      }
    }

    /**
     * Returns the number of steps taken to do the work done so far.
     */
    public int getStepCount() {
      return stepCount;
    }

    @Override public boolean hasNext() {
      if (!nextComputed) {
        next = computeNext();
        nextComputed = true;
      }
      return next != null;
    }

    @Override public Grid next() {
      if (!hasNext()) throw new NoSuchElementException();
      nextComputed = false;
      return next;
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }

    @Nullable private Grid computeNext() {
      while (!worklist.isEmpty()) {
        ++stepCount;
        Assignment assignment = worklist.removeFirst();
        Marks.Builder marksBuilder = assignment.marks.asBuilder();
        if (marksBuilder.assign(assignment.location, assignment.numeral)) {
          Marks marks = marksBuilder.build();
          if (!pushNextAssignments(marks)) return marks.asGrid();
        }
      }
      return null;  // All done.
    }

    /** Returns true if there are more assignments to be made. */
    private boolean pushNextAssignments(Marks marks) {
      List<Assignment> list = chooseNextAssignments(marks);
      if (list == null) return false;  // No more assignments.

      // Push all possibilities onto the stack in random order.
      for (int last = list.size(); last-- > 0; ) {
        int index = random.nextInt(last + 1);
        worklist.addFirst(list.get(index));
        if (index != last) list.set(index, list.get(last));
      }
      return true;
    }

    /**
     * Chooses a random set of mutually exclusive assignments from those
     * available in the given marks, or null if there aren't any left.
     */
    @Nullable private List<Assignment> chooseNextAssignments(Marks marks) {
      int size = 10;
      int max = choicePoints.size();
      ChoicePoint current = null;
      for (int count = 0; count < max; ++count) {
        ChoicePoint point = points.next();
        int pointSize = point.size(marks);
        if (pointSize > 1 && pointSize < size) {
          current = point;
          size = pointSize;
          max = Math.min(max, (size - 2) * factor);
        }
      }
      if (current == null) return null;
      return current.choices(marks);
    }
  }

  private static class Assignment {
    final Marks marks;
    final Location location;
    final Numeral numeral;

    Assignment(Marks marks, Location location, Numeral numeral) {
      this.marks = marks;
      this.location = location;
      this.numeral = numeral;
    }
  }

  /**
   * A choice point is a part of the puzzle that has mutually exclusive possible
   * assignments of numerals to locations.  There are two kinds of choice
   * points.  The first is the obvious: locations, with the possible numerals
   * for each.  The second is more subtle: units and numerals, with the possible
   * locations within each unit available for each numeral.
   */
  private interface ChoicePoint {
    /**
     * Tells how many possible assignments there are at this point in the given
     * marks.
     */
    int size(Marks marks);

    /** Returns the possible assignments for this point in the given marks. */
    List<Assignment> choices(Marks marks);
  }

  private static class LocationChoicePoint implements ChoicePoint {
    final Location location;

    LocationChoicePoint(Location location) {
      this.location = location;
    }

    @Override public int size(Marks marks) {
      return marks.get(location).size();
    }

    @Override public List<Assignment> choices(Marks marks) {
      NumSet set = marks.get(location);
      Assignment[] array = new Assignment[set.size()];
      int index = 0;
      for (Numeral numeral : set)
        array[index++] = new Assignment(marks, location, numeral);
      return Arrays.asList(array);
    }
  }

  private static class UnitNumeralChoicePoint implements ChoicePoint {
    final Unit unit;
    final Numeral numeral;

    UnitNumeralChoicePoint(Unit unit, Numeral numeral) {
      this.unit = unit;
      this.numeral = numeral;
    }

    @Override public int size(Marks marks) {
      return marks.get(unit, numeral).size();
    }

    @Override public List<Assignment> choices(Marks marks) {
      UnitSubset set = marks.get(unit, numeral);
      Assignment[] array = new Assignment[set.size()];
      int index = 0;
      for (Location location : set)
        array[index++] = new Assignment(marks, location, numeral);
      return Arrays.asList(array);
    }
  }

  private static final LocationChoicePoint[] locationChoicePoints;
  private static final UnitNumeralChoicePoint[] unitNumeralChoicePoints;
  static {
    locationChoicePoints = new LocationChoicePoint[Location.COUNT];
    int index = 0;
    for (Location loc : Location.ALL)
      locationChoicePoints[index++] = new LocationChoicePoint(loc);

    unitNumeralChoicePoints = new UnitNumeralChoicePoint[Unit.COUNT * 9];
    index = 0;
    for (Unit unit : Unit.allUnits())
      for (Numeral numeral : Numeral.ALL)
        unitNumeralChoicePoints[index++] = new UnitNumeralChoicePoint(unit, numeral);
  }
}
