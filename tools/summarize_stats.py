#!/usr/bin/env python3
"""Read-only balance export. No player identities, external libraries or network requests."""
import argparse
import collections
import csv
import json
from pathlib import Path


def summarize(path, minimum_rounds=30):
    selections, outcomes = {}, {}
    metrics = collections.defaultdict(collections.Counter)
    with Path(path).open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        required = {"round", "mode", "map", "profile", "actor", "class", "faction", "event", "value", "detail"}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError("Missing telemetry columns")
        for row in reader:
            key = (row["round"], row["mode"], row["map"], row["profile"])
            if row["event"] == "match_result":
                outcomes[key] = row["detail"]
            if row["event"] == "class_selected":
                selections[(key, row["actor"])] = (row["class"], row["faction"])
            if row["class"]:
                try:
                    value = float(row["value"])
                except ValueError:
                    continue
                metrics[(key, row["actor"])][row["event"]] += value
                metrics[(key, row["actor"])]["count:" + row["event"]] += 1
    groups = {}
    for (key, actor), (job, faction) in selections.items():
        outcome = outcomes.get(key)
        if outcome not in {"ESCAPED", "WCKD_WIN", "TIMEOUT", "TEAM_LOST"}:
            continue  # Exclude unfinished/admin-stopped/faulted rounds from win rates.
        group_key = (key[1], key[2], key[3], job, faction)
        group = groups.setdefault(group_key, {"rounds": set(), "appearances": 0, "wins": 0, "metrics": collections.Counter()})
        group["rounds"].add(key[0])
        group["appearances"] += 1
        group["metrics"].update(metrics[(key, actor)])
        group["wins"] += int((faction == "WCKD" and outcome == "WCKD_WIN") or (faction == "GLADERS" and outcome == "ESCAPED"))
    result = []
    for key, group in sorted(groups.items()):
        mode, arena, profile, job, faction = key
        m = group["metrics"]
        result.append(dict(mode=mode, map=arena, profile=profile, job=job, faction=faction,
                           distinct_rounds=len(group["rounds"]), appearances=group["appearances"], wins=group["wins"],
                           win_rate=group["wins"] / group["appearances"],
                           skill_success_rate=m["skill_success"] / m["skill_attempt"] if m["skill_attempt"] else None,
                           healing=m["healing"], objectives=m["count:objective_completed"], objective_work=m["objective_completed"],
                           interpretation="insufficient sample" if len(group["rounds"]) < minimum_rounds else "descriptive only; no automatic nerf"))
    return {"groups": result, "limitations": ["Actors are round-local, not unique players.", "No player skill adjustment or causal attribution.", "Team composition and party effects require review of the round events.", "Win rate alone is not class strength."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("events", type=Path)
    parser.add_argument("--minimum-rounds", type=int, default=30)
    args = parser.parse_args()
    if args.minimum_rounds < 1:
        parser.error("minimum-rounds must be positive")
    print(json.dumps(summarize(args.events, args.minimum_rounds), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
