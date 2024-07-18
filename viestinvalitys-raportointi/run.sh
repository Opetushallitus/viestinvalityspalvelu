#!/bin/bash

export PORT=$PORT
export VIRKAILIJA_URL="https://virkailija.$OPINTOPOLKU_DOMAIN.fi"
export VIESTINTAPALVELU_URL="https://viestinvalitys.$OPINTOPOLKU_DOMAIN.fi"
export LOGIN_URL="$VIESTINTAPALVELU_URL/raportointi/login"
export COOKIE_NAME=JSESSIONID

node server.js
