#!/bin/bash

export PORT=8080

if [ $ENVIRONMENT_NAME = "pallero" ]; then
  export VIESTINTAPALVELU_URL="https://viestinvalitys.testiopintopolku.fi"
else
  export VIESTINTAPALVELU_URL="https://viestinvalitys.$ENVIRONMENT_NAME.fi"
fi
export LOGIN_URL="$VIESTINTAPALVELU_URL/raportointi/login"
export COOKIE_NAME=JSESSIONID

node server.js
