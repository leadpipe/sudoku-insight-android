# Copyright 2013 Luke Blanshard
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Filters out the sporadically slow leaving the truly pathological."""

import sys

def main():
  results = []
  for line in sys.stdin:
    fields = line.split("\t")
    steps = [int(fields[i]) for i in range(3, len(fields), 2)]
    slow_count = sum([s > 32 for s in steps])
    avg_slow = sum([s for s in steps if s > 32]) / slow_count
    if slow_count * avg_slow > 500:
      results.append((avg_slow, slow_count, fields[0], fields[1], fields[2]))

  for result in sorted(results, reverse=True):
    print "{}\t{}\t{}\t{}\t{}".format(*result)


if __name__ == "__main__":
  main()
