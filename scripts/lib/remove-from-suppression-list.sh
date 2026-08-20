#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  local -r env=$(parse_env_from_script_name)
  local -r email_address="${1:-}"

  if [ -z "${email_address}" ]; then
    fatal "Usage: $(basename "$0") <email-address>"
  fi

  case "${env}" in
    "hahtuva" | "dev" | "qa" | "prod")
      remove_from_suppression_list "${env}" "${email_address}"
      ;;
    *)
      fatal "Unknown env ${env}"
      ;;
  esac
}

function remove_from_suppression_list {
  local -r env="$1"
  local -r email_address="$2"
  export_aws_credentials "${env}"

  info "Removing ${email_address} from SES suppression list in ${env}"
  aws sesv2 delete-suppressed-destination \
    --email-address "${email_address}"

  info "Removed ${email_address} from SES suppression list in ${env}"
}

main "$@"
