#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../lib/common-functions.sh"

function main {
  init_nodejs

  cd "${repo}/viestinvalitys-raportointi"
  npm_ci_if_needed
  npx playwright install
  npx vitest run --reporter=junit --outputFile=./junit-report.xml
}

main "$@"
