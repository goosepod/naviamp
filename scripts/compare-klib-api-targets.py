#!/usr/bin/env python3
"""Compare declaration inventories per target in BCV KLIB ABI dumps, retaining all differences."""
import argparse
import json
from pathlib import Path
import re


def declarations_by_target(text):
    aliases = {}
    targets = set()
    pending = {}
    scopes = []
    declarations = {}
    in_body = False

    def expand(names):
        expanded = set()
        for name in names:
            expanded.update(aliases.get(name, {name}))
        return expanded

    for line in text.splitlines():
        stripped = line.strip()
        alias = re.fullmatch(r"// Alias: (\w+) => \[(.*)\]", stripped)
        if alias:
            aliases[alias[1]] = set(alias[2].split(", "))
            continue
        selected = re.fullmatch(r"// Targets: \[(.*)\]", stripped)
        indent = len(line) - len(line.lstrip())
        if selected:
            names = set(selected[1].split(", "))
            if not targets:
                targets = names
                declarations = {name: set() for name in targets}
            else:
                resolved = expand(names)
                if not resolved <= targets:
                    raise ValueError(f"Unknown declaration targets: {sorted(resolved - targets)}")
                pending[indent] = resolved
            continue
        if stripped.startswith("// Library unique name:"):
            in_body = True
            continue
        if not in_body or " // " not in line or stripped.startswith("//"):
            continue
        while scopes and scopes[-1][0] >= indent:
            scopes.pop()
        inherited = scopes[-1][1] if scopes else targets
        selected_targets = inherited & pending.pop(indent, targets)
        # Remove annotations left behind by a scope that ended before this declaration.
        pending = {depth: names for depth, names in pending.items() if depth < indent}
        scopes.append((indent, selected_targets))
        declaration = stripped
        for target in selected_targets:
            declarations[target].add(declaration)
    if not targets or not in_body or not any(declarations.values()):
        raise ValueError("Expected a nonempty BCV KLIB ABI dump with target and library headers")
    return declarations


def compare(baseline, candidate, target_pairs):
    result = {}
    for before, after in target_pairs:
        if before not in baseline or after not in candidate:
            raise ValueError(f"Missing requested target: {before} -> {after}")
        result[f"{before}->{after}"] = {
            "baselineDeclarations": len(baseline[before]),
            "candidateDeclarations": len(candidate[after]),
            "removed": sorted(baseline[before] - candidate[after]),
            "added": sorted(candidate[after] - baseline[before]),
        }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--target", action="append", required=True, metavar="BEFORE=AFTER")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    pairs = [pair.split("=", 1) for pair in args.target]
    if any(len(pair) != 2 or not all(pair) for pair in pairs):
        parser.error("Each --target must name BEFORE=AFTER explicitly")
    result = compare(declarations_by_target(args.baseline.read_text()),
                     declarations_by_target(args.candidate.read_text()), pairs)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    for target, difference in result.items():
        print(f"{target}: {len(difference['removed'])} removed, {len(difference['added'])} added")
    # Additions are reported as differences too; callers must review rather than bless them.
    raise SystemExit(int(any(d['removed'] or d['added'] for d in result.values())))


if __name__ == "__main__":
    main()
