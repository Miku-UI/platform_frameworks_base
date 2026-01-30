#!/usr/bin/env python3
# Copyright (C) 2025 The Android Open Source Project
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

# Tool to graph AMS process dependencies.
# m graph_ams
# adb shell dumpsys activity --proto > activity.bin
# graph_ams activity.bin > activity.dot
# dot -Tsvg activity.dot -o activity.svg
# dot -Tpdf activity.dot -o activity.pdf
# dot -Tpng -Gdpii=100 activity.dot -o activity.png

import argparse
import json
import re
import sys

from frameworks.base.core.proto.android.server import activitymanagerservice_pb2
from frameworks.proto_logging.stats.enums.app_shared import app_enums_pb2

# Colours from Google Material.
BLUE = "#4285f4"
LIGHT_BLUE = "#77bdff"
RED = "#ea4335"
LIGHT_RED = "#ff5252"
YELLOW = "#fbbc05"
LIGHT_YELLOW = "#ffcc50"
GREEN = "#34a853"
LIGHT_GREEN = "#57bb8a"

def read_activity_proto(filename):
  """Read the AMS proto."""
  ams = activitymanagerservice_pb2.ActivityManagerServiceProto()
  with open(filename, "rb") as f:
    ams.ParseFromString(f.read())
  return ams

def countBindingsOut(pid, edges):
  """Count the # of bindings out from a process."""
  count = 0
  for e in edges:
    if e["source"] == str(pid):
      count += 1
  return count

def countBindingsIn(pid, edges):
  """Count the # of bindings to a process."""
  count = 0
  for e in edges:
    if e["target"] == str(pid):
      count += 1
  return count

def make_name(p):
  """Make a pretty process name."""
  return f"{p.pid}:{p.process_name}/{p.uid}"

def flag_str(flag):
  """Convert bind flags into a string."""
  return activitymanagerservice_pb2.ConnectionRecordProto.Flag.Name(flag)  + " (" + str(flag) + ")"

def schedGroup_str(schedGroup):
  """ Convert schedule group into a string."""
  return activitymanagerservice_pb2.ProcessOomProto.SchedGroup.Name(schedGroup)

def setState_str(setState):
  """ Convert set state into a string."""
  return app_enums_pb2.ProcessStateEnum.Name(setState)

def capabilityFlag_str(capabilityFlags) :
  """ Convert caoability flag into a string."""
  capability_flags = []
  for flag in capabilityFlags :
    capability_flags.append(app_enums_pb2.ProcessCapabilityEnum.Name(flag)  + " (" + str(flag) + ")")
  return capability_flags

def make_nodes(ams, edges):
  """Make a list of all the nodes."""
  procs = ams.processes
  broads = ams.broadcasts
  services = ams.services
  # Create a list of process details from 'details {}'.
  details = [
  {"pid": str(d.proc.pid),
   "uid": str(d.proc.uid),
   "setState": setState_str(d.detail.set_state) + " (" + str(d.detail.set_state) + ")",
   "schedGroup": schedGroup_str(d.sched_group) + " (" + str(d.sched_group) + ")",
   "oomAdj": str(d.oom_adj), # Universally understood OOM score.
   "setAdj": str(d.detail.set_adj), # Used for D3.js forces.
   # May remove any following data point.
   "persistent": str(d.persistent),
   "maxAdj": str(d.detail.max_adj),
   "curRawAdj": str(d.detail.cur_raw_adj),
   "setRawAdj": str(d.detail.set_raw_adj),
   "curAdj": str(d.detail.cur_adj),
   "currentState": str(d.detail.current_state),
   "lastPss": str(d.detail.last_pss),
   "lastSwapPss": str(d.detail.last_swap_pss),
   "lastCachedPss": str(d.detail.last_cached_pss),
   "numOfBindingsOut": str(countBindingsOut(d.proc.pid, edges)),
   "numOfBindingsIn": str(countBindingsIn(d.proc.pid, edges)),
   "capabilityFlags": capabilityFlag_str(d.detail.capability_flags)}
  for d in procs.lru_procs.list]
  # Create a list of processes from 'procs {}'.
  process = [{"pid": str(p.pid), "name": make_name(p), "user": str(p.user_id)} for p in procs.procs]
  if DEBUG_LOGS:
    print(process, file=sys.stderr)
  # Create a lookup map from the details list for efficient merging. Set the 'id' as the key.
  details_map = {d['pid']: d for d in details}
  nodes = []
  # Create a list of broadcasts from 'broadcasts {}'.
  broadcasts = [{"pid": str(b.pid),
                 "numberReceivers": str(b.number_receivers),
                 "broadcastIntentActions": str(f.intent_filter.actions),
                 "broadcastRequiredPermissions": str(f.required_permission)}
                for b in broads.receiver_list
                for f in b.filters]
  # Create a lookup map from the broadcasts list for efficient merging. Set the 'id' as the key.
  broadcasts_map = {b['pid']: b for b in broadcasts}
  timeMetrics = [{"pid": str(s.pid), "createRealTime":
                 {"startMs": str(s.create_real_time.start_ms),
                  "endMs": str(s.create_real_time.end_ms)},
                  "startingBgTimeout": {"endMs": str(s.starting_bg_timeout.end_ms)},
                  "lastActivityTime": {"startMs": str(s.last_activity_time.start_ms),
                  "endMs": str(s.last_activity_time.end_ms)},
                  "restartTime": {"startMs": str(s.restart_time.start_ms),
                  "endMs":str(s.restart_time.end_ms)}}
                for sbu in services.active_services.services_by_users
                for  s in sbu.service_records]
  # Create a lookup map from the timeMetrics list for efficient merging. Set the 'id' as the key.
  timeMetrics_map = {t['pid']: t for t in timeMetrics}
  # Iterate through the process list.
  for proc_item in process:
    # Start with the base process info (name, user, etc.).
    node = proc_item.copy()
    # Find the corresponding details using the id from the map.
    detail_item = details_map.get(node['pid'])
    # Find the corresponding broadcasts using the id from the map.
    broadcast_item = broadcasts_map.get(node['pid'])
    # Find the corresponding timeMetrics using the id from the map.
    timeMetric_item = timeMetrics_map.get(node['pid'])
    # If details exist for this process, merge them into the node.
    if detail_item:
      node.update(detail_item)
    # If broadcasts exist for this process, merge them into the node.
    if broadcast_item:
      node.update(broadcast_item)
    # If timeMetrics extst for this process, merge them into the node.
    if timeMetric_item:
      node.update(timeMetric_item)
    nodes.append(node)
  return nodes

def make_edges(services):
  """Make a list of all the edges."""
  edges = []
  SKIP_FLAGS = { "AUTO_CREATE" }
  for sbu in services.active_services.services_by_users:
    for s in sbu.service_records:
      src = f"{s.pid}"
      for c in s.connections:
        dst = c.client_pid
        attrs = []

        flags_full = [flag_str(f) for f in c.flags]
        flags = [f for f in flags_full if f not in SKIP_FLAGS]
        flags_str = '|'.join(flags)

        # Note that these are "reversed".  AMS tracks and dumps the connections
        # from the client perspective, while people more often think of the
        # bindings in the other direction.
        edge = {
            "source": str(dst),
            "target": str(src),
            "flagsFull": flags_full,
            "flags": flags_str,
        }
        edges.append(edge)
  return edges

def make_attr(name, value):
  """Make a dot attribute string."""
  return f"{name}=\"{value}\""

def colourize_name(name):
  """Colourize important process names."""
  COLOUR_MAP = [
    [r"chrome.*SandboxedProcess", LIGHT_BLUE],
    [r"chrome", BLUE],
    [r":system/", GREEN],
    [r":com.google.android.gms", LIGHT_GREEN],
  ]

  # Search for the first regex that matches.
  for regex, colour in COLOUR_MAP:
    if re.search(regex, name):
      return colour
  return None

def print_dot_nodes(nodes):
  """Print all the nodes based on processes."""
  # Label all the process nodes.
  for n in nodes:
    name = n["name"]
    attrs = [
      make_attr("label", name)
    ]
    colour = colourize_name(name)
    if colour:
      attrs.append(make_attr("fillcolor", colour))
      attrs.append(make_attr("style", "filled"))
    print(f"  {n["pid"]} [" + " ".join(attrs) + "]")

def print_dot_edges(edges, bindflags, highlight):
  """Print all the edges based on service connections."""
  for e in edges:
    source = e["source"]
    target = e["target"]
    attrs = []
    if bindflags:
      attrs.append(make_attr("label", e["flags"]))
    if highlight:
      flags = e["flags"].split("|")
      if 'IMPORTANT' in flags:
        attrs.append(make_attr("color", RED))
      elif 'WAIVE_PRIORITY' in flags:
        attrs.append(make_attr("color", LIGHT_YELLOW))

    print(f"  {source} -> {target} [" + " ".join(attrs) + "]")

def print_dot(nodes, edges, args):
  """Print dot file of process graph."""
  print("digraph processes {")
  print(f"  layout={args.layout};")
  print("  overlap=false;")
  print("  splines=true;")

  print_dot_nodes(nodes)
  print_dot_edges(edges, bindflags=args.bindflags, highlight=args.highlight)

  print("}")

def print_json(nodes, edges):
  """Print json file of process graph."""
  data = {
    "nodes": nodes,
    "links": edges,
  }
  print(json.dumps(data, indent=2))

def print_text(proto):
  print(proto)

def print_node(proto):
  print(proto.processes.procs)

def print_link(proto):
  print(proto.services)

def parse_args():
  """Parse command-line arguments."""
  parser = argparse.ArgumentParser(
      description="Create dot of AMS process graph")
  parser.add_argument("--layout", help="Layout engine", default="neato")
  parser.add_argument("--bindflags",
                      help="Label bind flags", action="store_true")
  parser.add_argument("--no-highlight", dest="highlight",
                      help="Highlight connections", action="store_false")
  parser.add_argument("--format", help="Format type", default="dot",
                      choices=["dot", "json", "text", "node", "link"])
  parser.add_argument("filename", help="Input file dumpsys activity --proto")
  return parser.parse_args()

def main():
  """Generate a dot graph from an AMS protobuf dump."""
  args = parse_args()
  ams = read_activity_proto(args.filename)

  edges = make_edges(ams.services)
  nodes = make_nodes(ams, edges)

  if args.format == "dot":
    print_dot(nodes, edges, args)
  elif args.format == "json":
    print_json(nodes, edges)
  elif args.format == "text":
    print_text(ams)
  elif args.format == "node":
    print_node(ams)
  elif args.format == "link":
    print_link(ams)
DEBUG_LOGS= False
if __name__ == "__main__":
  main()

