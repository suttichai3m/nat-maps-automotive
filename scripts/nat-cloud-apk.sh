#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ "${GITHUB_REF:-}" == refs/heads/map-original ]] || { echo "Use map-original"; exit 1; }
[[ "${VERSION:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9]+)*$ ]] || { echo "Invalid version"; exit 1; }
[[ "${VAULT:-}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ && -n "${GH_TOKEN:-}" ]] || { echo "Configure private vault and token"; exit 1; }
[[ "$(gh api "repos/$VAULT" --jq '.private')" == true ]] || { echo "Vault must be private"; exit 1; }
[[ "$VAULT" != "$GITHUB_REPOSITORY" ]] || { echo "Vault must be separate"; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export MORPHE_DATA_DIR="$work/morphe-data"
mkdir -p "$MORPHE_DATA_DIR" "$work/out"
shopt -s nullglob
bundles=(patches/build/libs/*.mpp)
[[ ${#bundles[@]} == 1 ]] || { echo "Expected exactly one MPP"; exit 1; }
bundle="$(realpath "${bundles[0]}")"
curl --fail --silent --show-error --location --retry 3 --proto '=https' --tlsv1.2 'https://github.com/MorpheApp/morphe-desktop/releases/download/v2.4.0/morphe-desktop-2.4.0-all.jar' -o "$work/morphe.jar"
echo "d5f75c05e6c84731dc18faa658810a3ed49ab32d60b756da8503432e78cf1477  $work/morphe.jar" | sha256sum -c -
# Never expose the vault token or signing secrets to the third-party CLI.
cli(){ env -u GH_TOKEN -u KEYSTORE_B64 -u STORE_PASSWORD -u ENTRY_PASSWORD java -Djava.awt.headless=true -jar "$work/morphe.jar" "$@"; }
tag="nat-${MODE}-${VERSION}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
if [[ "$MODE" == options ]]; then
  cli options-create -p "$bundle" -o "$work/out/options.json" -f com.google.android.apps.maps
  gh release create "$tag" "$work/out/options.json" --repo "$VAULT" --title "NAT options $VERSION" --notes "Review defaults against your desktop selections before use. Source $GITHUB_SHA"
  echo "Options generated for review; no APK built."
  exit 0
fi
[[ "$MODE" == build ]] || exit 1
[[ "${ORIGINAL_SHA256:-}" =~ ^[[:xdigit:]]{64}$ && "${EXPECTED_CERT:-}" =~ ^[[:xdigit:]]{64}$ && -n "${KEYSTORE_B64:-}" ]] || { echo "Configure source hash, signer hash and keystore"; exit 1; }
gh release download nat-inputs --repo "$VAULT" --pattern original.apk --pattern options.json --dir "$work"
echo "${ORIGINAL_SHA256,,}  $work/original.apk" | sha256sum -c -
test -s "$work/options.json"
python3 -m json.tool "$work/options.json" >/dev/null
printf '%s' "$KEYSTORE_B64" | base64 --decode > "$work/morphe.keystore"
test -s "$work/morphe.keystore"
unset KEYSTORE_B64
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/36.0.0"
original="$work/original.apk"
"$sdk/apksigner" verify "$original"
badging=$("$sdk/aapt" dump badging "$original")
grep -F "package: name='com.google.android.apps.maps'" <<<"$badging" >/dev/null
grep -F "versionName='26.10.120003.E'" <<<"$badging" >/dev/null
out="$work/out/NAT-Maps-V${VERSION}.apk"
# Use exact reviewed options and normal compatibility checks; never force or continue on error.
cli patch -p "$bundle" --options-file "$work/options.json" --keystore "$work/morphe.keystore" --keystore-password "${STORE_PASSWORD:-}" --keystore-entry-alias "${ENTRY_ALIAS:-Morphe}" --keystore-entry-password "${ENTRY_PASSWORD:-Morphe}" -o "$out" "$original"
test -s "$out"
"$sdk/apksigner" verify --verbose --print-certs "$out" > "$work/out/signature.txt"
cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$work/out/signature.txt")
[[ "${cert,,}" == "${EXPECTED_CERT,,}" ]] || { echo "Signer mismatch; refusing publication"; exit 1; }
[[ $(grep -c '^Signer #[0-9]* certificate SHA-256 digest:' "$work/out/signature.txt") == 1 ]] || { echo "Unexpected signer count"; exit 1; }
"$sdk/aapt" dump badging "$out" > "$work/out/manifest.txt"
grep -F "package: name='app.morphe.android.apps.maps.automotive'" "$work/out/manifest.txt" >/dev/null
grep -F "versionName='26.10.120003.E'" "$work/out/manifest.txt" >/dev/null
python3 - "$out" <<'PY'
import sys,zipfile,hashlib,zlib,struct
with zipfile.ZipFile(sys.argv[1]) as z:
    assert z.testzip() is None, "ZIP CRC failure"
    dex=[n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")]
    assert dex and any(n.startswith("lib/arm64-v8a/") for n in z.namelist()), "Missing DEX or ARM64 libraries"
    found=set()
    for n in dex:
        d=z.read(n)
        for marker in (b"Lapp/morphe/extension/mapsautomotive/patches/NatEvPlanner;",b"Lapp/morphe/extension/mapsautomotive/patches/NatDriverWork;",b"Lapp/morphe/extension/mapsautomotive/patches/NatNativeEvLayer;",b"Lapp/morphe/extension/mapsautomotive/patches/NatEvLearning;"):
            if marker in d: found.add(marker)
        assert d[12:32]==hashlib.sha1(d[32:]).digest(), n
        assert struct.unpack_from("<I",d,8)[0]==zlib.adler32(d[12:]) & 0xffffffff,n
    assert len(found)==4, "Missing required V4 candidate classes in patched APK"
PY
(cd "$work/out"; sha256sum "NAT-Maps-V${VERSION}.apk" > SHA256SUMS.txt)
printf 'Source: %s\nRun: %s / %s\nCLI: 2.4.0\n' "$GITHUB_SHA" "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" > "$work/out/build-info.txt"
gh release create "$tag" "$work/out/"* --repo "$VAULT" --title "NAT Maps V$VERSION" --notes "Private verified APK. Source $GITHUB_SHA. Vehicle testing not implied."
echo "Private release: https://github.com/$VAULT/releases/tag/$tag" >> "$GITHUB_STEP_SUMMARY"
