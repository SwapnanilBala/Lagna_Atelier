#!/usr/bin/env python3
"""
Which English strings are not translated yet, and what is wrong with the ones that are.

    python tools/i18n/check_translations.py              # every language
    python tools/i18n/check_translations.py hi de        # just these
    python tools/i18n/check_translations.py --missing    # only the untranslated keys, one per line
    python tools/i18n/check_translations.py --skeleton hi strings_timing.xml
                                                         # an English copy of that file's untranslated
                                                         # entries, ready to translate into values-hi

English lives in app/src/main/res/values/strings*.xml; each translation is a
file of the same name in values-<lang>. A key counts as translated when the
same file in values-<lang> has it (a translation in another file of that
folder is reported as misplaced). Strings marked translatable="false" are
skipped. Checks on what is translated:

  - placeholders: the same %1$s / %2$d set as English (order may change)
  - string arrays: as many items as English
  - plurals: every quantity English has (Android adds its own for some
    languages; extra ones are fine)
  - keys no longer in English (stale)
  - Hindi and Bengali text that is not NFC-normalised
  - an apostrophe Android would reject (a bare ' outside quotes)
  - words the copy must never use: AI in any language, paid / free / expensive

Exit status 1 when anything other than missing keys is wrong, so it can gate
a commit; missing keys alone exit 0 (new English lands before its
translations).
"""
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
LANGUAGES = ["hi", "bn", "es", "fr", "it", "de"]
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdf]")

# Whole words, case-insensitive except acronyms. The owner's rules: never "AI" in reader-facing
# copy, never paid / free / expensive.
BANNED = {
    "en": [r"\bAI\b", r"\bpaid\b", r"\bfree\b", r"\bexpensive\b"],
    "hi": [r"एआई", r"\bAI\b", r"मुफ़?्त", r"सशुल्क", r"महँगा", r"महंगा"],
    "bn": [r"এআই", r"\bAI\b", r"বিনামূল্যে", r"ফ্রি", r"দামি"],
    "es": [r"\bIA\b", r"\bAI\b", r"\bgratis\b", r"\bgratuit[oa]s?\b", r"\bde pago\b", r"\bcaros?\b"],  # not "cara": the site's "La otra cara" is a face, not a price
    "fr": [r"\bIA\b", r"\bAI\b", r"\bgratuit(e|s|es)?\b", r"\bpayant(e|s|es)?\b", r"\bcher\b", r"\bchère\b"],
    "it": [r"\bIA\b", r"\bAI\b", r"\bgratis\b", r"\bgratuit[oaie]\b", r"\ba pagamento\b", r"\bcostos[oaie]\b"],
    "de": [r"\bKI\b", r"\bAI\b", r"\bkostenlos\b", r"\bgratis\b", r"\bkostenpflichtig\b", r"\bteuer\b"],
}
ACRONYM = re.compile(r"\\b[A-Z]+\\b")


def banned(pattern, text):
    """Words match in any case; acronyms (AI, IA, KI) only in capitals, so Italian "ai" is not "AI"."""
    return re.search(pattern, text, 0 if ACRONYM.fullmatch(pattern) else re.IGNORECASE) is not None


def text_of(element):
    """The resource's text as written, inner markup included."""
    inner = (element.text or "") + "".join(ET.tostring(child, encoding="unicode") for child in element)
    return inner.strip()


def load(folder):
    """{file name: {key: ("string", text) | ("plurals", {quantity: text})}}, translatable entries only."""
    out = {}
    for path in sorted(folder.glob("strings*.xml")):
        entries = {}
        for element in ET.parse(path).getroot():
            if element.get("translatable") == "false":
                continue
            name = element.get("name")
            if element.tag == "string":
                entries[name] = ("string", text_of(element))
            elif element.tag == "plurals":
                entries[name] = ("plurals", {item.get("quantity"): text_of(item) for item in element})
            elif element.tag == "string-array":
                entries[name] = ("array", {str(i): text_of(item) for i, item in enumerate(element)})
        out[path.name] = entries
    return out


def texts(entry):
    kind, value = entry
    return [value] if kind == "string" else list(value.values())


def placeholders(entry):
    found = set()
    for text in texts(entry):
        found |= set(PLACEHOLDER.findall(text))
    return found


def bare_apostrophe(text):
    if text.startswith('"') and text.endswith('"'):
        return False
    return re.search(r"(?<!\\)'", text) is not None


def check(language, english, report_missing_only):
    folder = RES / f"values-{language}"
    translated = load(folder) if folder.is_dir() else {}
    all_translated = {key: (name, entry) for name, entries in translated.items() for key, entry in entries.items()}
    missing, problems = [], []

    for file_name, entries in english.items():
        here = translated.get(file_name, {})
        for key, entry in entries.items():
            if key not in here:
                if key in all_translated:
                    problems.append(f"{key}: in values-{language}/{all_translated[key][0]}, belongs in {file_name}")
                else:
                    missing.append((file_name, key))
                continue
            theirs = here[key]
            if theirs[0] != entry[0]:
                problems.append(f"{file_name} {key}: is a {theirs[0]} here, a {entry[0]} in English")
                continue
            if placeholders(theirs) != placeholders(entry):
                problems.append(f"{file_name} {key}: placeholders {sorted(placeholders(theirs))}, English has {sorted(placeholders(entry))}")
            if entry[0] == "plurals":
                lost = set(entry[1]) - set(theirs[1])
                if lost:
                    problems.append(f"{file_name} {key}: no plural for {sorted(lost)}")
            if entry[0] == "array" and len(theirs[1]) != len(entry[1]):
                problems.append(f"{file_name} {key}: {len(theirs[1])} items, English has {len(entry[1])}")
            for text in texts(theirs):
                if language in ("hi", "bn") and unicodedata.normalize("NFC", text) != text:
                    problems.append(f"{file_name} {key}: not NFC-normalised")
                if bare_apostrophe(text):
                    problems.append(f"{file_name} {key}: bare apostrophe (write \\' or ’)")
                for pattern in BANNED.get(language, []):
                    if banned(pattern, text):
                        problems.append(f"{file_name} {key}: uses a word the copy never uses ({pattern})")

    english_keys = {key for entries in english.values() for key in entries}
    for file_name, entries in translated.items():
        for key in entries:
            if key not in english_keys:
                problems.append(f"{file_name} {key}: not in English any more")

    if report_missing_only:
        for file_name, key in missing:
            print(f"{language}\t{file_name}\t{key}")
        return problems
    print(f"values-{language}: {len(missing)} untranslated, {len(problems)} problem(s)")
    by_file = {}
    for file_name, key in missing:
        by_file.setdefault(file_name, []).append(key)
    for file_name, keys in by_file.items():
        print(f"  untranslated in {file_name}: {', '.join(keys)}")
    for problem in problems:
        print(f"  ! {problem}")
    return problems


def skeleton(language, file_name):
    """The English entries of [file_name] that values-<language> lacks, as resource XML to translate."""
    source = RES / "values" / file_name
    target = RES / f"values-{language}" / file_name
    have = set(load(target.parent).get(file_name, {})) if target.exists() else set()
    print('<?xml version="1.0" encoding="utf-8"?>\n<resources>')
    for line_group in re.finditer(r"(?s)( *<(string|plurals|string-array) name=\"([^\"]+)\"(?![^>]*translatable=\"false\").*?</\2>)", source.read_text(encoding="utf-8")):
        if line_group.group(3) not in have:
            print(line_group.group(1))
    print("</resources>")


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    if argv[:1] == ["--skeleton"]:
        skeleton(argv[1], argv[2])
        return 0
    missing_only = "--missing" in argv
    languages = [a for a in argv if not a.startswith("--")] or LANGUAGES
    english = load(RES / "values")
    for file_name, entries in english.items():
        for key, entry in entries.items():
            for text in texts(entry):
                for pattern in BANNED["en"]:
                    if banned(pattern, text):
                        print(f"! English {file_name} {key}: uses a word the copy never uses ({pattern})")
    failed = False
    for language in languages:
        failed |= bool(check(language, english, missing_only))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
