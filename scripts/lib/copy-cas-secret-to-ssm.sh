#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

readonly USERNAME_PARAM="/viestinvalitys/JarjestelmatunnusCasUsername"
readonly PASSWORD_PARAM="/viestinvalitys/JarjestelmatunnusCasPassword"

function main {
  local -r env=$(parse_env_from_script_name)
  require_command jq

  case "${env}" in
    "hahtuva" | "dev" | "qa" | "prod")
      copy_cas_secret_to_ssm "${env}"
      ;;
    *)
      fatal "Unknown env ${env}"
      ;;
  esac
}

function copy_cas_secret_to_ssm {
  local -r env="$1"
  export_aws_credentials "${env}"

  info "Reading cas-secret from Secrets Manager in ${env}"
  local -r secret_json="$(aws secretsmanager get-secret-value \
    --secret-id "cas-secret" \
    --query "SecretString" \
    --output text)"
  local -r username="$(echo "${secret_json}" | jq -r ".username")"
  local -r password="$(echo "${secret_json}" | jq -r ".password")"

  if [ -z "${username}" ] || [ "${username}" = "null" ]; then
    fatal "cas-secret has no username field"
  fi
  if [ -z "${password}" ] || [ "${password}" = "null" ]; then
    fatal "cas-secret has no password field"
  fi

  info "Upserting SSM parameter ${USERNAME_PARAM} in ${env}"
  aws ssm put-parameter \
    --name "${USERNAME_PARAM}" \
    --type "SecureString" \
    --value "${username}" \
    --overwrite

  info "Upserting SSM parameter ${PASSWORD_PARAM} in ${env}"
  aws ssm put-parameter \
    --name "${PASSWORD_PARAM}" \
    --type "SecureString" \
    --value "${password}" \
    --overwrite

  info "cas-secret copied to SSM parameters in ${env}"
}

main "$@"
