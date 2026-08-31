#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
ENV_FILE="$TOOLS/env.sh"
GIT_MIN="2.46.0"
GIT_FALLBACK_VERSION="2.54.0"
GIT_FALLBACK_SHA256="f689162364c10de79ef89aa8dbf48731eb057e34edbbd20aca510ce0154681a3"
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
  python3-venv
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

# Build host tools against the distro toolchain only. Conda/venv environments commonly export
# PATH and linker/compiler variables that can make a locally built Git accidentally link against
# private libcurl/libiconv copies. That produces binaries which fail to link or later depend on the
# user's activated environment. Keep HOME/locale, but deliberately drop build/linker overrides.
run_clean_host_tool() {
  env \
    -u CONDA_PREFIX \
    -u CONDA_DEFAULT_ENV \
    -u CONDA_EXE \
    -u _CE_CONDA \
    -u _CE_M \
    -u VIRTUAL_ENV \
    -u PYTHONHOME \
    -u PYTHONPATH \
    -u LD_LIBRARY_PATH \
    -u LIBRARY_PATH \
    -u CPATH \
    -u C_INCLUDE_PATH \
    -u CPLUS_INCLUDE_PATH \
    -u PKG_CONFIG_PATH \
    -u PKG_CONFIG_LIBDIR \
    -u CMAKE_PREFIX_PATH \
    -u CFLAGS \
    -u CPPFLAGS \
    -u CXXFLAGS \
    -u LDFLAGS \
    -u LIBS \
    -u CURL_CONFIG \
    -u CC \
    -u CXX \
    -u AR \
    -u RANLIB \
    PATH=/usr/bin:/bin \
    HOME="$HOME" \
    LANG="${LANG:-C.UTF-8}" \
    "$@"
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
    echo "System Git $current is older than depot_tools' recommended $GIT_MIN; building Git $GIT_FALLBACK_VERSION locally."
    echo "Building fallback Git with an isolated distro toolchain (Conda/venv libraries are ignored)."
    local tmp archive
    tmp="$(mktemp -d)"
    archive="$tmp/git.tar.xz"
    if ! run_clean_host_tool /usr/bin/curl -fsSL "https://www.kernel.org/pub/software/scm/git/git-${GIT_FALLBACK_VERSION}.tar.xz" -o "$archive"; then
      rm -rf "$tmp"
      echo "Failed to download Git $GIT_FALLBACK_VERSION." >&2
      exit 2
    fi
    if ! echo "$GIT_FALLBACK_SHA256  $archive" | /usr/bin/sha256sum --check --strict -; then
      rm -rf "$tmp"
      echo "Git source archive checksum verification failed." >&2
      exit 2
    fi
    /usr/bin/tar -xJf "$archive" -C "$tmp"
    rm -rf "$prefix"
    pushd "$tmp/git-$GIT_FALLBACK_VERSION" >/dev/null
    run_clean_host_tool /usr/bin/make configure
    run_clean_host_tool ./configure --prefix="$prefix"
    run_clean_host_tool /usr/bin/make -j"$(nproc)" all NO_TCLTK=YesPlease
    run_clean_host_tool /usr/bin/make install NO_TCLTK=YesPlease
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

ensure_python_env() {
  local host_python python_env
  host_python="${TIHULU_HOST_PYTHON:-/usr/bin/python3}"
  if [[ ! -x "$host_python" ]]; then
    host_python="$(command -v python3)"
  fi
  python_env="$TOOLS/python"

  if [[ ! -x "$python_env/bin/python3" ]]; then
    echo "Creating isolated Python environment for Brave hooks."
    rm -rf "$python_env"
    "$host_python" -m venv "$python_env"
  fi

  if ! "$python_env/bin/python3" -m pip --version >/dev/null 2>&1; then
    echo "The isolated Python environment does not contain pip." >&2
    exit 2
  fi
}

ensure_git
ensure_node24
ensure_python_env

mkdir -p "$TOOLS/bin"
# Generate executable shell syntax without an expanding heredoc. The project-local
# Python is kept first for our scripts; Brave's own build/env.sh supplies its PYTHONPATH
# immediately before gclient hooks are run.
{
  printf 'export PATH="%s/bin:%s/node24/bin:%s/python/bin:$PATH"\n' "$TOOLS" "$TOOLS" "$TOOLS"
  cat <<'EOF_ENV'
# Tihulu host toolchain. Brave hook-specific PATH/PYTHONPATH setup lives in bootstrap.sh.
EOF_ENV
} > "$ENV_FILE"

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
  echo "Git >=$GIT_MIN is required by this build wrapper, but $(git --version) is active." >&2
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
if ! python3 -m pip --version >/dev/null 2>&1; then
  echo "Brave hooks require a Python environment with pip, but pip is unavailable." >&2
  exit 2
fi

echo "Host dependencies ready."
echo "Git: $(git --version)"
echo "Node: $(node --version)"
echo "pnpm: $(pnpm --version)"
echo "Python: $(python3 --version)"
echo "pip: $(python3 -m pip --version)"
echo "Java: $(javac -version 2>&1)"
echo "ADB: $(adb version | head -n1)"
