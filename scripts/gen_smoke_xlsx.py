#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成 XLSX 拆解冒烟样例（含中文表头）。

环境无 openpyxl/xlsxwriter 时使用：手写最小 OOXML 包，
表头行用 inlineStr 中文文本、预计天数列用数字单元格，
同时覆盖 POI DataFormatter 的文本与数字格式化路径。

用法: python scripts/gen_smoke_xlsx.py [输出路径]
"""
import sys
import zipfile

HEADERS = ["任务名称", "阶段", "预计天数", "优先级", "说明"]
ROWS = [
    ["学习Python基础语法", "基础阶段", 10, "高", "变量循环函数面向对象"],
    ["练习编写爬虫脚本", "进阶阶段", 5, "中", "requests入门"],
    ["解析动态网页", "进阶阶段", 6, "高", "Selenium与Ajax分析"],
    ["反爬应对与架构总结", "冲刺阶段", 9, "低", "代理池与工程化"],
]

COLS = "ABCDE"


def cell(ref: str, value) -> str:
    if isinstance(value, (int, float)):
        return f'<c r="{ref}" t="n"><v>{value}</v></c>'
    text = str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return f'<c r="{ref}" t="inlineStr"><is><t>{text}</t></is></c>'


def sheet_xml() -> str:
    rows = ['<row r="1">' + "".join(cell(f"{c}1", h) for c, h in zip(COLS, HEADERS)) + "</row>"]
    for index, row in enumerate(ROWS, start=2):
        rows.append(f'<row r="{index}">' + "".join(cell(f"{c}{index}", v) for c, v in zip(COLS, row)) + "</row>")
    body = "".join(rows)
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        f"<sheetData>{body}</sheetData></worksheet>"
    )


def main(out_path: str) -> None:
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
        '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        "</Types>"
    )
    root_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
        "</Relationships>"
    )
    workbook = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        '<sheets><sheet name="学习计划" sheetId="1" r:id="rId1"/></sheets></workbook>'
    )
    workbook_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
        "</Relationships>"
    )
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zipped:
        zipped.writestr("[Content_Types].xml", content_types)
        zipped.writestr("_rels/.rels", root_rels)
        zipped.writestr("xl/workbook.xml", workbook)
        zipped.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        zipped.writestr("xl/worksheets/sheet1.xml", sheet_xml())
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "scripts/smoke_plan_table.xlsx")
