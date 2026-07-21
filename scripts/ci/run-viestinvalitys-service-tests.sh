#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../lib/common-functions.sh"

trap cleanup EXIT INT QUIT TERM

function main {
  select_java_version "21"

  cd "${repo}"

  if is_running_on_ci; then
    docker compose up -d
  fi

  wait_for_container_to_be_healthy viestinvalitys-test-postgres
  wait_for_container_to_be_healthy viestinvalitys-keycloak

  cd "${repo}/viestinvalitys-service"

  if is_running_on_codebuild; then
    ../mvnw --batch-mode clean install -s ./codebuild-mvn-settings.xml
  else
    ../mvnw --batch-mode clean install
  fi
}

function cleanup {
  if is_running_on_ci; then
    cd "${repo}"
    docker compose down
  fi
}

main "$@"
