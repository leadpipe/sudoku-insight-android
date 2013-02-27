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
"""Summarizes the output of GenStats."""

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

def SummarizeLines(lines, ngenerators):
  def Stat(): return {"time": RunningStat(), "clues": RunningStat()}
  def Stats(): return [Stat() for _ in range(ngenerators)]
  def AllStats(): return {"overall": Stat(), "by_generator": Stats()}
  symmetries = defaultdict(AllStats)
  generators = [AllStats() for _ in range(ngenerators)]
  for line in lines:
    fields = line.split("\t")
    symmetry = fields[0]
    time = [float(fields[n]) / 1000.0 for n in range(3, len(fields), 2)]
    clues = [int(fields[n]) for n in range(2, len(fields), 2)]

    for n in range(ngenerators):
      symmetries[symmetry]["overall"]["time"].append(time[n])
      symmetries[symmetry]["overall"]["clues"].append(clues[n])
      symmetries[symmetry]["by_generator"][n]["time"].append(time[n])
      symmetries[symmetry]["by_generator"][n]["clues"].append(clues[n])

      generators[n]["overall"]["time"].append(time[n])
      generators[n]["overall"]["clues"].append(clues[n])

      for m in range(ngenerators):
        generators[n]["by_generator"][m]["time"].append(time[n] - time[m])
        generators[n]["by_generator"][m]["clues"].append(clues[n] - clues[m])

  return (symmetries, generators)

def ConstructData(file_in):
  line = file_in.next()
  if line.startswith("Generating"):
    line = file_in.next()
  else:
    return Usage("No header lines found")

  headers = line.split("\t")
  generatorIds = [h.split(":")[0] for h in headers if h.endswith(":Num Clues")]
  ngenerators = len(generatorIds)

  (symmetries, generators) = SummarizeLines(file_in, ngenerators)

  symmetryIds = sorted(symmetries.keys())

  def ToName(id):
    return " ".join([w.capitalize() for w in id.split("_")])

  def Generator(n):
    return {
      "name": ToName(generatorIds[n]),
      "time": generators[n]["overall"]["time"],
      "clues": generators[n]["overall"]["clues"],
      "by_generator": generators[n]["by_generator"],
      }

  def Symmetry(id):
    return {
      "name": ToName(id),
      "time": symmetries[id]["overall"]["time"],
      "clues": symmetries[id]["overall"]["clues"],
      "by_generator": symmetries[id]["by_generator"],
      }

  return {
    "count": generators[0]["overall"]["time"].count_formatted(),
    "when": datetime.fromtimestamp(os.fstat(file_in.fileno()).st_mtime),
    "generators": [Generator(n) for n in range(ngenerators)],
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
    file_out.write(render_to_string("summary_template.html", data).encode("UTF-8"))

  print "Summary written to %s" % out_name

if __name__ == "__main__":
  main()
