#!/usr/bin/env bash
# Install the CloudyPad CLI (JebetrexxMC fork) and create an instance.
set -euo pipefail
FORK="${CLOUDYPAD_FORK:-JebetrexxMC/cloudypad}"
BRANCH="${CLOUDYPAD_BRANCH:-master}"

if ! command -v docker >/dev/null; then
  echo "Docker is required: https://docs.docker.com/engine/install/" >&2; exit 1
fi
if ! command -v cloudypad >/dev/null; then
  curl -fsSL "https://raw.githubusercontent.com/${FORK}/${BRANCH}/install.sh" | bash
  export PATH="$HOME/.cloudypad/bin:$PATH"
fi
cloudypad create "$@"
