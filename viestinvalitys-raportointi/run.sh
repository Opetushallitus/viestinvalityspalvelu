#!/bin/bash

export PORT=8080
export VIESTINTAPALVELU_URL=https://viestinvalitys.hahtuvaopintopolku.fi
export LOGIN_URL=https://viestinvalitys.hahtuvaopintopolku.fi/raportointi/login
export COOKIE_NAME=JSESSIONID

node server.js
