#!/usr/bin/env python3
"""将 plugins/ 下的插件目录打包为 .mfp（MoFox Plugin）格式。

.mfp 本质是 ZIP，遵循 MoFox 官方打包规则：
- 包含：plugin.py、manifest.json、LICENSE、README.md、*.py、子目录
- 排除：__pycache__、*.pyc、.git、.venv、dist、build、docs（除非 --with-docs）
- 文件名：{name}-{version}.mfp（name/version 取自 manifest.json）

用法：
    python scripts/build_mfp.py                    # 打包 plugins/ 下所有插件
    python scripts/build_mfp.py plugins/phone_tool # 打包指定插件
    python scripts/build_mfp.py --output dist      # 指定输出目录
    python scripts/build_mfp.py --with-docs        # 包含文档
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

# 默认排除的文件/目录（对齐 mpdt plugin build 规则）
EXCLUDE_DIRS = {
    "__pycache__",
    ".git",
    ".venv",
    "venv",
    "node_modules",
    "dist",
    "build",
    ".eggs",
    "*.egg-info",
    ".pytest_cache",
    ".mypy_cache",
    ".ruff_cache",
    ".ipynb_checkpoints",
}

EXCLUDE_SUFFIXES = {".pyc", ".pyo", ".pyd", ".so", ".egg-info"}

EXCLUDE_FILES = {".DS_Store", "Thumbs.db", "ehthumbs.db"}


def should_exclude(path: Path, with_docs: bool) -> bool:
    """判断路径是否应被排除。"""
    parts = path.parts
    for part in parts:
        lower = part.lower()
        if lower in EXCLUDE_DIRS:
            return True
        if lower.endswith(".egg-info"):
            return True
    if path.name in EXCLUDE_FILES:
        return True
    if path.suffix in EXCLUDE_SUFFIXES:
        return True
    # docs 目录默认排除
    if not with_docs and "docs" in parts:
        return True
    return False


def read_manifest(plugin_dir: Path) -> tuple[str, str]:
    """读取 manifest.json，返回 (name, version)。"""
    manifest_path = plugin_dir / "manifest.json"
    if not manifest_path.is_file():
        raise FileNotFoundError(f"未找到 manifest.json: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    name = manifest.get("name", plugin_dir.name)
    version = manifest.get("version", "0.0.0")
    return name, version


def build_plugin(plugin_dir: Path, output_dir: Path, with_docs: bool) -> Path:
    """打包单个插件为 .mfp，返回输出文件路径。"""
    plugin_dir = plugin_dir.resolve()
    if not plugin_dir.is_dir():
        raise NotADirectoryError(f"插件目录不存在: {plugin_dir}")

    name, version = read_manifest(plugin_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    output_file = output_dir / f"{name}-{version}.mfp"

    file_count = 0
    with zipfile.ZipFile(output_file, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(plugin_dir.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(plugin_dir)
            if should_exclude(rel, with_docs):
                continue
            arcname = str(rel).replace("\\", "/")
            zf.write(path, arcname)
            file_count += 1

    size_kb = output_file.stat().st_size / 1024
    print(f"✓ {name} v{version} -> {output_file.name} ({file_count} files, {size_kb:.1f} KB)")
    return output_file


def main() -> int:
    parser = argparse.ArgumentParser(description="打包 MoFox 插件为 .mfp 格式")
    parser.add_argument(
        "paths",
        nargs="*",
        help="插件目录路径（可多个）。留空则打包 plugins/ 下所有含 manifest.json 的子目录",
    )
    parser.add_argument(
        "-o", "--output",
        default="dist",
        help="输出目录，默认 dist",
    )
    parser.add_argument(
        "--with-docs",
        action="store_true",
        help="包含 docs/ 目录和 *.md 文件",
    )
    args = parser.parse_args()

    output_dir = Path(args.output)

    # 确定要打包的插件目录
    if args.paths:
        plugin_dirs = [Path(p) for p in args.paths]
    else:
        plugins_root = Path("plugins")
        if not plugins_root.is_dir():
            print(f"错误：未找到 plugins/ 目录（cwd={Path.cwd()}）", file=sys.stderr)
            return 1
        plugin_dirs = [
            d for d in sorted(plugins_root.iterdir())
            if d.is_dir() and (d / "manifest.json").is_file()
        ]

    if not plugin_dirs:
        print("错误：未找到任何插件目录", file=sys.stderr)
        return 1

    print(f"📦 开始打包 {len(plugin_dirs)} 个插件到 {output_dir}/")
    built = []
    for plugin_dir in plugin_dirs:
        try:
            built.append(build_plugin(plugin_dir, output_dir, args.with_docs))
        except Exception as exc:
            print(f"✗ 打包失败 {plugin_dir}: {exc}", file=sys.stderr)
            return 1

    print(f"\n✓ 构建完成：{len(built)} 个插件包")
    return 0


if __name__ == "__main__":
    sys.exit(main())
