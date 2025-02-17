#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  select_java_version "21"
  require_command "mvn"

  cd "${repo}"
  mvn install -DskipTests

  wait_for_containers_to_be_healthy

  cd "${repo}/integraatio"
  mvn -Dexec.mainClass="fi.oph.viestinvalitys.DevApp" -Dexec.classpathScope=test test-compile exec:java
}

function select_java_version {
  java_version="$1"
  JAVA_HOME="$(/usr/libexec/java_home -v "${java_version}")"
  export JAVA_HOME
}

function wait_for_containers_to_be_healthy {
  wait_for_container_to_be_healthy viestinvalitys-localstack
}

main "$@"
