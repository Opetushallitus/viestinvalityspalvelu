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
  start_viestinvalitys_service   # reporting + create/send API (8081) — owns the DB schema (Flyway)
  start_ui                       # new UI (3000)
  run_playwright_tests
}

function kill_background_processes {
  jobs -p | xargs kill || true
}

function stop_docker_containers {
  cd "${repo}"
  docker compose down --volumes
}

function start_docker_containers {
  info "Starting Docker containers..."
  cd "${repo}"
  docker compose down --volumes
  docker compose up --force-recreate --renew-anon-volumes -d postgres keycloak
  wait_for_container_to_be_healthy viestinvalitys-postgres
  wait_for_container_to_be_healthy viestinvalitys-keycloak
  info "Docker containers started."
}

function start_viestinvalitys_service {
  info "Building and starting viestinvalitys-service..."
  cd "${repo}/viestinvalitys-service"
  if is_running_on_codebuild; then
    ../mvnw --batch-mode clean install -DskipTests -s "${repo}/codebuild-mvn-settings.xml"
  else
    ../mvnw --batch-mode clean install -DskipTests
  fi

  ../mvnw -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local" spring-boot:run > viestinvalitys-service.log 2>&1 &

  info "Waiting for viestinvalitys-service to be ready..."
  retry_until_seconds_passed 300 is_service_ready
  info "viestinvalitys-service started."
}

function is_service_ready {
  curl -s http://localhost:8081/viestinvalityspalvelu/actuator/health > /dev/null
}

function start_ui {
  info "Starting viestinvalitys-ui..."
  cd "${repo}/viestinvalitys-ui"
  npm_ci_if_needed

  NODE_ENV=development npx webpack serve --mode development > frontend.log 2>&1 &

  info "Waiting for UI to be ready..."
  until curl -s http://localhost:3000/viestinvalityspalvelu/ > /dev/null; do
    sleep 5
    info "Still waiting for UI..."
  done
  info "UI started."
}

function run_playwright_tests {
  info "Running Playwright tests..."
  cd "${repo}/playwright-viestinvalitys-ui"
  npm_ci_if_needed
  npx playwright install chromium
  npx playwright install-deps chromium
  npx playwright test
}

main "$@"
