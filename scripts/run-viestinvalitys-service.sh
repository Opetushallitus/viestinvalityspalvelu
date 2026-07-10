#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  select_java_version "21"

  cd "${repo}/viestinvalitys-service"

  wait_for_containers_to_be_healthy

  local -r jvm_args=(
    "-Dspring.profiles.active=${1:-local}"
  )

  ../mvnw clean install -DskipTests
  ../mvnw -Dspring-boot.run.jvmArguments="${jvm_args[*]}" spring-boot:run
}

function wait_for_containers_to_be_healthy {
  wait_for_container_to_be_healthy viestinvalitys-postgres
}

main "$@"
