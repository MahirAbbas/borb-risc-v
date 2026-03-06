#!/usr/bin/env python3
import argparse
import json
import os
import sys
from pathlib import Path


def load_project(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    return data["project"]


def emit_lines(values):
    for value in values:
        print(value)


def find_sources(project: dict):
    exts = {".scala", ".java"}
    seen = set()
    for root in project.get("sources", []):
        root_path = Path(root)
        if not root_path.exists():
            continue
        if root_path.is_file():
            if root_path.suffix in exts:
                seen.add(str(root_path))
            continue
        for path in root_path.rglob("*"):
            if path.is_file() and path.suffix in exts:
                seen.add(str(path))
    return sorted(seen)


def newest_mtime(paths):
    latest = 0.0
    for path in paths:
        try:
            latest = max(latest, os.path.getmtime(path))
        except FileNotFoundError:
            pass
    return latest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default=".bloop/projectname.json")
    parser.add_argument(
        "field",
        choices=[
            "classes-dir",
            "compile-classpath",
            "runtime-classpath",
            "scala-jars",
            "scala-options",
            "java-home",
            "sources",
            "needs-compile",
        ],
    )
    args = parser.parse_args()

    config = Path(args.config)
    project = load_project(config)

    if args.field == "classes-dir":
        print(project["classesDir"])
        return
    if args.field == "compile-classpath":
        emit_lines(project.get("classpath", []))
        return
    if args.field == "runtime-classpath":
        cp = project.get("platform", {}).get("classpath") or [project["classesDir"], *project.get("classpath", [])]
        emit_lines(cp)
        return
    if args.field == "scala-jars":
        emit_lines(project.get("scala", {}).get("jars", []))
        return
    if args.field == "scala-options":
        emit_lines(project.get("scala", {}).get("options", []))
        return
    if args.field == "java-home":
        print(project.get("platform", {}).get("config", {}).get("home", ""))
        return
    if args.field == "sources":
        emit_lines(find_sources(project))
        return
    if args.field == "needs-compile":
        classes_dir = Path(project["classesDir"])
        stamp = classes_dir / ".compile-stamp"
        if not classes_dir.exists() or not any(classes_dir.rglob("*.class")):
            print("yes")
            return
        sources = find_sources(project)
        newest_source = newest_mtime(sources + [str(config)])
        newest_output = newest_mtime([str(p) for p in classes_dir.rglob("*.class")] + [str(stamp)])
        print("yes" if newest_source > newest_output else "no")
        return


if __name__ == "__main__":
    sys.exit(main())
