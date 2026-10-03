#!/usr/bin/env python3
"""Keep raw application styling in the theme; user color parsing and zero geometry are allowed."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/com/datadragon/app/ui'
RULES = {
    'nonzero dp literal': r'\b(?!0\.dp\b)\d+(?:\.\d+)?\.dp\b',
    'font size literal': r'\b\d+(?:\.\d+)?\.sp\b',
    'literal application color': r'Color\((?:0x|[0-9])|Color\.(?:Black|White|Gray|Red|Green|Blue|Yellow|Cyan|Magenta|DarkGray|LightGray)\b',
    'screen-owned shape': r'\b(?:RoundedCornerShape|CircleShape|CutCornerShape)\b',
    'screen-owned font': r'\b(?:FontWeight|FontFamily)\.',
    'disabled alpha literal': r'alpha\s*=\s*0\.[0-9]+f',
}


def code_only(text):
    # Ignore documentation and quoted UI wording, preserving newlines for diagnostics.
    return re.sub(r'/\*[\s\S]*?\*/|//[^\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"',
                  lambda m: '\n' * m.group().count('\n'), text)


def main():
    errors = []
    files = [p for p in UI.rglob('*.kt') if 'theme' not in p.relative_to(UI).parts]
    for path in files:
        code = code_only(path.read_text())
        for reason, pattern in RULES.items():
            for match in re.finditer(pattern, code):
                line = code[:match.start()].count('\n') + 1
                errors.append(f'{path.relative_to(ROOT)}:{line}: {reason}: {match.group()}')
    if errors:
        print('\n'.join(errors))
        return 1
    print(f'Theme token check passed ({len(files)} UI files).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
