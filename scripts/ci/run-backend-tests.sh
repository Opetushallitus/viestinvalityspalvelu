#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../lib/common-functions.sh"

function main {
  select_java_version 21

  cd "${repo}"
  if is_running_on_codebuild; then
    ./mvnw test -s ./settings.xml
  else
    ./mvnw test
  fi
}

main "$@"
