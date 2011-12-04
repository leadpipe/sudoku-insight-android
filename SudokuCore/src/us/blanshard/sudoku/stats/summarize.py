# Copyright 2011 Google Inc.
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
"""Summarizes the output of GenStats, read from stdin."""

import locale
import os
import sys

from collections import defaultdict
from datetime import datetime

from django.conf import settings
from django.template.loader import render_to_string

from stats import RunningStat

locale.setlocale(locale.LC_ALL, "en_US")
settings.configure(DEBUG=True, TEMPLATE_DEBUG=True, TEMPLATE_DIRS=("."), TEMPLATE_STRING_IF_INVALID = "%s")

def summarize_lines(lines):
  per_step = [RunningStat() for _ in range(6)]
  detailed = [defaultdict(lambda: {"overall": RunningStat(),
                                   "by_steps": defaultdict(RunningStat)}) for _ in range(6)]
  steps = [RunningStat() for _ in range(6)]
  for line in lines:
    fields = line.split("\t")
    for n in range(6):
      offset = 3 * n + 4
      num_solutions = int(fields[offset])
      num_steps = 1 + int(fields[offset + 1])  # Count the initial building as one step
      micros = float(fields[offset + 2])
      per_step[n].append(micros/num_steps)
      detailed[n][num_solutions]["by_steps"][num_steps].append(micros)
      detailed[n][num_solutions]["overall"].append(micros)
      steps[n].append(num_steps)
  return zip(per_step, detailed, steps)

def construct_data(file_in):
  line = file_in.next()
  if line.startswith("Generating"):
    file_in.next()  # Skip following header line too
  else:
    file_in.seek(0)  # Jump back to the beginning: no stderr lines

  summaries = summarize_lines(file_in)
  def versions(n):
    slice = summaries[2 * n : 2 * n + 2]
    return [ {"per_step": per_step,
              "by_solutions": { num_solutions :
                                  { "overall": details["overall"],
                                    "by_steps": sorted(details["by_steps"].items()) }
                                for num_solutions, details in detailed.items() }}
             for (per_step, detailed, _) in slice]
  return { "when": datetime.fromtimestamp(os.fstat(file_in.fileno()).st_mtime),
           "count": summaries[0][0].count_formatted(),
           "algorithms": [ { "name": "Locations",
                             "steps": summaries[0][2],
                             "versions": versions(0) },
                           { "name": "Blocks/Numerals",
                             "steps": summaries[2][2],
                             "versions": versions(1) },
                           { "name": "All",
                             "steps": summaries[4][2],
                             "versions": versions(2) } ] }

def main(prog, args):
  if len(args) != 1:
    print "Usage: %s <fname>" % prog
    exit(1)

  with open(args[0], "r") as file_in:
    data = construct_data(file_in)

  (root, _) = os.path.splitext(args[0])
  out_name = "%s-summary.html" % root

  with open(out_name, "w") as file_out:
    file_out.write(render_to_string("summary_template.html", data).encode("UTF-8"))

  print "Summary written to %s" % out_name

if __name__ == "__main__":
  main(sys.argv[0], sys.argv[1:])
