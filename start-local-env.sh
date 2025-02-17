#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail -o xtrace
readonly repo="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function stop() {
  cd "$repo"
  docker compose down
}
trap stop EXIT

function main {
  cd "$repo"
  local -r session="organisaatio"
  tmux kill-session -t "$session" || true
  tmux start-server
  tmux new-session -d -s "$session"

  tmux select-pane -t 0
  tmux send-keys "cd $repo/integraatio/docker; docker compose down --volumes; docker compose up --force-recreate --renew-anon-volumes" C-m

  tmux splitw -v
  tmux select-pane -t 1
  tmux send-keys "$repo/scripts/run-backend.sh" C-m

  tmux splitw -h
  tmux select-pane -t 2
  tmux send-keys "$repo/scripts/run-raportointi.sh" C-m

  open "https://localhost:8080/login" # login to raportointi
  open "http://localhost:1080" # mailcatcher

  tmux attach-session -t "$session"
}

main "$@"
