#!/usr/bin/env bash

set -eo pipefail

if [ $# == 0  ] || [ $# -gt 3 ] 
then
    echo 'please provide 1-3 arguments. Use -h or --help for usage information.'
    exit 0
fi

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -h | --help | help )
    echo '''
Usage: deploy.sh [-h] [-d] environment deploy/build/loadup/loaddown

Light weight version of cdk.sh in cloud-base 

positional arguments:
  deploy                builds and deploys the stack to target environment, environment must be supplied.
  build                 only builds the Lambda & synthesizes CDK (useful when developing)
  loadup                create load testing instance, environment must be supplied.
  loaddown              destroy load testing instance, environment must be supplied.
  environment           Environment name (e.g. pallero)

optional arguments:
  -h, --help            Show this help message and exit
  -d, --dependencies    Clean and install dependencies before deployment (i.e. run npm ci)
  '''
    exit 0
    ;;

    loadup)
    loadup="true"
    shift
    ;;

    loaddown)
    loaddown="true"
    shift
    ;;

    -d | --dependencies)
    dependencies="true"
    shift
    ;;

    build)
    build="true"
    shift
    ;;

    deploy)
    deploy="true"
    shift
    ;;

    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done

git_root=$(git rev-parse --show-toplevel)

if [[ "${loadup}" == "true" ]]; then
    environment=${POSITIONAL[~-1]}
    if [[ "${environment}" =~ ^(sade)$ ]]; then
      echo "Let's not deploy load testing instance to production account"
      exit
    fi
    echo "Deploying load testing instance"
    cd "${git_root}/cdk/"
    aws-vault exec oph-dev -- cdk deploy LoadtestStack --require-approval never -c "environment=$environment"
fi

if [[ "${loaddown}" == "true" ]]; then
    environment=${POSITIONAL[~-1]}
    echo "Destroying load testing instance"
    cd "${git_root}/cdk/"
    aws-vault exec oph-dev -- cdk destroy LoadtestStack -f -c "environment=$environment"
fi

if [[ "${build}" == "true" ]]; then
    echo "Building Lambda code and synthesizing CDK template"
    npx cdk synth
fi

if [[ -n "${dependencies}" ]]; then
    echo "Installing CDK dependencies.."
    cd "${git_root}/cdk/" && npm ci
    echo "Installing app dependencies.."
    cd "${git_root}/app/" && npm ci
fi

if [[ "${build}" == "true" ]]; then
    echo "Building Lambda code and synthesizing CDK template"
    npx cdk synth
fi

if [[ "${deploy}" == "true" ]]; then
    environment=${POSITIONAL[~-1]}
    ## Profiles are defined in user's .aws/config
    if [[ "${environment}" =~ ^(sade)$ ]]; then
        aws_profile="oph-prod"
    elif [[ "${environment}" =~ ^(untuva)$ ]]; then
        aws_profile="oph-dev"
    elif [[ "${environment}" =~ ^(hahtuva)$ ]]; then
        aws_profile="oph-dev"
    elif [[ "${environment}" =~ ^(pallero)$ ]]; then
        aws_profile="oph-dev"
    else
        echo "Unknown environment: ${environment}"
        exit 0
    fi

   echo "Building Lambda code, synhesizing CDK code and deploying to environment: $environment"
   cd "${git_root}/cdk/"
   aws-vault exec $aws_profile -- cdk deploy ViestinValitysStack PersistenssiStack -c "environment=$environment"
fi
