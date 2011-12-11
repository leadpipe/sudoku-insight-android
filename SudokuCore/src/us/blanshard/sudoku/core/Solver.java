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
 * <p> The worklist approach and the trying of alternative worklists if the
 * first one is taking too long are due to David Bau in Heidi's Sudoku Hintpad
 * (https://www.assembla.com/spaces/jssudoku/team).
 *
 * @author Luke Blanshard
 */
public final class Solver implements Iterable<Grid> {

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, Random random, Strategy strategy) {
    return new Solver(start, random, strategy).result();
  }

  /**
   * A summary of a solver's work.
   */
  public final class Result {
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
   * This enumeration provides strategies for solving a puzzle.
   */
  public enum Strategy {
    SIMPLE {
      @Override Iter getIterator(Solver solver) {
        int numSteps = 0;
        Worklist worklist = solver.new Worklist();
        while (!worklist.isCompleteOrFoundSomething()) {
          numSteps += worklist.run(10000);
        }
        return solver.new Iter(worklist, numSteps);
      }
    },
    FAST_100_1_60_10 {  // Roughly corresponds to David Bau's parameters
      @Override Iter getIterator(Solver solver) {
        return getIterator(solver, 100, 1.0, 60, 10);
      }
    },
    FAST_75_2_40_0 {
      @Override Iter getIterator(Solver solver) {
        return getIterator(solver, 50, 2.0, 30, 0);
      }
    },
    FAST_50_2_30_0 {
      @Override Iter getIterator(Solver solver) {
        return getIterator(solver, 50, 2.0, 30, 0);
      }
    },
    FAST_40_2_25_0 {
      @Override Iter getIterator(Solver solver) {
        return getIterator(solver, 50, 2.0, 30, 0);
      }
    };

    /**
     * Returns an Iter instance that corresponds to this strategy.
     */
    abstract Iter getIterator(Solver solver);

    private static Iter getIterator(Solver solver, int longSteps, double factor, int shortSteps, int delta) {
      int numSteps = 0;
      Worklist worklist;
      // The idea behind this is that some grids that take many steps only do so
      // under peculiar circumstances, so it may be beneficial to try
      // alternative paths to see if a short solution can be found.  The mean
      // number of steps required to solve a grid is about 23, but some grids
      // require thousands or tens of thousands of steps.
      Worklist longRunner = solver.new Worklist();
      if (longRunner.isCompleteOrFoundSomething()) {
        worklist = longRunner;
      } else {
        while (true) {
          numSteps += longRunner.run(longSteps);
          if (longRunner.isCompleteOrFoundSomething()) {
            worklist = longRunner;
            break;
          }
          Worklist shortRunner = solver.new Worklist();
          // Note we don't have to ask if the short runner found something at
          // construction time, because the long runner didn't.
          numSteps += shortRunner.run(shortSteps);
          if (shortRunner.isCompleteOrFoundSomething()) {
            worklist = shortRunner;
            break;
          }
          shortSteps += delta;
          longSteps = (int) (longSteps * factor);
        }
      }
      return solver.new Iter(worklist, numSteps);
    }
  }

  private final Grid start;
  private final Random random;
  private final Strategy strategy;
  private final Marks startMarks;
  private final Location[] locations;

  public Solver(Grid start, Random random, Strategy strategy) {
    this.start = start;
    this.random = random;
    this.strategy = strategy;

    Marks.Builder builder = Marks.builder();
    if (!builder.assignAll(start)) {
      // This puzzle is not solvable.
      this.startMarks = null;
      this.locations = null;
    } else {
      Marks marks = this.startMarks = builder.build();
      List<Location> locations = Lists.newArrayList();
      for (Location loc : Location.ALL) {
        if (marks.get(loc).size() > 1)
          locations.add(loc);
      }
      this.locations = locations.toArray(new Location[locations.size()]);
    }
  }

  @Override public Iter iterator() {
    return strategy.getIterator(this);
  }

  public Result result() {
    return new Result();
  }

  public final class Iter implements Iterator<Grid> {
    private boolean nextComputed;
    private Grid next;
    private int stepCount;
    private final Worklist worklist;

    private Iter(Worklist worklist, int stepCount) {
      this.nextComputed = true;
      this.next = worklist.getFound();
      this.stepCount = stepCount;
      this.worklist = worklist;
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
      while (!worklist.isComplete()) {
        stepCount += worklist.run(10000);
        if (worklist.getFound() != null)
          return worklist.getFound();
      }
      return null;
    }
  }

  /**
   * Forms the core of the solution iterators: a depth-first searcher for
   * solutions that can be run for a set number of steps, instead of
   * indefinitely.
   */
  public final class Worklist {
    @Nullable private Grid found;
    private final ArrayDeque<Assignment> worklist = new ArrayDeque<Assignment>();
    private final Location[] locations;

    public Worklist() {
      if (startMarks == null) {
        this.locations = null;
      } else {
        this.locations = Solver.this.locations.clone();
        Collections.shuffle(Arrays.asList(this.locations), random);
        if (!pushNextAssignments(startMarks)) {
          found = startMarks.asGrid();
        }
      }
    }

    /**
     * Returns the grid found on the most recent call to {@link #run}, or null
     * if it stopped before finding one.
     */
    @Nullable public Grid getFound() {
      return found;
    }

    /**
     * Tells whether this worklist has finished searching for solutions.
     */
    public boolean isComplete() {
      return worklist.isEmpty();
    }

    public boolean isCompleteOrFoundSomething() {
      return isComplete() || found != null;
    }

    /**
     * Runs through the remaining work, but taking not more than the given
     * number of steps.  Returns the number of steps taken in this pass.
     */
    public int run(int maxSteps) {
      found = null;
      int count = 0;
      while (!worklist.isEmpty() && count++ < maxSteps) {
        Assignment assignment = worklist.removeFirst();
        Marks.Builder builder = assignment.marks.asBuilder();
        if (builder.assign(assignment.location, assignment.numeral)) {
          Marks marks = builder.build();
          if (!pushNextAssignments(marks)) {
            found = marks.asGrid();
            break;
          }
        }
      }
      return count;
    }

    /** Returns true if there are more assignments to be made. */
    private boolean pushNextAssignments(Marks marks) {
      Assignment[] assignments = chooseNextAssignments(marks);
      if (assignments == null) return false;  // No more assignments.

      // Push all possibilities onto the stack in random order.
      for (int last = assignments.length; last-- > 0; ) {
        int index = random.nextInt(last + 1);
        worklist.addFirst(assignments[index]);
        if (index != last) assignments[index] = assignments[last];
      }
      return true;
    }

    /**
     * Chooses a random set of mutually exclusive assignments from those
     * available in the given marks, or null if there aren't any left.
     */
    @Nullable private Assignment[] chooseNextAssignments(Marks marks) {
      int size = 9;
      int count = 0;
      Location current = null;
      for (Location loc : locations) {
        NumSet possible = marks.get(loc);
        if (possible.size() < 2 || possible.size() > size) continue;
        if (possible.size() < size) {
          count = 0;
          size = possible.size();
        }
        // Take the first size-2 location:
        if (size == 2)
          return makeAssignments(marks, loc);

        // Maintain a random choice of the smallest size seen so far.
        if (random.nextInt(++count) == 0) {
          current = loc;
        }
      }
      if (count == 0) return null;
      return makeAssignments(marks, current);
    }
  }

  private static Assignment[] makeAssignments(Marks marks, Location loc) {
    NumSet set = marks.get(loc);
    Assignment[] answer = new Assignment[set.size()];
    int i = 0;
    for (Numeral n : set)
      answer[i++] = new Assignment(marks, loc, n);
    return answer;
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
}
