"""Tiny UI driver for the emulator smoke test (adb + uiautomator, no test framework).

  python3 scripts/ui.py tap "Begin"        tap the first element whose text starts with "Begin"
  python3 scripts/ui.py has "Begin"        exit 0 if it's on screen
  python3 scripts/ui.py texts              print every visible text
  python3 scripts/ui.py dismiss            answer a system "isn't responding" dialog if one is up
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
        # Remove the last dump first: if this one fails, reading a stale file would describe an old screen.
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
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
    if cmd == "coords":  # one dump, several labels: "x y" per label (raw taps later don't need uiautomator)
        found = {}
        for n in nodes():
            t = n.get("text") or n.get("content-desc") or ""
            for label in sys.argv[2:]:
                if label not in found and (t == label or t.startswith(label)):
                    found[label] = center(n)
        for label in sys.argv[2:]:
            x, y = found.get(label, (-1, -1))
            print(x, y)
        return 0
    if cmd == "last":  # "x y" of the LAST element matching the label (a bottom tab rather than a header)
        hit = None
        for n in nodes():
            t = n.get("text") or n.get("content-desc") or ""
            if t == sys.argv[2] or t.startswith(sys.argv[2]):
                hit = n
        x, y = center(hit) if hit is not None else (-1, -1)
        print(x, y)
        return 0
    if cmd == "dismiss":  # a system "isn't responding" / "keeps stopping" dialog over everything: answer it
        ns = nodes()
        if any("isn't responding" in (n.get("text") or "") or "keeps stopping" in (n.get("text") or "") for n in ns):
            for want in ("Wait", "Close app", "Close"):
                for n in ns:
                    if (n.get("text") or "") == want:
                        x, y = center(n)
                        adb("shell", "input", "tap", str(x), str(y))
                        print(f"   (answered a system dialog: {want})")
                        return 0
        return 1
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
