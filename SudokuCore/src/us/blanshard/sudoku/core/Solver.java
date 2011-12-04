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

import com.google.common.collect.Lists;

import java.util.ArrayDeque;
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
  public static Result solve(Grid start, Random random, Strategy strategy, boolean fat) {
    return new Solver(start, random, strategy, fat).result();
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
   * An assignment set is a set of mutually exclusive possible assignments of
   * numerals to locations.  There are two kinds of assignment sets.  The first
   * is the obvious: locations, with the possible numerals for each.  The second
   * is more subtle: units and numerals, with the possible locations within each
   * unit available for each numeral.
   *
   * <p> This enumeration provides 3 strategies for choosing assignment sets:
   * just locations, just units and numerals, or both.
   */
  public enum Strategy {
    LOCATION {
      @Override protected AssignmentSetChoice chooseAssignmentSet(Marks marks, Random random) {
        return chooseLocationAssignmentSet(marks, random);
      }
    },
    UNIT {
      @Override protected AssignmentSetChoice chooseAssignmentSet(Marks marks, Random random) {
        return chooseUnitNumeralAssignmentSet(marks, random, new AssignmentSetChoice());
      }
    },
    ALL {
      @Override protected AssignmentSetChoice chooseAssignmentSet(Marks marks, Random random) {
        return chooseUnitNumeralAssignmentSet(marks, random,
                                              chooseLocationAssignmentSet(marks, random));
      }
    };

    abstract AssignmentSetChoice chooseAssignmentSet(Marks marks, Random random);
  }

  private final Grid start;
  private final Random random;
  private final Strategy strategy;
  private final boolean fat;

  public Solver(Grid start, Random random, Strategy strategy, boolean fat) {
    this.start = start;
    this.random = random;
    this.strategy = strategy;
    this.fat = fat;
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

    private final ArrayDeque<Assignment> worklist = new ArrayDeque<Assignment>();

    private Iter() {
      Marks.Builder builder = fat ? Marks.Fat.builder() : Marks.builder();
      if (!builder.assignAll(start)) {
        // This puzzle is not solvable.
        next = null;
        nextComputed = true;
      } else if (!pushNextAssignments(builder.build())) {
        // This puzzle was solved by the initial assignments.
        next = builder.asGrid();
        nextComputed = true;
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

    private Grid computeNext() {
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
      if (list.isEmpty()) return false;  // No more assignments.

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
     * available in the given marks.
     */
    private List<Assignment> chooseNextAssignments(Marks marks) {
      return strategy.chooseAssignmentSet(marks, random).assignments;
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

  private static class AssignmentSetChoice {
    final int size;
    final int count;
    final List<Assignment> assignments;

    AssignmentSetChoice() {
      this.size = 9;
      this.count = 0;
      this.assignments = Collections.emptyList();
    }

    AssignmentSetChoice(int size, int count, List<Assignment> assignments) {
      this.size = size;
      this.count = count;
      this.assignments = assignments;
    }
  }

  protected static AssignmentSetChoice chooseLocationAssignmentSet(Marks marks, Random random) {
    int size = 9;
    int count = 0;
    Location current = null;
    for (Location loc : Location.ALL) {
      NumSet possible = marks.get(loc);
      if (possible.size() <= 1 || possible.size() > size) continue;
      if (possible.size() < size) {
        count = 0;
        size = possible.size();
      }
      // Choose a random location at the smallest size.
      if (random.nextInt(++count) == 0) {
        current = loc;
      }
    }
    if (count == 0) return new AssignmentSetChoice();
    List<Assignment> assignments = Lists.newArrayList();
    for (Numeral num : marks.get(current)) {
      assignments.add(new Assignment(marks, current, num));
    }
    return new AssignmentSetChoice(size, count, assignments);
  }

  protected static AssignmentSetChoice chooseUnitNumeralAssignmentSet(
      Marks marks, Random random, AssignmentSetChoice prev) {
    int size = prev.size;
    int count = prev.count;
    List<Assignment> current = prev.assignments;
    for (Unit unit : Unit.allUnits()) {
      for (Numeral num : Numeral.ALL) {
        UnitSubset possible = marks.get(unit, num);
        if (possible.size() <= 1 || possible.size() > size) continue;
        if (possible.size() < size) {
          count = 0;
          size = possible.size();
        }
        // Choose a random assignment set at the smallest size.
        if (random.nextInt(++count) == 0) {
          current = Lists.newArrayList();
          for (Location loc : possible) {
            current.add(new Assignment(marks, loc, num));
          }
        }
      }
    }
    if (count == 0) return new AssignmentSetChoice();
    return new AssignmentSetChoice(size, count, current);
  }
}
