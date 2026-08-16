#!/usr/bin/env python3
"""Generate, audit, render and compare source-faithful exam-paper fixtures."""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "AppBackend"
REFERENCE_SHA256 = "449510b906bc119345388d379cb339ea8139981f0b2ee7f4865abb6fe7f8ad72"
MUTABLE = {"word/document.xml", "word/header1.xml", "word/header2.xml", "word/settings.xml"}
REQUIRED = {"word/document.xml", "word/header1.xml", "word/header2.xml", "word/footer1.xml",
            "word/footer2.xml", "word/styles.xml", "word/numbering.xml", "word/settings.xml",
            "word/_rels/document.xml.rels"}
TOKENS = {
    "word/document.xml": ["%TITLE%", "%SUBTITLE%", "%TIME%", "%NAME%", "%SCORE%", "%PRECAUTIONS%",
                          "%QUESTION%", "%ANSWER%", "%HEADER%", "%PageSetting%", "%IMAGE%"],
    "word/header1.xml": ["%h1LineHeight%", "%h1LineTop%", "%h1LineWidth%", "%h1MarginLeftIn%",
                         "%h1MarginLeftInside%", "%h1MarginLeftOutside%", "%h1wordAbout1%", "%h1wordAbout2%",
                         "%h1wordAbout3%", "%h1wordAbout4%", "%h1wordAbout5%", "%h1wordUpAndDown1%",
                         "%h1wordUpAndDown2%", "%h1wordUpAndDown3%", "%h1wordUpAndDown4%", "%h1wordUpAndDown5%",
                         "%information%"],
    "word/header2.xml": ["%h2LineHeight%", "%h2LineTop%", "%h2LineWidth%", "%h2wordAbout1%", "%h2wordAbout2%",
                         "%h2wordAbout3%", "%h2wordAbout4%", "%h2wordAbout5%", "%h2wordUpAndDown1%",
                         "%h2wordUpAndDown2%", "%h2wordUpAndDown3%", "%h2wordUpAndDown4%", "%h2wordUpAndDown5%",
                         "%information%"]}
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
PR = "{http://schemas.openxmlformats.org/package/2006/relationships}"
REL = {
    "rId8": ("http://schemas.openxmlformats.org/officeDocument/2006/relationships/header", "header1.xml", "headerReference"),
    "rId9": ("http://schemas.openxmlformats.org/officeDocument/2006/relationships/header", "header2.xml", "headerReference"),
    "rId10": ("http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer", "footer1.xml", "footerReference"),
    "rId11": ("http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer", "footer2.xml", "footerReference")}

def discover(explicit: str | None, name: str, patterns: list[str]) -> Path:
    if explicit:
        candidate = Path(explicit).expanduser()
        if candidate.exists(): return candidate.resolve()
        found = shutil.which(explicit)
        if found: return Path(found).resolve()
        raise RuntimeError(f"configured {name} not found: {explicit}")
    found = shutil.which(name)
    if found: return Path(found).resolve()
    for pattern in patterns:
        hits = sorted(Path("/").glob(pattern.lstrip("/")))
        if hits: return hits[-1].resolve()
    raise RuntimeError(f"unable to discover {name}; pass --{name}")

def run(command: list[str], cwd: Path | None = None, env=None):
    print("+", " ".join(command), flush=True); subprocess.run(command, cwd=cwd, env=env, check=True)

def digest_bytes(value: bytes) -> str: return hashlib.sha256(value).hexdigest()
def digest_file(path: Path) -> str: return digest_bytes(path.read_bytes())

def attr(element, name): return element.attrib.get(W + name)

def structural_audit(path: Path, source_template: Path, expected: dict) -> dict:
    out = {"file": path.name, "sha256": digest_file(path), "errors": [], "expected": expected}
    with zipfile.ZipFile(source_template) as source, zipfile.ZipFile(path) as final:
        sn, fn = set(source.namelist()), set(final.namelist())
        missing = sorted(REQUIRED - fn)
        if missing: out["errors"].append(f"missing entries: {missing}")
        parsed = {}
        for name in REQUIRED & fn:
            if name.endswith((".xml", ".rels")):
                try: parsed[name] = ET.fromstring(final.read(name))
                except ET.ParseError as exc: out["errors"].append(f"malformed {name}: {exc}")
        unresolved = {part: [token for token in tokens if token.encode() in final.read(part)]
                      for part, tokens in TOKENS.items() if part in fn}
        unresolved = {key: value for key, value in unresolved.items() if value}
        if unresolved: out["errors"].append(f"unresolved named tokens: {unresolved}")
        drift = [name for name in sorted(sn - MUTABLE) if name not in fn or digest_bytes(source.read(name)) != digest_bytes(final.read(name))]
        if drift: out["errors"].append(f"preserve-only drift: {drift}")
        doc, rels = parsed.get("word/document.xml"), parsed.get("word/_rels/document.xml.rels")
        settings = parsed.get("word/settings.xml")
        definitions = {item.attrib.get("Id"): item for item in list(rels)} if rels is not None else {}
        for rid, (typ, target, element_name) in REL.items():
            definition = definitions.get(rid)
            if definition is None or definition.attrib.get("Type") != typ or definition.attrib.get("Target") != target:
                out["errors"].append(f"relationship contract mismatch: {rid}")
            refs = [] if doc is None else [node for node in doc.iter(W + element_name) if node.attrib.get(R + "id") == rid]
            expected_count = 1 if expected["binding"] else 0
            if len(refs) != expected_count: out["errors"].append(f"document reference {rid}: expected {expected_count}, got {len(refs)}")
        even_odd_count = 0 if settings is None else sum(1 for _ in settings.iter(W + "evenAndOddHeaders"))
        if even_odd_count != 1:
            out["errors"].append(f"evenAndOddHeaders: expected 1, got {even_odd_count}")
        if doc is not None:
            section = next(iter(doc.iter(W + "sectPr")), None)
            size = section.find(W + "pgSz"); margins = section.find(W + "pgMar"); cols = section.find(W + "cols")
            grid = section.find(W + "docGrid")
            actual = {"width": int(attr(size,"w")), "height": int(attr(size,"h")), "orientation": attr(size,"orient").upper(),
                      "top": int(attr(margins,"top")), "right": int(attr(margins,"right")),
                      "bottom": int(attr(margins,"bottom")), "left": int(attr(margins,"left")),
                      "columns": int(attr(cols,"num")) if cols is not None else 1,
                      "space": int(attr(cols,"space")) if cols is not None else expected["space"],
                      "docGrid": int(attr(grid,"linePitch")) if grid is not None else None}
            for key, value in actual.items():
                if value != expected[key]: out["errors"].append(f"{key}: expected {expected[key]}, got {value}")
            text = "".join(node.text or "" for node in doc.iter(W + "t"))
            checks = {"answer": "答案解析" in text, "scoreTables": text.count("题号"), "graderTables": text.count("阅卷人")}
            for key, value in checks.items():
                if value != expected[key]: out["errors"].append(f"{key}: expected {expected[key]}, got {value}")
    return out

def render(docx: Path, output: Path, python: Path, renderer: Path, env) -> dict:
    destination = output / docx.stem; destination.mkdir(parents=True, exist_ok=True)
    run([str(python), str(renderer), str(docx), "--output_dir", str(destination), "--emit_pdf"], env=env)
    pages = sorted(destination.glob("page-*.png")); pdf = destination / f"{docx.stem}.pdf"
    if not pages or not pdf.exists() or pdf.read_bytes()[:4] != b"%PDF": raise RuntimeError(f"render failed: {docx}")
    return {"pages": len(pages), "pngs": [str(p) for p in pages], "pdf": str(pdf)}

def rasterize(pdf: Path, directory: Path, pdftoppm: Path) -> list[Path]:
    directory.mkdir(parents=True, exist_ok=True); prefix = directory / "page"
    run([str(pdftoppm), "-png", "-r", "144", str(pdf), str(prefix)])
    return sorted(directory.glob("page-*.png"))

def equivalence(preview: Path, final: Path, qa: Path, pdftoppm: Path) -> dict:
    a, b = rasterize(preview, qa / "preview", pdftoppm), rasterize(final, qa / "final", pdftoppm)
    same = len(a) == len(b); pages=[]
    for index, (left, right) in enumerate(zip(a,b), 1):
        equal = digest_file(left) == digest_file(right); same &= equal
        pages.append({"page": index, "preview_sha256": digest_file(left), "final_sha256": digest_file(right), "pixel_diff_ratio": 0.0 if equal else 1.0, "pass": equal})
    return {"preview_pages": len(a), "final_pages": len(b), "pages": pages, "pass": same}

def manual_inspection(reports: list[dict], manual_path: Path | None) -> tuple[list[dict], list[str]]:
    if manual_path is None: return [], ["--manual-verdict is required for a PASS verdict"]
    rules = json.loads(manual_path.read_text(encoding="utf-8")); default = rules.get("default")
    items=[]; errors=[]
    allowed={"PASS", "KNOWN_FONT_LIMITATION"}
    for report in reports:
        for page in range(1, report.get("pages", 0)+1):
            key=f"{report['file']}#page-{page}"; value=rules.get("pages",{}).get(key, default)
            status=value.get("status") if isinstance(value,dict) else value
            checks=(value.get("checks") if isinstance(value,dict) else None) or rules.get("checks",[])
            item={"file":report["file"],"page":page,"status":status,"checks":checks}; items.append(item)
            if status not in allowed or not checks: errors.append(f"manual verdict missing/invalid: {key}")
    return items, errors

def main() -> int:
    p=argparse.ArgumentParser()
    p.add_argument("--reference", default=os.getenv("EXAM_REFERENCE", "/Users/zzs/Desktop/zzs/github/wordpapergenerate/动态试卷.docx"))
    p.add_argument("--mvn", default=os.getenv("MVN")); p.add_argument("--python", default=os.getenv("EXAM_PYTHON"))
    p.add_argument("--renderer", default=os.getenv("EXAM_DOCX_RENDERER")); p.add_argument("--soffice", default=os.getenv("SOFFICE"))
    p.add_argument("--output-dir", type=Path); p.add_argument("--manual-verdict", type=Path)
    args=p.parse_args()
    mvn=discover(args.mvn,"mvn",["/opt/homebrew/bin/mvn"]); python=discover(args.python,"python",[str(Path(sys.executable))])
    renderer=discover(args.renderer,"renderer",["/Users/*/.codex/plugins/cache/openai-primary-runtime/documents/*/skills/documents/render_docx.py"])
    soffice=discover(args.soffice,"soffice",["/Applications/LibreOffice.app/Contents/MacOS/soffice", "/Users/*/.cache/codex-runtimes/*/dependencies/bin/override/soffice"])
    pdftoppm=discover(None,"pdftoppm",["/Users/*/.cache/codex-runtimes/*/dependencies/bin/override/pdftoppm"])
    reference=Path(args.reference).expanduser().resolve()
    if not reference.exists() or digest_file(reference)!=REFERENCE_SHA256: raise RuntimeError("reference missing or SHA-256 changed")
    qa=(args.output_dir or Path(tempfile.mkdtemp(prefix="exam-paper-visual-qa-"))).resolve()
    if qa.exists(): shutil.rmtree(qa)
    fixtures=qa/"fixtures"; renders=qa/"renders"; fixtures.mkdir(parents=True); renders.mkdir()
    preview_root=Path(tempfile.mkdtemp(prefix="exam-preview-root-"))
    try:
        run([str(mvn),"-q","-Dtest=SourcePaperVisualFixtureTest",f"-Dexam.visual.output={fixtures}",
             f"-Dexam.visual.soffice={soffice}",f"-Dexam.visual.previewRoot={preview_root}","test"],cwd=BACKEND)
    finally: shutil.rmtree(preview_root,ignore_errors=True)
    manifest={item["file"]:item for item in json.loads((fixtures/"manifest.json").read_text())}
    source_template=BACKEND/"src/main/resources/exam-paper-template/static/document.docx"
    env=os.environ.copy(); env["TMPDIR"]=tempfile.mkdtemp(prefix="exam-render-tmp-")
    reports=[]
    reference_report={"file":reference.name,"sha256":digest_file(reference),"errors":[]}; reference_report.update(render(reference,renders,python,renderer,env)); reports.append(reference_report)
    for docx in sorted(fixtures.glob("*.docx")):
        report=structural_audit(docx,source_template,manifest[docx.name]) if docx.name.startswith("template-") else {"file":docx.name,"sha256":digest_file(docx),"errors":[],"mode":"SIMPLE"}
        report.update(render(docx,renders,python,renderer,env)); reports.append(report)
    final_pdf=Path(next(r["pdf"] for r in reports if r["file"]=="template-a3-landscape-binding-2col-paper.docx"))
    equiv=equivalence(fixtures/"template-a3-landscape-binding-2col-paper.preview.pdf",final_pdf,qa/"equivalence",pdftoppm)
    manual,manual_errors=manual_inspection(reports,args.manual_verdict)
    errors=[f"{r['file']}: {e}" for r in reports for e in r["errors"]]+manual_errors
    if not equiv["pass"]: errors.append("preview/final raster equivalence failed")
    payload={"qa_directory":str(qa),"tools":{"mvn":str(mvn),"python":str(python),"renderer":str(renderer),"soffice":str(soffice),"pdftoppm":str(pdftoppm)},
             "reference":str(reference),"reference_sha256":REFERENCE_SHA256,"fixtures":reports,"preview_final_equivalence":equiv,
             "manual_inspection":manual,"errors":errors,"verdict":"PASS" if not errors else "FAIL"}
    (qa/"verdict.json").write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8")
    (qa/"verdict.md").write_text(f"# Exam Paper Visual QA\n\n- Verdict: **{payload['verdict']}**\n- Fixtures: {len(reports)}\n- Manual pages: {len(manual)}\n- Preview/final equivalent: {equiv['pass']}\n- Errors: {len(errors)}\n",encoding="utf-8")
    print(json.dumps(payload,ensure_ascii=False,indent=2)); return 1 if errors else 0
if __name__=="__main__": sys.exit(main())
