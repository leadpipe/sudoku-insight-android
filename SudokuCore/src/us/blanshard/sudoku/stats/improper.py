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
"""Summarizes the output of Improper."""

import locale
import os
import re
import sys

from collections import defaultdict
from datetime import datetime

from django.conf import settings
from django.template.loader import render_to_string

from stats import RunningStat

locale.setlocale(locale.LC_ALL, "en_US")
settings.configure(DEBUG=True, TEMPLATE_DEBUG=True, TEMPLATE_DIRS=("."), TEMPLATE_STRING_IF_INVALID = "%s")

def SummarizeLines(lines, max_solns):
  def Stat(): return {"solns": RunningStat(), "holes": RunningStat()}
  def Stats(): return [Stat() for _ in range(max_solns)]
  def AllStats(): return {"overall": Stat(), "by_solns": Stats()}
  all = AllStats()
  symmetries = defaultdict(AllStats)
  for line in lines:
    fields = line.split("\t")
    symmetry = fields[0]
    solns = int(fields[1])
    holes = int(fields[2])
    solns_index = solns - 1

    all["overall"]["solns"].append(solns)
    all["overall"]["holes"].append(holes)
    all["by_solns"][solns_index]["solns"].append(solns)
    all["by_solns"][solns_index]["holes"].append(holes)
    symmetries[symmetry]["overall"]["solns"].append(solns)
    symmetries[symmetry]["overall"]["holes"].append(holes)
    symmetries[symmetry]["by_solns"][solns_index]["solns"].append(solns)
    symmetries[symmetry]["by_solns"][solns_index]["holes"].append(holes)

  return (all, symmetries)

def ConstructData(file_in):
  summary = file_in.next()
  if summary.startswith("Generating"):
    line = file_in.next()
  else:
    return Usage("No header lines found")

  headers = line.split("\t")

  max_solns = int(re.search(r"\((\d+),", summary).group(1))
  (all, symmetries) = SummarizeLines(file_in, max_solns)

  symmetryIds = sorted(symmetries.keys())

  def Symmetry(id):
    return {
      "name": id,
      "solns": symmetries[id]["overall"]["solns"],
      "holes": symmetries[id]["overall"]["holes"],
      "by_solns": symmetries[id]["by_solns"],
      }

  return {
    "summary": summary,
    "when": datetime.fromtimestamp(os.fstat(file_in.fileno()).st_mtime),
    "count": all["overall"]["solns"].count_formatted(),
    "all": all,
    "symmetries": [Symmetry(id) for id in symmetryIds],
    }


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
    file_out.write(render_to_string("improper_template.html", data).encode("UTF-8"))

  print "Summary written to %s" % out_name

if __name__ == "__main__":
  main()
