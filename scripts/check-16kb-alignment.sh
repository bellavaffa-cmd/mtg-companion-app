#!/usr/bin/env bash
# Fails if any native library in the given APKs won't load on a 16 KB-page Android device
# (Android 15+; Google Play requires 16 KB support for apps targeting Android 15 and up).
#
#   scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk
#
# Two checks per APK:
#   1. ELF: every LOAD segment of every 64-bit .so (arm64-v8a, x86_64) is aligned to at least 16 KB
#      (2**14). This is what matters whether the libs are stored compressed (extracted at install,
#      the GitHub APKs) or not. 32-bit ABIs never run with 16 KB pages, so they're only reported.
#   2. Zip: `zipalign -c -P 16 -v 4` — uncompressed .so entries start on a 16 KB boundary inside the
#      APK, so they can be mapped straight from it (the Play build stores them uncompressed).
#
# Each library's result is printed as a GitHub Actions annotation, readable from the API.
set -uo pipefail

[ $# -gt 0 ] || { echo "usage: $0 app.apk [more.apk ...]" >&2; exit 2; }

# zipalign's -P (page alignment) arrived in build-tools 35: the newest installed one of those.
zipalign=""
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
  echo "build-tools installed: $(ls "$ANDROID_HOME/build-tools" | tr '\n' ' ')"
  for dir in $(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V -r); do
    major=$(basename "$dir" | cut -d. -f1)
    if [ -x "$dir/zipalign" ] && [ "$major" -ge 35 ] 2>/dev/null; then zipalign="$dir/zipalign"; break; fi
  done
fi
ok_libs=""

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failed=0
declare -A reported

for apk in "$@"; do
  echo "== $apk"
  rm -rf "$work/x"; mkdir -p "$work/x"
  unzip -q -o "$apk" 'lib/*' -d "$work/x" 2>/dev/null || true
  while IFS= read -r so; do
    rel=${so#"$work/x/"}
    abi=$(echo "$rel" | cut -d/ -f2)
    # The smallest LOAD segment alignment, e.g. 0x4000.
    min=$(readelf -lW "$so" | awk '$1 == "LOAD" { print $NF }' | while read -r a; do printf '%d\n' "$a"; done | sort -n | head -n 1)
    if [ -z "$min" ]; then
      echo "::error::$rel: couldn't read its ELF program headers"; failed=1; continue
    fi
    key="$rel"
    case "$abi" in
      arm64-v8a|x86_64)
        if [ "$min" -ge 16384 ]; then
          [ -n "${reported[$key]:-}" ] || { echo "16 KB OK: $rel (LOAD align $min)"; ok_libs="$ok_libs $rel"; }
        else
          echo "::error::NOT 16 KB aligned: $rel (LOAD align $min) — upgrade the library that ships it"
          failed=1
        fi ;;
      *)
        [ -n "${reported[$key]:-}" ] || echo "32-bit, not checked: $rel (LOAD align $min)" ;;
    esac
    reported[$key]=1
  done < <(find "$work/x/lib" -name '*.so' 2>/dev/null | sort)

  if [ -n "$zipalign" ]; then
    if ! "$zipalign" -c -P 16 -v 4 "$apk" > "$work/zipalign.log" 2>&1; then
      grep -E 'lib/.*\.so' "$work/zipalign.log" | grep -v 'OK' | head -n 20 | while IFS= read -r line; do echo "::error::$(basename "$apk"): zip entry not 16 KB aligned: $line"; done
      echo "::error::$(basename "$apk") fails zipalign -c -P 16"
      failed=1
    else
      echo "zipalign -P 16: OK"
    fi
  else
    echo "::warning::No zipalign with -P (build-tools 35+) found; zip alignment not checked"
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "::error::Some native code isn't 16 KB page-size compatible (see above)"
  exit 1
fi
# One annotation for the lot (Actions keeps only the first 10 notices of a step).
echo "::notice::16 KB aligned (ELF LOAD >= 16384):$ok_libs"
echo "All native libraries are 16 KB page-size compatible."
