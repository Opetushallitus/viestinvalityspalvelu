#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../lib/common-functions.sh"

trap cleanup EXIT INT QUIT TERM

backend_pid=""
readonly backend_port=8089
readonly frontend_host="http://localhost:${backend_port}"

function main {
  select_java_version "21"
  init_nodejs

  cd "${repo}"

  if is_running_on_ci; then
    docker compose up -d
  fi

  wait_for_container_to_be_healthy viestinvalitys-test-postgres
  wait_for_container_to_be_healthy viestinvalitys-keycloak

  build_ui
  start_backend
  run_playwright_tests
}

function build_ui {
  info "Building viestinvalitys-ui into the backend static resources"
  cd "${repo}/viestinvalitys-ui"
  npm_ci_if_needed
  npm run build
}

function start_backend {
  info "Building viestinvalitys-service"
  cd "${repo}/viestinvalitys-service"
  if is_running_on_codebuild; then
    ../mvnw --batch-mode package -DskipTests -s "${repo}/codebuild-mvn-settings.xml"
  else
    ../mvnw --batch-mode package -DskipTests
  fi

  info "Starting viestinvalitys-service on port ${backend_port} against the test database"
  java -Dspring.profiles.active=local -jar target/viestinvalitys-service-*.jar \
    --server.port="${backend_port}" \
    --spring.datasource.url="jdbc:postgresql://localhost:5433/viestinvalityspalvelu" \
    --cas.service="${frontend_host}/viestinvalityspalvelu" \
    --raportointi.login-success-url="${frontend_host}/viestinvalityspalvelu" \
    > viestinvalitys-service-e2e.log 2>&1 &
  backend_pid=$!

  info "Waiting for viestinvalitys-service to be ready..."
  retry_until_seconds_passed 300 is_backend_ready
  info "viestinvalitys-service started."
}

function is_backend_ready {
  curl -sf "${frontend_host}/viestinvalityspalvelu/actuator/health" > /dev/null
}

function run_playwright_tests {
  info "Running Playwright tests..."
  cd "${repo}/playwright-viestinvalitys-ui"
  npm_ci_if_needed
  npx playwright install chromium
  npx playwright install-deps chromium
  FRONTEND_HOST="${frontend_host}" npx playwright test
}

function cleanup {
  info "Cleaning up..."
  if [ -n "${backend_pid}" ]; then
    kill "${backend_pid}" 2>/dev/null || true
    wait "${backend_pid}" 2>/dev/null || true
  fi
  if is_running_on_ci; then
    cd "${repo}"
    docker compose down
  fi
}

main "$@"
