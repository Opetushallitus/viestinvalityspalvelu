#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && cd ../.. && pwd)"
source "${repo}/scripts/lib/common-functions.sh"

function main {
  run_prettier_check
}

function run_prettier_check {
  local -a failed_dirs=()
  for dir in "$repo/infra" "$repo/viestinvalitys-raportointi" "$repo/playwright"; do
    cd "$dir"
    init_nodejs
    npm_ci_if_needed
    local -a ignore_args=("--ignore-path=$repo/.gitignore")
    if [ -f ".gitignore" ]; then
      ignore_args+=("--ignore-path=.gitignore")
    fi
    if ! npx prettier . --check "${ignore_args[@]}"; then
      failed_dirs+=("$dir")
    fi
  done
  if [ "${#failed_dirs[@]}" -gt 0 ]; then
    for dir in "${failed_dirs[@]}"; do
      log ERROR "Prettier check failed in: $dir"
    done
    log ERROR "Run scripts/format-code.sh to fix formatting"
    exit 1
  fi
}

main "$@"
