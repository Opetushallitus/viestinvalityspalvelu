#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  pushd ${repo}/viestinvalitys-raportointi
  init_nodejs
  npm_ci_if_needed
  build_nextjs_part
  build_open_next_part
  popd
}

function build_nextjs_part {
  npx next build
}

function build_open_next_part {
  npx open-next build
}

main "$@"