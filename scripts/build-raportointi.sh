#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  if [[ $# -eq 0 ]]; then
      exec "$0" "opennext"
  else
    pushd ${repo}/viestinvalitys-raportointi
    init_nodejs
    npm_ci_if_needed

    case "$1" in
      next)
        npm run build
        ;;
      opennext)
        npx open-next build -- --build-command "$0 next",
        ;;
    esac

    popd
  fi
}

main "$@"