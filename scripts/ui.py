"""Tiny UI driver for the emulator smoke test (adb + uiautomator, no test framework).

  python3 scripts/ui.py tap "Begin"        tap the first element whose text starts with "Begin"
  python3 scripts/ui.py has "Begin"        exit 0 if it's on screen
  python3 scripts/ui.py texts              print every visible text
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=60)


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml").stdout
        if "<hierarchy" in raw:
            try:
                return list(ET.fromstring(raw[raw.index("<hierarchy"):]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(label):
    for n in nodes():
        t = n.get("text") or n.get("content-desc") or ""
        if t == label or t.startswith(label):
            return n
    return None


def main():
    cmd = sys.argv[1]
    if cmd == "texts":
        for n in nodes():
            t = n.get("text") or n.get("content-desc")
            if t:
                print(t.replace("\n", " "))
        return 0
    n = find(sys.argv[2])
    if n is None:
        print(f"not found: {sys.argv[2]}")
        return 1
    if cmd == "tap":
        x, y = center(n)
        adb("shell", "input", "tap", str(x), str(y))
    return 0


sys.exit(main())
