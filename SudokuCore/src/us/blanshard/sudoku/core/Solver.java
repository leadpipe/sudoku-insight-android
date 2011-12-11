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
    return new Solver(start, random, strategy).result();
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
   * This enumeration provides strategies for solving a puzzle.
   */
  public enum Strategy {
    BASE {
      @Override Iter getIterator(Solver solver) {
        return solver.new Iter();
      }
    },
    LIST {
      @Override Iter getIterator(Solver solver) {
        return solver.new IterList();
      }
    },
    SHUFFLE {
      @Override Iter getIterator(Solver solver) {
        return solver.new IterShuffle();
      }
    },
    CYCLE10 {
      @Override Iter getIterator(Solver solver) {
        return solver.new IterCycle(10);
      }
    },
    CYCLE25 {
      @Override Iter getIterator(Solver solver) {
        return solver.new IterCycle(25);
      }
    },
    CYCLE60 {
      @Override Iter getIterator(Solver solver) {
        return solver.new IterCycle(60);
      }
    };

    /**
     * Returns the Iter subclass instance that corresponds to this strategy.
     */
    abstract Iter getIterator(Solver solver);
  }

  private final Grid start;
  private final Random random;
  private final Strategy strategy;

  public Solver(Grid start, Random random, Strategy strategy) {
    this.start = start;
    this.random = random;
    this.strategy = strategy;
  }

  @Override public Iter iterator() {
    return strategy.getIterator(this);
  }

  public Result result() {
    return new Result();
  }

  public class Iter implements Iterator<Grid> {
    protected boolean nextComputed;
    private Grid next;
    private int stepCount;
    protected final ArrayDeque<Assignment> worklist = new ArrayDeque<Assignment>();

    protected Iter() {
      Marks.Builder builder = Marks.builder();
      if (!builder.assignAll(start)) {
        // This puzzle is not solvable.
        next = null;
        nextComputed = true;
      } else if (!pushInitialAssignments(builder.build())) {
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

    @Nullable private Grid computeNext() {
      while (!worklist.isEmpty()) {
        ++stepCount;
        Assignment assignment = worklist.removeFirst();
        Marks.Builder builder = assignment.marks.asBuilder();
        if (builder.assign(assignment.location, assignment.numeral)) {
          Marks marks = builder.build();
          if (!pushNextAssignments(marks)) return marks.asGrid();
        }
      }
      return null;  // All done.
    }

    protected boolean pushInitialAssignments(Marks marks) {
      return pushNextAssignments(marks);
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
    @Nullable protected Assignment[] chooseNextAssignments(Marks marks) {
      int size = 9;
      int count = 0;
      Location current = null;
      for (Location loc : Location.ALL) {
        NumSet possible = marks.get(loc);
        if (possible.size() < 2 || possible.size() > size) continue;
        if (possible.size() < size) {
          count = 0;
          size = possible.size();
        }
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

  private class IterList extends Iter {
    protected /*final*/ List<Location> locations;

    @Override protected boolean pushInitialAssignments(Marks marks) {
      makeLocations(marks);
      return super.pushInitialAssignments(marks);
    }

    protected void makeLocations(Marks marks) {
      locations = Lists.newArrayList();
      for (Location loc : Location.ALL)
        if (marks.get(loc).size() > 1)
          locations.add(loc);
    }

    @Override @Nullable protected Assignment[] chooseNextAssignments(Marks marks) {
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
        // Maintain a random choice of the smallest size seen so far.
        if (random.nextInt(++count) == 0) {
          current = loc;
        }
      }
      if (count == 0) return null;
      return makeAssignments(marks, current);
    }
  }

  private class IterShuffle extends IterList {
    @Override protected void makeLocations(Marks marks) {
      super.makeLocations(marks);
      Collections.shuffle(locations, random);
    }

    @Override @Nullable protected Assignment[] chooseNextAssignments(Marks marks) {
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

  private class IterCycle extends IterShuffle {
    private final int factor;
    private /*final*/ Iterator<Location> locIterator;

    IterCycle(int factor) {
      this.factor = factor;
    }

    @Override protected void makeLocations(Marks marks) {
      super.makeLocations(marks);
      locIterator = Iterators.cycle(locations);
    }

    @Override @Nullable protected Assignment[] chooseNextAssignments(Marks marks) {
      int size = 10;
      int max = locations.size();
      Location current = null;
      for (int count = 0; count < max; ++count) {
        Location loc = locIterator.next();
        NumSet possible = marks.get(loc);
        if (possible.size() > 1 && possible.size() < size) {
          current = loc;
          size = possible.size();
          max = Math.min(max, (size - 2) * factor);
        }
      }
      if (current == null) return null;
      return makeAssignments(marks, current);
    }
  }
}
