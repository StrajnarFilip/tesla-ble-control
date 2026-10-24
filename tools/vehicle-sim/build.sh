#!/usr/bin/env bash
# Builds the reference vehicle simulator. The simulator is compiled inside a copy of the pinned
# vehicle-command module so it can use Tesla's own (internal) vehicle-side authentication code
# unchanged. Host-only test tooling: nothing here ships in the app.
#
# Usage: tools/vehicle-sim/build.sh            build build/bin/vehicle-sim
#        tools/vehicle-sim/build.sh --golden   also regenerate the golden command payloads
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
VERSION="v0.4.2-0.20261021222658-fc5a1910ad45"
MODULE="github.com/teslamotors/vehicle-command"
BUILD="$HERE/build"
SRC="$BUILD/vehicle-command"

command -v go >/dev/null || { echo "go is required to build the simulator" >&2; exit 1; }
mkdir -p "$BUILD/bin"

if [ ! -f "$SRC/.pinned-version" ] || [ "$(cat "$SRC/.pinned-version")" != "$VERSION" ]; then
  go mod download "$MODULE@$VERSION"
  MODDIR="$(go env GOMODCACHE)/$MODULE@$VERSION"
  rm -rf "$SRC"
  cp -r "$MODDIR" "$SRC"
  chmod -R u+w "$SRC"
  echo "$VERSION" > "$SRC/.pinned-version"
fi

rm -rf "$SRC/cmd/vehicle-sim"
mkdir -p "$SRC/cmd/vehicle-sim"
cp "$HERE"/sim/*.go "$SRC/cmd/vehicle-sim/"
(cd "$SRC" && go vet ./cmd/vehicle-sim && go build -o "$BUILD/bin/vehicle-sim" ./cmd/vehicle-sim)
echo "built $BUILD/bin/vehicle-sim"

if [ "${1:-}" = "--golden" ]; then
  OUT="$ROOT/protocol/src/test/resources/golden/commands.json"
  mkdir -p "$(dirname "$OUT")"
  "$BUILD/bin/vehicle-sim" golden > "$OUT"
  echo "wrote $OUT"
fi
