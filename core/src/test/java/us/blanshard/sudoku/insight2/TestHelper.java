package us.blanshard.sudoku.insight2;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

import static us.blanshard.sudoku.core.NumSetTest.set;

public class TestHelper {
  public static Grid g(String s) { return Grid.fromString(s); }
  public static Marks m(String s) { return Marks.builder(g(s)).build(); }
  public static Numeral n(int num) { return Numeral.of(num); }
  public static NumSet ns(int... nums) { return set(nums); }
  public static Row r(int num) { return Row.of(num); }
  public static Column c(int num) { return Column.of(num); }
  public static Block b(int num) { return Block.of(num); }
  public static UnitNumeral un(Unit unit, int num) { return UnitNumeral.of(unit, n(num)); }
  public static UnitSubset us(Unit unit, int... nums) { return UnitSubset.ofBits(unit, ns(nums).bits); }
  public static Location l(int row, int col) { return Location.of(r(row), c(col)); }
  public static Assignment a(Location loc, int num) { return Assignment.of(loc, n(num)); }
  public static Assignment a(int row, int col, int num) { return Assignment.of(l(row, col), n(num)); }
}
