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
import javax.annotation.concurrent.Immutable;

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
@Immutable
public final class Solver implements Iterable<Grid> {

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start) {
    return solve(start, new Random());
  }

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, int maxSolutions) {
    return solve(start, maxSolutions, new Random());
  }

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, Random random) {
    return solve(start, random, Strategy.FAST);
  }

  /**
   * Solves the given starting grid, returns a summary of the result.
   */
  public static Result solve(Grid start, int maxSolutions, Random random) {
    return solve(start, maxSolutions, random, Strategy.FAST);
  }

  /**
   * Solves the given starting grid using the given strategy, returns a summary
   * of the result.
   */
  public static Result solve(Grid start, Random random, Strategy strategy) {
    return solve(start, 1, random, strategy);
  }

  /**
   * Solves the given starting grid using the given strategy, returns a summary
   * of the result.
   */
  public static Result solve(Grid start, int maxSolutions, Random random, Strategy strategy) {
    return new Solver(start, random, strategy).result(maxSolutions);
  }

  /**
   * A summary of a solver's work.
   */
  @Immutable
  public final class Result {
    public final Grid start;
    public final int numSolutions;  // maxSolutions + 1 if more than maxSolutions
    public final int numSteps;  // The total number
    public final int numStepsAfterSolution;
    @Nullable public final Grid solution;  // Not null when numSolutions == 1
    @Nullable public final Grid intersection;  // Not null when numSolutions in 1..maxSolutions

    public Result(int maxSolutions) {
      Iter iter = iterator();
      int count = 0;
      Grid grid = null;
      Grid.Builder builder = null;
      int steps = 0;
      while (iter.hasNext() && ++count <= maxSolutions) {
        grid = iter.next();
        steps = iter.getStepCount();
        if (builder == null) builder = grid.toBuilder();
        else builder.intersect(grid);
      }
      this.start = Solver.this.start;
      this.numSolutions = count;
      this.numSteps = iter.getStepCount();
      this.numStepsAfterSolution = count == 0 ? 0 : iter.getStepCount() - steps;
      this.solution = count == 1 ? grid : null;
      this.intersection = (count > 0 && count <= maxSolutions) ? builder.build() : null;
    }
  }

  /**
   * This enumeration provides strategies for solving a puzzle.
   */
  public enum Strategy {
    SIMPLE {
      @Override public Iter getIterator(Solver solver) {
        int numSteps = 0;
        Worklist worklist = solver.new Worklist();
        while (!worklist.isCompleteOrFoundSomething()) {
          numSteps += worklist.run(10000);
        }
        return solver.new Iter(worklist, numSteps);
      }
    },
    FAST {
      @Override public Iter getIterator(Solver solver) {
        // Lots of testing to arrive at these values.
        return solver.makeIterator(50, 1.5, 25, 5);
      }
    };

    /**
     * Returns an Iter instance that corresponds to this strategy.
     */
    public abstract Iter getIterator(Solver solver);
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
    if (!builder.assignAllRecursively(start)) {
      // This puzzle is not solvable.
      this.startMarks = null;
      this.locations = null;
    } else {
      Marks marks = this.startMarks = builder.build();
      List<Location> locations = Lists.newArrayList();
      for (Location loc : Location.all()) {
        if (marks.get(loc).size() > 1)
          locations.add(loc);
      }
      this.locations = locations.toArray(new Location[locations.size()]);
    }
  }

  @Override public Iter iterator() {
    return strategy.getIterator(this);
  }

  public Result result(int maxSolutions) {
    return new Result(maxSolutions);
  }

  /**
   * Makes an iterator using several parameters to control the creation and
   * running of the worklist the iterator will use.
   *
   * <p> The idea behind this is that some grids that take many steps only do so
   * under peculiar circumstances, so it may be beneficial to try alternative
   * paths to see if a short solution can be found.  The mean number of steps
   * required to solve a grid is about 23, but some grids require thousands or
   * tens of thousands of steps.
   */
  public Iter makeIterator(int longSteps, double factor, int shortSteps, int delta) {
    int numSteps = 0;
    Worklist worklist;
    Worklist longRunner = new Worklist();
    if (longRunner.isCompleteOrFoundSomething()) {
      worklist = longRunner;
    } else {
      while (true) {
        numSteps += longRunner.run(longSteps);
        if (longRunner.isCompleteOrFoundSomething()) {
          worklist = longRunner;
          break;
        }
        Worklist shortRunner = new Worklist();
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
    return new Iter(worklist, numSteps);
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
    private final ArrayDeque<WorkItem> worklist = new ArrayDeque<WorkItem>();
    private final Location[] locations;

    public Worklist() {
      if (startMarks == null) {
        this.locations = null;
      } else {
        this.locations = Solver.this.locations.clone();
        Collections.shuffle(Arrays.asList(this.locations), random);
        if (!pushNextItems(startMarks)) {
          found = startMarks.toGrid();
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
        WorkItem item = worklist.removeFirst();
        Marks.Builder builder = item.marks.toBuilder();
        if (builder.assignRecursively(item.location, item.numeral)) {
          Marks marks = builder.build();
          if (!pushNextItems(marks)) {
            found = marks.toGrid();
            break;
          }
        }
      }
      return count;
    }

    /** Returns true if there is more work to do. */
    private boolean pushNextItems(Marks marks) {
      WorkItem[] items = chooseNextItems(marks);
      if (items == null) return false;  // No more work.

      // Push all possibilities onto the stack in random order.
      for (int last = items.length; last-- > 0; ) {
        int index = random.nextInt(last + 1);
        worklist.addFirst(items[index]);
        if (index != last) items[index] = items[last];
      }
      return true;
    }

    /**
     * Chooses a random set of mutually exclusive assignments from those
     * available in the given marks, or null if there aren't any left.
     */
    @Nullable private WorkItem[] chooseNextItems(Marks marks) {
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
          return makeItems(marks, loc);

        // Maintain a random choice of the smallest size seen so far.
        if (random.nextInt(++count) == 0) {
          current = loc;
        }
      }
      if (count == 0) return null;
      return makeItems(marks, current);
    }
  }

  private static WorkItem[] makeItems(Marks marks, Location loc) {
    NumSet set = marks.get(loc);
    WorkItem[] answer = new WorkItem[set.size()];
    int i = 0;
    for (Numeral n : set)
      answer[i++] = new WorkItem(marks, loc, n);
    return answer;
  }

  private static class WorkItem {
    final Marks marks;
    final Location location;
    final Numeral numeral;

    WorkItem(Marks marks, Location location, Numeral numeral) {
      this.marks = marks;
      this.location = location;
      this.numeral = numeral;
    }
  }
}
