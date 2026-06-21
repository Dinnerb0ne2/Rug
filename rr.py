#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================
  RavenXD → Rug 一体化重构工具
  版本号 → b1.1
=============================================================

用法:
  python refactor_rug.py <项目根目录>                    # 重构
  python refactor_rug.py <项目根目录> --restore           # 恢复到重构前
  python refactor_rug.py <项目根目录> --clean-backups     # 仅清理备份
  python refactor_rug.py <项目根目录> --dry-run           # 预览不写入

功能:
  1. 品牌名替换: RavenXD/Raven → Rug
  2. 版本号统一: → b1.1
  3. 文件重命名: Raven.java → Rug.java 等
  4. build.gradle: archivesBaseName/version/mixin 名称
     ※ 绝不触碰 minecraft { version = "1.8.9-..." } Forge 版本
  5. mcmod.info / gradle.properties / lang / json 全覆盖
  6. 自动备份 (.bak.rug)，可一键恢复
=============================================================
"""

import os
import re
import sys
import shutil
from pathlib import Path
from datetime import datetime

# ═══════════════════════════════════════════════════════
#  配置区
# ═══════════════════════════════════════════════════════
NEW_NAME    = "Rug"
NEW_VERSION = "b1.1"
BACKUP_EXT  = ".bak.rug"
SKIP_DIRS   = {".git", "build", ".gradle", ".idea", "gradle", "run", "logs",
               "dependencies", "gradle/wrapper"}
TEXT_EXTS   = {".java", ".json", ".info", ".gradle", ".properties",
               ".txt", ".lang", ".cfg", ".toml", ".xml", ".yml", ".yaml", ".md"}

FILE_RENAMES = {
    "Raven.java":    "Rug.java",
    "RavenXD.cfg":   "Rug.cfg",
    "RavenXD.json":  "Rug.json",
    "ravenxd.cfg":   "rug.cfg",
    "ravenxd.json":  "rug.json",
    "mixins.raven.json":       "mixins.rug.json",
    "mixins.raven.refmap.json": "mixins.rug.refmap.json",
}


# ═══════════════════════════════════════════════════════
#  工具函数
# ═══════════════════════════════════════════════════════
def walk_files(root):
    """安全遍历，跳过 build/.git 等目录"""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for f in filenames:
            yield os.path.join(dirpath, f)


def backup_file(path):
    bak = path + BACKUP_EXT
    if not os.path.exists(bak):
        shutil.copy2(path, bak)


def read_file(path):
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as f:
            return f.read()
    except (IOError, UnicodeDecodeError):
        return None


def write_file(path, content):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)


# ═══════════════════════════════════════════════════════
#  1. 恢复模块
# ═══════════════════════════════════════════════════════
def do_restore(root):
    """从 .bak.rug 备份完整恢复到重构前状态"""
    print("=" * 60)
    print("  恢复模式 — 从备份还原项目")
    print("=" * 60)

    # 1. 恢复所有有备份的文件
    restored = 0
    for fp in list(walk_files(root)):
        if not fp.endswith(BACKUP_EXT):
            continue
        orig = fp[:-len(BACKUP_EXT)]
        shutil.copy2(fp, orig)
        os.remove(fp)
        restored += 1
        print(f"  [+] 恢复: {os.path.relpath(orig, root)}")

    # 2. 删除因重命名产生的无备份新文件
    deleted = 0
    for fp in list(walk_files(root)):
        fname = os.path.basename(fp)
        if fname in FILE_RENAMES.values():
            # 找到对应的旧文件名
            old_names = [k for k, v in FILE_RENAMES.items() if v == fname]
            has_backup = any(os.path.exists(os.path.join(os.path.dirname(fp), old + BACKUP_EXT))
                             for old in old_names)
            if not has_backup:
                os.remove(fp)
                deleted += 1
                print(f"  [x] 删除: {os.path.relpath(fp, root)}")

    # 3. 删除报告文件
    report = os.path.join(root, "REFACTOR_REPORT.md")
    if os.path.exists(report):
        os.remove(report)
        print(f"  [x] 删除: REFACTOR_REPORT.md")

    print(f"\n  恢复了 {restored} 个文件，删除了 {deleted} 个新文件")
    print("  项目已回到重构前状态。\n")


# ═══════════════════════════════════════════════════════
#  2. 清理备份模块
# ═══════════════════════════════════════════════════════
def do_clean_backups(root):
    """仅删除所有 .bak.rug 文件"""
    print("=" * 60)
    print("  清理模式 — 删除所有备份文件")
    print("=" * 60)
    n = 0
    for fp in list(walk_files(root)):
        if fp.endswith(BACKUP_EXT):
            os.remove(fp)
            n += 1
    print(f"\n  清理了 {n} 个备份文件\n")


# ═══════════════════════════════════════════════════════
#  3. build.gradle 精准处理（最关键）
# ═══════════════════════════════════════════════════════
def process_build_gradle(path, dry_run=False):
    """
    精准处理 build.gradle:
    - archivesBaseName → "Rug"
    - 顶层 version → "b1.1"
    - mixin 配置文件名 raven → rug
    - ※ minecraft { version = "1.8.9-..." } 绝不触碰
    """
    content = read_file(path)
    if content is None:
        print("    [!] build.gradle 读取失败")
        return

    lines = content.splitlines(keepends=True)
    out_lines = []
    changed = False
    in_minecraft_block = False
    brace_depth = 0
    version_set = False

    for line in lines:
        stripped = line.strip()

        # ── 跟踪 minecraft { } 块的进入与退出 ──
        if not in_minecraft_block:
            if re.match(r'^\s*minecraft\s*\{', stripped):
                in_minecraft_block = True
                brace_depth = stripped.count('{') - stripped.count('}')
                out_lines.append(line)
                continue
        else:
            brace_depth += stripped.count('{') - stripped.count('}')
            if brace_depth <= 0:
                in_minecraft_block = False
            out_lines.append(line)  # minecraft 块内原样保留
            continue

        # ── archivesBaseName = "xxx" → "Rug" ──
        if re.match(r'^\s*archivesBaseName\s*=', stripped):
            new_line = f'archivesBaseName = "{NEW_NAME}"\n'
            if new_line != line:
                changed = True
            out_lines.append(new_line)
            continue

        # ── 顶层 version = "xxx" → "b1.1" ──
        #    匹配: version = "1.0" 或 //version = "Dev" 等
        m = re.match(r'^(\s*)(\/\/?\s*)version\s*=\s*["\'][^"\']*["\']\s*$', line)
        if m:
            new_line = f'{m.group(1)}version = "{NEW_VERSION}"\n'
            if new_line != line:
                changed = True
                version_set = True
            out_lines.append(new_line)
            continue

        # ── mixin 配置文件名引用 ──
        if "mixins.raven" in line:
            new_line = line.replace("mixins.raven", "mixins.rug")
            if new_line != line:
                changed = True
            out_lines.append(new_line)
            continue

        # ── refMap 名称 ──
        if "raven.refmap" in line:
            new_line = line.replace("raven.refmap", "rug.refmap")
            if new_line != line:
                changed = True
            out_lines.append(new_line)
            continue

        out_lines.append(line)

    # 如果顶层没有 version 行，在 archivesBaseName 后插入
    if not version_set:
        final = []
        inserted = False
        for line in out_lines:
            final.append(line)
            if not inserted and re.match(r'^\s*archivesBaseName\s*=', line.strip()):
                final.append(f'version = "{NEW_VERSION}"\n')
                inserted = True
                changed = True
        out_lines = final

    if changed:
        if not dry_run:
            backup_file(path)
            write_file(path, ''.join(out_lines))
        print(f"    [+] build.gradle 已更新")


# ═══════════════════════════════════════════════════════
#  4. mcmod.info 处理
# ═══════════════════════════════════════════════════════
def process_mcmod_info(path, dry_run=False):
    content = read_file(path)
    if content is None:
        return
    original = content

    content = re.sub(r'"modid"\s*:\s*"[^"]*"', '"modid": "rug"', content)
    content = re.sub(r'"name"\s*:\s*"[^"]*"', f'"name": "{NEW_NAME}"', content)
    content = re.sub(r'"version"\s*:\s*"\$\{[^}]*\}"', f'"version": "{NEW_VERSION}"', content)
    content = re.sub(r'"version"\s*:\s*"[^"]*"', f'"version": "{NEW_VERSION}"', content)
    content = re.sub(r'"description"\s*:\s*"[^"]*"',
                     f'"description": "{NEW_NAME} - refactored keystrokes mod"', content)

    if content != original:
        if not dry_run:
            backup_file(path)
            write_file(path, content)
        print(f"    [+] {os.path.relpath(path, root)} 已更新")


# ═══════════════════════════════════════════════════════
#  5. gradle.properties 处理
# ═══════════════════════════════════════════════════════
def process_gradle_properties(path, dry_run=False):
    content = read_file(path)
    if content is None:
        return
    original = content

    content = re.sub(r'(?m)^(modVersion|mod_version)\s*=.*$',
                     f'modVersion = {NEW_VERSION}', content)
    content = re.sub(r'(?m)^(modName|mod_name)\s*=.*$',
                     f'modName = {NEW_NAME}', content)

    if content != original:
        if not dry_run:
            backup_file(path)
            write_file(path, content)
        print(f"    [+] gradle.properties 已更新")


# ═══════════════════════════════════════════════════════
#  6. Java 源码处理
# ═══════════════════════════════════════════════════════
def process_java_file(path, dry_run=False):
    content = read_file(path)
    if content is None:
        return False
    original = content

    # ── 类/枚举/接口声明 ──
    content = re.sub(r'\bclass\s+Raven\b',       'class Rug',       content)
    content = re.sub(r'\benum\s+Raven\b',        'enum Rug',        content)
    content = re.sub(r'\binterface\s+Raven\b',   'interface Rug',   content)

    # ── 品牌名（长串优先）──
    content = content.replace("RavenXD",  "Rug")
    content = content.replace("RAVENXD",  "RUG")
    content = content.replace("raven_xd", "rug")
    content = content.replace("RAVEN_XD", "RUG")

    # ── 静态引用 Raven.xxx → Rug.xxx ──
    content = re.sub(r'\bRaven\.', 'Rug.', content)

    # ── import 语句 ──
    content = re.sub(r'\bimport\s+keystrokesmod\.Raven\s*;', 'import keystrokesmod.Rug;', content)
    content = re.sub(r'\bkeystrokesmod\.Raven\b', 'keystrokesmod.Rug', content)

    # ── 类型引用：方法参数、强转、泛型 ──
    content = re.sub(r'\bRaven\(',  'Rug(',  content)
    content = re.sub(r'<Raven>',    '<Rug>',  content)

    # ── 字符串字面量 ──
    content = content.replace('"RavenXD"', '"Rug"')
    content = content.replace('"Raven"',   '"Rug"')
    content = content.replace("'RavenXD'", "'Rug'")
    content = content.replace("'Raven'",   "'Rug'")

    # ── mixin 配置文件名引用 ──
    content = content.replace("mixins.raven", "mixins.rug")
    content = content.replace("raven.refmap", "rug.refmap")
    content = content.replace("raven.property", "rug.property")

    # ── 配置文件名 ──
    content = content.replace("RavenXD.cfg",  "Rug.cfg")
    content = content.replace("ravenxd.cfg",  "rug.cfg")

    # ── 兜底：剩余独立 Raven 标识符 ──
    # 用 \b 确保不误伤 ravenB / graven 等子串
    content = re.sub(r'\bRaven\b', 'Rug', content)

    if content != original:
        if not dry_run:
            backup_file(path)
            write_file(path, content)
        return True
    return False


# ═══════════════════════════════════════════════════════
#  7. 资源文件处理 (lang/json/cfg/yml/txt)
# ═══════════════════════════════════════════════════════
def process_resource_file(path, dry_run=False):
    ext = os.path.splitext(path)[1].lower()
    if ext not in TEXT_EXTS:
        return False

    # mcmod.info 由专门函数处理
    if os.path.basename(path) in ("mcmod.info",) or path.endswith(".info"):
        return False
    # build.gradle / gradle.properties 由专门函数处理
    if os.path.basename(path) == "build.gradle" or os.path.basename(path) == "gradle.properties":
        return False

    content = read_file(path)
    if content is None:
        return False
    original = content

    content = content.replace("RavenXD",  "Rug")
    content = content.replace("RAVENXD",  "RUG")
    content = content.replace("ravenxd",  "rug")
    content = content.replace("raven_xd", "rug")
    content = content.replace("RAVEN_XD", "RUG")

    # 品牌名替换（保留 raven.git 等不会被 \bRaven\b 匹配）
    content = re.sub(r'\bRaven\b', 'Rug', content)

    # mixin 文件名
    content = content.replace("mixins.raven", "mixins.rug")
    content = content.replace("raven.refmap", "rug.refmap")

    if content != original:
        if not dry_run:
            backup_file(path)
            write_file(path, content)
        return True
    return False


# ═══════════════════════════════════════════════════════
#  8. 文件重命名
# ═══════════════════════════════════════════════════════
def do_rename_files(root, dry_run=False):
    print("\n[4] 重命名文件...")
    renamed = 0
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for old_name, new_name in FILE_RENAMES.items():
            if old_name in filenames:
                old_path = os.path.join(dirpath, old_name)
                new_path = os.path.join(dirpath, new_name)
                if os.path.exists(old_path) and not os.path.exists(new_path):
                    if not dry_run:
                        # 先备份旧文件名
                        backup_file(old_path)
                        shutil.move(old_path, new_path)
                    print(f"    [+] {old_name} → {new_name}")
                    renamed += 1
    if renamed == 0:
        print("    [=] 无文件需要重命名")
    return renamed


# ═══════════════════════════════════════════════════════
#  9. 遗漏扫描
# ═══════════════════════════════════════════════════════
def scan_missed_references(root):
    """扫描所有文本文件中残留的 Raven 引用"""
    print("\n[6] 扫描遗漏的 Raven 引用...")
    missed = []
    for fp in walk_files(root):
        if fp.endswith(BACKUP_EXT):
            continue
        ext = os.path.splitext(fp)[1].lower()
        if ext not in TEXT_EXTS:
            continue
        content = read_file(fp)
        if content is None:
            continue
        for i, line in enumerate(content.split('\n'), 1):
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('#'):
                continue
            # 排除注释中的引用
            if 'Raven' in line and 'Rug' not in line:
                rel = os.path.relpath(fp, root)
                missed.append((rel, i, line.strip()[:100]))

    if missed:
        print(f"  [!] 发现 {len(missed)} 处可能遗漏:")
        for rel, line_no, line in missed[:50]:
            print(f"    {rel}:{line_no} → {line}")
        if len(missed) > 50:
            print(f"    ... 还有 {len(missed) - 50} 处")
    else:
        print("  [=] 无遗漏引用")
    return missed


# ═══════════════════════════════════════════════════════
#  10. 生成报告
# ═══════════════════════════════════════════════════════
def generate_report(root, changed_files, renamed_count):
    report_path = os.path.join(root, "REFACTOR_REPORT.md")
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(f"# {NEW_NAME} 重构报告\n\n")
        f.write(f"- 时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"- 目标名称: {NEW_NAME}\n")
        f.write(f"- 目标版本: {NEW_VERSION}\n\n")
        f.write(f"## 修改的文件 ({len(changed_files)})\n\n")
        for cf in sorted(changed_files):
            f.write(f"- `{cf}`\n")
        f.write(f"\n## 重命名的文件 ({renamed_count})\n\n")
        f.write(f"- 见上方输出\n")
        f.write(f"\n## 替换规则\n\n")
        f.write(f"- RavenXD → {NEW_NAME}\n")
        f.write(f"- Raven (类名/引用) → {NEW_NAME}\n")
        f.write(f"- 版本号 → {NEW_VERSION}\n")
        f.write(f"- modid → rug\n")
        f.write(f"- minecraft {{ version }} 块: **未触碰**\n")
        f.write(f"- 备份扩展名: {BACKUP_EXT}\n")
    print(f"\n  [+] 报告已生成: {report_path}")


# ═══════════════════════════════════════════════════════
#  主入口
# ═══════════════════════════════════════════════════════
def main():
    global root

    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    root = os.path.abspath(sys.argv[1])
    dry_run  = "--dry-run" in sys.argv
    restore  = "--restore" in sys.argv
    clean    = "--clean-backups" in sys.argv

    if not os.path.isdir(root):
        print(f"错误: {root} 不是有效目录")
        sys.exit(1)

    # ── 恢复模式 ──
    if restore:
        do_restore(root)
        return

    # ── 清理模式 ──
    if clean:
        do_clean_backups(root)
        return

    # ── 重构模式 ──
    print("=" * 60)
    print(f"  {NEW_NAME} 重构工具（一体化版）")
    print(f"  目标: {root}")
    print(f"  名称: {NEW_NAME}    版本: {NEW_VERSION}")
    print(f"  Dry-run: {dry_run}")
    print("=" * 60)

    # Step 1: build.gradle（最关键，绝不碰 minecraft 块）
    print("\n[1] 处理 build.gradle...")
    gradle_path = os.path.join(root, "build.gradle")
    if os.path.exists(gradle_path):
        process_build_gradle(gradle_path, dry_run)
    else:
        print("    [!] build.gradle 未找到")

    # Step 2: gradle.properties + mcmod.info
    print("\n[2] 处理构建/资源配置...")
    process_gradle_properties(os.path.join(root, "gradle.properties"), dry_run)
    for fp in walk_files(root):
        fname = os.path.basename(fp)
        if fname == "mcmod.info" or fp.endswith(".info"):
            process_mcmod_info(fp, dry_run)

    # Step 3: Java 源码 + 资源文件
    print("\n[3] 处理源码与资源文件...")
    changed_files = []
    for fp in walk_files(root):
        if fp.endswith(BACKUP_EXT):
            continue
        fname = os.path.basename(fp)
        # 跳过已由专门函数处理的文件
        if fname == "build.gradle" or fname == "gradle.properties":
            continue
        if fname == "mcmod.info" or fp.endswith(".info"):
            continue

        ext = os.path.splitext(fp)[1].lower()
        if ext == ".java":
            if process_java_file(fp, dry_run):
                rel = os.path.relpath(fp, root)
                changed_files.append(rel)
                print(f"    [+] {rel}")
        elif ext in TEXT_EXTS:
            if process_resource_file(fp, dry_run):
                rel = os.path.relpath(fp, root)
                changed_files.append(rel)
                print(f"    [+] {rel}")

    # Step 4: 文件重命名
    renamed_count = do_rename_files(root, dry_run)

    # Step 5: 处理重命名后的文件内容（Rug.java 内部类名）
    print("\n[5] 处理重命名后的主类文件...")
    for fp in walk_files(root):
        if os.path.basename(fp) == "Rug.java":
            if process_java_file(fp, dry_run):
                print(f"    [+] {os.path.relpath(fp, root)} (二次扫描)")
            break

    # Step 6: 遗漏扫描
    scan_missed_references(root)

    # Step 7: 报告
    print("\n[7] 生成报告...")
    if not dry_run:
        generate_report(root, changed_files, renamed_count)

    print("\n" + "=" * 60)
    print(f"  重构完成!")
    print(f"  修改文件: {len(changed_files)}")
    print(f"  重命名文件: {renamed_count}")
    if dry_run:
        print("  (Dry-run 模式，未实际写入)")
    else:
        print(f"  备份扩展名: {BACKUP_EXT}")
    print("=" * 60)
    print(f"\n  下一步:")
    print(f"    ./gradlew build              # 验证编译")
    print(f"    python {sys.argv[0]} {root} --restore   # 不满意？一键恢复")
    print(f"    python {sys.argv[0]} {root} --clean-backups  # 确认后清理备份\n")


if __name__ == "__main__":
    main()
