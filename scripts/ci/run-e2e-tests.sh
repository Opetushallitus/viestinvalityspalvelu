#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../lib/common-functions.sh"

function cleanup {
  info "Cleaning up..."
  kill_background_processes
  stop_docker_containers
}

function main {
  trap cleanup EXIT
  init_nodejs
  select_java_version 21
  start_docker_containers
  start_backend
  start_frontend
  run_playwright_tests
}

function kill_background_processes {
  jobs -p | xargs kill || true
}

function stop_docker_containers {
  cd "${repo}/integraatio/docker"
  docker compose down --volumes
}

function start_docker_containers {
  info "Starting Docker containers..."
  cd "${repo}/integraatio/docker"
  docker compose down --volumes
  docker compose up --force-recreate --renew-anon-volumes -d
  wait_for_container_to_be_healthy viestinvalitys-localstack
  wait_for_container_to_be_healthy viestinvalitys-postgres
  info "Docker containers started."
}

function start_backend {
  info "Building and starting Backend..."
  cd "${repo}"
  if is_running_on_codebuild; then
    ./mvnw --batch-mode install -DskipTests -s ./settings.xml
  else
    ./mvnw --batch-mode install -DskipTests
  fi

  cd "${repo}/integraatio"
  ../mvnw -Dexec.mainClass="fi.oph.viestinvalitys.DevApp" -Dexec.classpathScope=test test-compile exec:java > backend.log 2>&1 &

  info "Waiting for Backend to be ready..."
  until curl -s http://localhost:8080/lahetys/v1/healthcheck > /dev/null; do
    sleep 5
    info "Still waiting for Backend..."
  done
  info "Backend started."
}

function start_frontend {
  info "Starting Frontend..."
  cd "${repo}/viestinvalitys-raportointi"
  npm_ci_if_needed

  export VIRKAILIJA_URL="https://virkailija.testiopintopolku.fi"
  export VIESTINTAPALVELU_URL="http://localhost:8080"
  export LOGIN_URL="http://localhost:8080/login"
  export LOCAL="true"
  export NODE_TLS_REJECT_UNAUTHORIZED="0"
  export COOKIE_NAME="JSESSIONID"
  export FEATURE_DOWNLOAD_VIESTI_ENABLED="true"

  npx next build
  npx next start > frontend.log 2>&1 &

  info "Waiting for Frontend to be ready..."
  until curl -s http://localhost:3000/raportointi > /dev/null; do
    sleep 5
    info "Still waiting for Frontend..."
  done
  info "Frontend started."
}

function run_playwright_tests {
  info "Running Playwright tests..."
  cd "${repo}/playwright"
  npm_ci_if_needed
  npx playwright install chromium
  npx playwright install-deps chromium
  npx playwright test
}

main "$@"
