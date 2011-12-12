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

def SummarizeLines(lines, nstrategies):
  detailed = [defaultdict(lambda: {"steps": RunningStat(),  # Avg steps by #solutions
                                   "per_step": RunningStat(),  # Avg time per step by #solutions
                                   "overall": RunningStat(),  # Avg time by #solutions
                                   "by_steps": defaultdict(RunningStat)})  # Time by #steps & #solutions
              for _ in range(nstrategies)]
  steps = [RunningStat() for _ in range(nstrategies)]
  per_step = [RunningStat() for _ in range(nstrategies)]
  total = [RunningStat() for _ in range(nstrategies)]
  for line in lines:
    fields = line.split("\t")
    num_solutions = int(fields[4])
    for n in range(nstrategies):
      offset = 2 * n + 5
      num_steps = int(fields[offset + 0])
      micros = float(fields[offset + 1])
      micros_per_step = micros/num_steps if num_steps > 0 else micros
      detailed[n][num_solutions]["steps"].append(num_steps)
      detailed[n][num_solutions]["per_step"].append(micros_per_step)
      detailed[n][num_solutions]["overall"].append(micros)
      detailed[n][num_solutions]["by_steps"][num_steps].append(micros)
      steps[n].append(num_steps)
      per_step[n].append(micros_per_step)
      total[n].append(micros)
  return zip(detailed, steps, per_step, total)

def ConstructData(file_in):
  line = file_in.next()
  if line.startswith("Generating"):
    line = file_in.next()
  else:
    return Usage("No header lines found")

  headers = line.split("\t")
  strategies = [h.split(":")[0] for h in headers if h.endswith(":Num Steps")]
  nstrategies = len(strategies)

  summaries = SummarizeLines(file_in, nstrategies)
  def ForStrategy(n):
    (detailed, steps, per_step, total) = summaries[n]
    return {"name": strategies[n],
            "steps": steps,
            "per_step": per_step,
            "total": total,
            "by_solutions": { num_solutions :
                                { "steps": details["steps"],
                                  "per_step": details["per_step"],
                                  "overall": details["overall"],
                                  "by_steps": sorted(details["by_steps"].items()) }
                              for num_solutions, details in detailed.items() }}
  return { "when": datetime.fromtimestamp(os.fstat(file_in.fileno()).st_mtime),
           "count": summaries[0][1].count_formatted(),
           "strategies": [ ForStrategy(i) for i in range(nstrategies) ] }

def Usage(msg = None):
  print "Usage: %s <fname>" % sys.argv[0]
  if msg:
    print msg
  exit(1)

def main():
  if len(sys.argv) != 2:
    Usage();
  with open(sys.argv[1], "r") as file_in:
    data = ConstructData(file_in)

  (root, _) = os.path.splitext(sys.argv[1])
  out_name = "%s-summary.html" % root

  with open(out_name, "w") as file_out:
    file_out.write(render_to_string("summary_template.html", data).encode("UTF-8"))

  print "Summary written to %s" % out_name

if __name__ == "__main__":
  main()
