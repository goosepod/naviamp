#!/usr/bin/env python3
"""Summarize xctrace's exported metal-gpu-intervals XML without disclosing trace environment data.

Export with: xcrun xctrace export --input recording.trace --xpath
  '/trace-toc/run[@number="1"]/data/table[@schema="metal-gpu-intervals"]' --output intervals.xml
Channel durations can overlap; they are not total GPU utilization or energy.
"""
import argparse
from collections import defaultdict
import json
import math
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('xml')
parser.add_argument('--process', action='append', default=[], help='Process name substring; repeatable')
args = parser.parse_args()
root = ET.parse(args.xml).getroot()
ids = {element.get('id'): element for element in root.iter() if element.get('id')}

def resolve(element):
    while element.get('ref'):
        element = ids[element.get('ref')]
    return element

totals = defaultdict(list)
for node in root.findall('node'):
    schema = node.find('schema')
    if schema is None or schema.get('name') != 'metal-gpu-intervals':
        continue
    names = [column.findtext('mnemonic') for column in schema.findall('col')]
    for row in node.findall('row'):
        fields = dict(zip(names, (resolve(element) for element in row)))
        if fields['event-depth'].text != '0' or fields['state'].text != 'Active':
            continue
        process = fields['process'].get('fmt', '')
        if args.process and not any(name in process for name in args.process):
            continue
        key = (process, fields['channel-name'].text)
        totals[key].append((int(fields['start'].text), int(fields['duration'].text)))

def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)] / 1_000_000

print(json.dumps([
    dict(process=process, channel=channel, intervals=len(events),
         channel_ms=sum(duration for _, duration in events) / 1_000_000,
         span_seconds=(max(start + duration for start, duration in events) - min(start for start, _ in events)) / 1e9,
         interval_mean_ms=sum(duration for _, duration in events) / len(events) / 1_000_000,
         interval_p50_ms=percentile([duration for _, duration in events], .5),
         interval_p95_ms=percentile([duration for _, duration in events], .95),
         interval_p99_ms=percentile([duration for _, duration in events], .99),
         interval_max_ms=max(duration for _, duration in events) / 1_000_000)
    for (process, channel), events in sorted(totals.items())
], indent=2))
