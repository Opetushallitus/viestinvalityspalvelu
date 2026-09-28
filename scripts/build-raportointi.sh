#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  pushd ${repo}/viestinvalitys-raportointi
  init_nodejs
  npm_ci_if_needed
  popd
}

function build_open_next_part {
  npx open-next build
}

main "$@"