"""Adds the options passed as arguments to the almond kernels Jupyter knows about (the kernel specs
whose language is Scala), the way the dev.jupyter* Mill commands of almond pass them to their
kernels. Options already there are left as is.

Usage: python add-kernel-options.py --variable-inspector …
"""

import json
import os
import subprocess
import sys

options = sys.argv[1:]
specs = json.loads(subprocess.check_output(["jupyter", "kernelspec", "list", "--json"]))
updated = 0
for name, info in sorted(specs["kernelspecs"].items()):
    path = os.path.join(info["resource_dir"], "kernel.json")
    with open(path, encoding="utf-8") as f:
        spec = json.load(f)
    if spec.get("language") != "scala":
        continue
    argv = spec["argv"]
    new_options = [opt for opt in options if opt not in argv]
    # before the connection file, that comes last
    idx = argv.index("--connection-file") if "--connection-file" in argv else len(argv)
    argv[idx:idx] = new_options
    with open(path, "w", encoding="utf-8") as f:
        json.dump(spec, f, indent=2)
    print(f"Kernel {name}: added {' '.join(new_options) or 'nothing'}")
    updated += 1

if updated == 0:
    sys.exit("No almond kernel found")
