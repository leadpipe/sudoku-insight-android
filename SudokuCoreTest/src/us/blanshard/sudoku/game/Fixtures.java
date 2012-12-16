/*
Copyright 2012 Google Inc.

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
package us.blanshard.sudoku.game;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.gen.Symmetry;

import com.google.common.base.Predicate;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.Random;

public class Fixtures {
  private static final long SEED = 123;

  static final Grid puzzle = Generator.SIMPLE.generate(new Random(SEED), Symmetry.BLOCKWISE);
  static final Location openLocation = Iterables.find(Location.ALL, new Predicate<Location>() {
    @Override public boolean apply(Location loc) {
      return !puzzle.containsKey(loc);
    }
  });

  private static final long ONE_MS = MILLISECONDS.toNanos(1);

  // A ticker that advances by one millisecond each time it's read.
  static Ticker fakeTicker() {
    return new Ticker() {
      long value = 0;
      @Override public long read() { return value += ONE_MS; }
    };
  }

  static Sudoku makeGame(Grid puzzle) {
    return makeGame(puzzle, 0, fakeTicker());
  }

  static Sudoku makeGame(Grid puzzle, Sudoku.Registry registry) {
    return new Sudoku(puzzle, registry, ImmutableList.<Move>of(), 0, fakeTicker())
        .resume();
  }

  static Sudoku makeGame(Grid puzzle, Ticker ticker) {
    return makeGame(puzzle, 0, ticker);
  }

  static Sudoku makeGame(Grid puzzle, long initialMillis, Ticker ticker) {
    return new Sudoku(
        puzzle, Sudoku.nullRegistry(), ImmutableList.<Move>of(), initialMillis, ticker)
        .resume();
  }
}
