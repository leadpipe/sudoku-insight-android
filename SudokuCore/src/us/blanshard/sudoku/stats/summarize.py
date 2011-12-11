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

def summarize_lines(lines, nstrategies):
  per_step = [RunningStat() for _ in range(nstrategies)]
  detailed = [defaultdict(lambda: {"overall": RunningStat(),
                                   "by_steps": defaultdict(RunningStat)}) for _ in range(nstrategies)]
  steps = [RunningStat() for _ in range(nstrategies)]
  for line in lines:
    fields = line.split("\t")
    for n in range(nstrategies):
      offset = 3 * n + 4
      num_solutions = int(fields[offset])
      num_steps = int(fields[offset + 1])
      micros = float(fields[offset + 2])
      per_step[n].append(micros/num_steps if num_steps > 0 else micros)
      detailed[n][num_solutions]["by_steps"][num_steps].append(micros)
      detailed[n][num_solutions]["overall"].append(micros)
      steps[n].append(num_steps)
  return zip(per_step, detailed, steps)

def construct_data(file_in):
  line = file_in.next()
  if line.startswith("Generating"):
    line = file_in.next()
  else:
    return usage("No header lines found")

  headers = line.split("\t")
  strategies = [h.split(":")[0] for h in headers if h.endswith(":Num Solutions")]
  nstrategies = len(strategies)

  summaries = summarize_lines(file_in, nstrategies)
  def for_strategy(n):
    (per_step, detailed, steps) = summaries[n]
    return {"name": strategies[n],
            "per_step": per_step,
            "steps": steps,
            "by_solutions": { num_solutions :
                                { "overall": details["overall"],
                                  "by_steps": sorted(details["by_steps"].items()) }
                              for num_solutions, details in detailed.items() }}
  return { "when": datetime.fromtimestamp(os.fstat(file_in.fileno()).st_mtime),
           "count": summaries[0][0].count_formatted(),
           "strategies": [ for_strategy(i) for i in range(nstrategies) ] }

def usage(msg = None):
  print "Usage: %s <fname>" % sys.argv[0]
  if msg:
    print msg
  exit(1)

def main(args):
  if len(args) != 1:
    usage();
  with open(args[0], "r") as file_in:
    data = construct_data(file_in)

  (root, _) = os.path.splitext(args[0])
  out_name = "%s-summary.html" % root

  with open(out_name, "w") as file_out:
    file_out.write(render_to_string("summary_template.html", data).encode("UTF-8"))

  print "Summary written to %s" % out_name

if __name__ == "__main__":
  main(sys.argv[1:])
