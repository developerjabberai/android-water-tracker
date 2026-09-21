#!/usr/bin/env python3
"""Turns the flat-fill SVG expressions into compact JSON the widget draws with android.graphics.Path.

Usage: python3 svg_to_json.py  (run from anywhere; paths are relative to this file)
Each output layer is {"d": <svg path data>, "fill": "#RRGGBB" | null, "grad": {...} | null, "alpha": 0..1}, in paint order.
"""
import json, re, sys, pathlib
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "expressions"
OUT = ROOT.parent / "WaterTracker" / "app" / "src" / "main" / "assets" / "art"

NAMED = {"white": "#FFFFFF", "black": "#000000"}


def hex_color(c):
    c = (c or "#000000").strip()
    if c.lower() in NAMED:
        return NAMED[c.lower()]
    if re.fullmatch(r"#[0-9a-fA-F]{3}", c):
        c = "#" + "".join(ch * 2 for ch in c[1:])
    return c.upper()


def convert(svg_path):
    root = ET.parse(svg_path).getroot()
    grads = {}
    for g in root.iter(NS + "linearGradient"):
        stops = []
        for s in g.iter(NS + "stop"):
            op = s.attrib.get("stop-opacity")
            stops.append([float(s.attrib.get("offset", 0)), hex_color(s.attrib.get("stop-color")), float(op) if op else 1.0])
        grads[g.attrib["id"]] = {
            "x1": float(g.attrib["x1"]), "y1": float(g.attrib["y1"]),
            "x2": float(g.attrib["x2"]), "y2": float(g.attrib["y2"]), "stops": stops,
        }
    layers = []
    for p in root.iter(NS + "path"):
        fill = p.attrib.get("fill", "#000000")
        m = re.fullmatch(r"url\(#(.+)\)", fill)
        layers.append({
            "d": p.attrib["d"],
            "fill": None if m else hex_color(fill),
            "grad": grads[m.group(1)] if m else None,
            "alpha": float(p.attrib.get("fill-opacity", 1)),
        })
    size = float(root.attrib["viewBox"].split()[2])
    return {"size": size, "layers": layers}


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for svg in sorted(SRC.glob("expression-*.svg")):
        art = convert(svg)
        target = OUT / (svg.stem + ".json")
        target.write_text(json.dumps(art, separators=(",", ":")))
        print(f"{svg.name}: {len(art['layers'])} layers -> {target.name} ({target.stat().st_size // 1024} KB)")
