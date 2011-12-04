"""Class for tracking the mean and standard deviation of a series of values."""

import math

class RunningStat(object):
  """Computes the running mean/variance/stddev of the values added.

  See http://www.johndcook.com/standard_deviation.html."""
  def __init__(self, fmt="{:8.5n}", kfmt="{:n}"):
    self.k = 0
    self.m = 0.0
    self.s = 0.0
    self.fmt = fmt
    self.kfmt = kfmt

  def __repr__(self):
    return "<RunningStat %d: %g %g %g>" % (len(self), self.mean(), self.variance(), self.standard_deviation())

  def __len__(self):
    """Returns the number of values appended to the statistic.  Call using the
       built-in function len(stat)."""
    return self.k

  def append(self, x):
    """Adds the given value to the statistic, updating estimates."""
    self.k += 1
    prev_m = self.m
    self.m = m = prev_m + (x - prev_m) / self.k
    self.s += (x - prev_m) * (x - m)

  def mean(self):
    return self.m

  def variance(self):
    return self.s / (self.k - 1) if self.k > 1 else 0.0

  def standard_deviation(self):
    return math.sqrt(self.variance())

  def count_formatted(self):
    return self.kfmt.format(self.k)

  def mean_formatted(self):
    return self.fmt.format(self.mean())

  def variance_formatted(self):
    return self.fmt.format(self.variance())

  def standard_deviation_formatted(self):
    return self.fmt.format(self.standard_deviation())
