#!/usr/bin/env bash
set -euo pipefail

if [[ "$GITHUB_RUN_ATTEMPT" != 1 ]]; then
  echo "::error::Manual retries are disabled. To redeploy the current version, open Actions in software-materialization-service, run Bounce, and enter $GITHUB_REPOSITORY as the materializer repository."
  exit 1
fi
