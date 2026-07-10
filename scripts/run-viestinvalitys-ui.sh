#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/lib/common-functions.sh"

function main {
  cd "$repo"
  init_nodejs
  cd "$repo/viestinvalitys-ui/"
  npm_ci_if_needed

  NODE_ENV=development npx webpack serve --mode development
}

main "$@"
