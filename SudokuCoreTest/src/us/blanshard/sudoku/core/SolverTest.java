package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SolverTest {
  private final Grid start;
  private final int numSolutions;

  @Parameters public static Collection<Object[]> getParams() {
    return Arrays.asList(new Object[][]{
        { "...8.9..6.23.........6.8...7....1..2...45...9......6......7......1.46.....3......",-1 },  // Broken
        { "1....6....59.....82....8....45...3....3...7....6..3.54...325..6........17389.....", 0 },  // No solution
        { ".6.5.4.3.1...9...8.........9...5...6.4.6.2.7.7...4...5.........4...8...1.5.2.3.4.", 1 },  // Unique solution
        { ".....6....59.....82....8....45........3........6..3.54...325..6..................", 2 },  // Multiple solutions
      });
  }

  public SolverTest(String start, int numSolutions) {
    this.start = Grid.fromString(start);
    this.numSolutions = numSolutions < 0 ? 0 : numSolutions;
    assertEquals(numSolutions < 0 ? Grid.State.BROKEN : Grid.State.INCOMPLETE,
                 this.start.getState());
  }

  @Test public void allPermutations() {
    Grid prev = null;
    for (Solver.Strategy strategy : Solver.Strategy.values()) {
      Solver.Result thinResult = Solver.solve(start, new Random(0), strategy, false);
      Solver.Result fatResult = Solver.solve(start, new Random(0), strategy, true);
      assertEquals(numSolutions, thinResult.numSolutions);
      assertEquals(numSolutions, fatResult.numSolutions);
      assertEquals(thinResult.numSteps, fatResult.numSteps);
      assertEquals(thinResult.solution, fatResult.solution);
      if (prev != null)
        assertEquals(prev, thinResult.solution);
      if (numSolutions == 1) {
        assertEquals(Grid.State.SOLVED, thinResult.solution.getState());
        assertEquals(true, thinResult.solution.isSolved());
        assertEquals(true, thinResult.solution.entrySet().containsAll(start.entrySet()));
      } else {
        assertNull(thinResult.solution);
      }
      prev = thinResult.solution;
    }
  }
}
