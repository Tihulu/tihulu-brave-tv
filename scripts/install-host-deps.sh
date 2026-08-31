#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
ENV_FILE="$TOOLS/env.sh"
GIT_MIN="2.41.0"
GIT_FALLBACK_VERSION="2.54.0"
NODE_MIN="24.16.0"
NODE_MAX="25.0.0"
PNPM_MIN="11.9.0"

if [[ ! -r /etc/os-release ]]; then
  echo "Unsupported host: /etc/os-release is missing." >&2
  exit 2
fi
# shellcheck disable=SC1091
source /etc/os-release
ID_LIKE_VALUE="${ID_LIKE:-}"
case " ${ID:-} $ID_LIKE_VALUE " in
  *ubuntu*|*debian*|*pop*) ;;
  *)
    echo "This installer currently supports Ubuntu, Pop!_OS and Debian hosts." >&2
    exit 2
    ;;
esac

if (( EUID == 0 )); then
  SUDO=()
elif command -v sudo >/dev/null 2>&1; then
  SUDO=(sudo)
else
  echo "sudo is required when this script is not run as root." >&2
  exit 2
fi

export DEBIAN_FRONTEND=noninteractive
"${SUDO[@]}" apt-get update
BASE_PACKAGES=(
  adb
  autoconf
  build-essential
  ca-certificates
  curl
  file
  gettext
  git
  libcurl4-openssl-dev
  libexpat1-dev
  libssl-dev
  libz-dev
  make
  perl
  pkg-config
  python3
  python3-setuptools
  rsync
  unzip
  xz-utils
  zip
)
"${SUDO[@]}" apt-get install -y "${BASE_PACKAGES[@]}"

package_has_candidate() {
  local package="$1"
  local candidate
  candidate="$(LC_ALL=C apt-cache policy "$package" | awk '$1 == "Candidate:" {print $2; exit}')"
  [[ -n "$candidate" && "$candidate" != "(none)" ]]
}

# The lightweight validation suite compiles Java sources before the large Chromium build.
# Prefer JDK 21 to match CI, but keep a distro-provided default JDK fallback for Debian-family
# releases that do not publish openjdk-21-jdk-headless.
if ! command -v javac >/dev/null 2>&1; then
  if package_has_candidate openjdk-21-jdk-headless; then
    "${SUDO[@]}" apt-get install -y openjdk-21-jdk-headless
  elif package_has_candidate default-jdk-headless; then
    "${SUDO[@]}" apt-get install -y default-jdk-headless
  else
    echo "No supported JDK package is available; javac is required for validation." >&2
    exit 2
  fi
fi
if ! command -v javac >/dev/null 2>&1; then
  echo "JDK installation completed but javac is still unavailable in PATH." >&2
  exit 2
fi

# python-is-python3 is useful for Brave/Chromium, but some derivatives may not publish it.
if package_has_candidate python-is-python3; then
  "${SUDO[@]}" apt-get install -y python-is-python3
elif ! command -v python >/dev/null 2>&1; then
  mkdir -p "$TOOLS/bin"
  ln -sfn "$(command -v python3)" "$TOOLS/bin/python"
fi

# Brave's wiki still mentions python3-distutils for some Ubuntu generations. Ubuntu 24.04
# no longer publishes it as an installable package, even though stale APT metadata can make
# `apt-cache show` succeed. Only install it when APT reports a real candidate version.
if package_has_candidate python3-distutils; then
  "${SUDO[@]}" apt-get install -y python3-distutils
fi

mkdir -p "$TOOLS"

version_ge() {
  [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n1)" == "$2" ]]
}

ensure_git() {
  local current="0"
  if command -v git >/dev/null 2>&1; then
    current="$(git --version | awk '{print $3}')"
  fi
  if version_ge "$current" "$GIT_MIN"; then
    return
  fi

  local prefix="$TOOLS/git-$GIT_FALLBACK_VERSION"
  if [[ ! -x "$prefix/bin/git" ]]; then
    echo "System Git $current is older than Brave's required $GIT_MIN; building Git $GIT_FALLBACK_VERSION locally."
    local tmp
    tmp="$(mktemp -d)"
    curl -fsSL "https://www.kernel.org/pub/software/scm/git/git-${GIT_FALLBACK_VERSION}.tar.xz" \
      -o "$tmp/git.tar.xz"
    tar -xJf "$tmp/git.tar.xz" -C "$tmp"
    pushd "$tmp/git-$GIT_FALLBACK_VERSION" >/dev/null
    make configure
    ./configure --prefix="$prefix"
    make -j"$(nproc)" all NO_TCLTK=YesPlease
    make install NO_TCLTK=YesPlease
    popd >/dev/null
    rm -rf "$tmp"
  fi
  mkdir -p "$TOOLS/bin"
  ln -sfn "$prefix/bin/git" "$TOOLS/bin/git"
}

ensure_node24() {
  local current="0"
  if command -v node >/dev/null 2>&1; then
    current="$(node -p 'process.versions.node' 2>/dev/null || echo 0)"
  fi
  if version_ge "$current" "$NODE_MIN" && ! version_ge "$current" "$NODE_MAX"; then
    return
  fi

  local machine node_arch
  machine="$(uname -m)"
  case "$machine" in
    x86_64|amd64) node_arch=x64 ;;
    aarch64|arm64) node_arch=arm64 ;;
    *) echo "Unsupported build-host CPU for automatic Node 24 install: $machine" >&2; exit 2 ;;
  esac

  local sums file sha prefix tmp
  sums="$(curl -fsSL https://nodejs.org/dist/latest-v24.x/SHASUMS256.txt)"
  read -r sha file < <(
    awk -v suffix="linux-${node_arch}.tar.xz" '$2 ~ suffix "$" {print $1, $2; exit}' <<<"$sums"
  )
  if [[ -z "${file:-}" || -z "${sha:-}" ]]; then
    echo "Could not resolve the latest Node.js 24 binary." >&2
    exit 2
  fi
  prefix="$TOOLS/${file%.tar.xz}"
  if [[ ! -x "$prefix/bin/node" ]]; then
    echo "Installing $file locally."
    tmp="$(mktemp -d)"
    curl -fsSL "https://nodejs.org/dist/latest-v24.x/$file" -o "$tmp/$file"
    echo "$sha  $tmp/$file" | sha256sum --check --strict -
    tar -xJf "$tmp/$file" -C "$TOOLS"
    rm -rf "$tmp"
  fi
  ln -sfn "$prefix" "$TOOLS/node24"
}

ensure_git
ensure_node24

mkdir -p "$TOOLS/bin"
cat > "$ENV_FILE" <<EOF_ENV
export PATH="$TOOLS/bin:$TOOLS/node24/bin:\$PATH"
EOF_ENV

# shellcheck disable=SC1090
source "$ENV_FILE"

ensure_pnpm() {
  local current="0"
  if command -v pnpm >/dev/null 2>&1; then
    current="$(pnpm --version 2>/dev/null || echo 0)"
  fi
  if version_ge "$current" "$PNPM_MIN"; then
    return
  fi

  local prefix="$TOOLS/pnpm"
  echo "Installing pnpm >=$PNPM_MIN locally."
  npm install --prefix "$prefix" "pnpm@^11.9.0"
  ln -sfn "$prefix/node_modules/.bin/pnpm" "$TOOLS/bin/pnpm"
}

ensure_pnpm

if ! version_ge "$(git --version | awk '{print $3}')" "$GIT_MIN"; then
  echo "Git 2.41+ is required, but $(git --version) is active." >&2
  exit 2
fi
NODE_VERSION="$(node -p 'process.versions.node')"
if ! version_ge "$NODE_VERSION" "$NODE_MIN" || version_ge "$NODE_VERSION" "$NODE_MAX"; then
  echo "Brave requires Node >=$NODE_MIN and <$NODE_MAX, but $(node --version) is active." >&2
  exit 2
fi
PNPM_VERSION="$(pnpm --version)"
if ! version_ge "$PNPM_VERSION" "$PNPM_MIN"; then
  echo "Brave requires pnpm >=$PNPM_MIN, but pnpm $PNPM_VERSION is active." >&2
  exit 2
fi

echo "Host dependencies ready."
echo "Git: $(git --version)"
echo "Node: $(node --version)"
echo "pnpm: $(pnpm --version)"
echo "Python: $(python3 --version)"
echo "Java: $(javac -version 2>&1)"
echo "ADB: $(adb version | head -n1)"
