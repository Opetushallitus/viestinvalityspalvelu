#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
readonly repo="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

function main {
  export NVM_DIR="${NVM_DIR:-$HOME/.cache/nvm}"
  source "$repo/scripts/lib/nvm.sh"

  nvm use 20 || nvm install -b 20 && nvm use 20

  cd "$repo/viestinvalitys-raportointi"
  npm ci --force

  export VIRKAILIJA_URL="https://virkailija.hahtuvaopintopolku.fi"
  export VIESTINTAPALVELU_URL="http://localhost:8080/"
  export LOGIN_URL="http://localhost:8080/login"
  export LOCAL="true"
  export NODE_TLS_REJECT_UNAUTHORIZED="0"
  export COOKIE_NAME="JSESSIONID"

  npm run dev
}

main "$@"
