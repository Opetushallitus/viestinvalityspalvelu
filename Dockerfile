FROM amazoncorretto:21-al2023@sha256:3284a05cb82e9bceb4857f9d70788a6f72681d9070b550ae5f477b5e3dc81a5c AS build

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN dnf install -y git tar gzip \
    && dnf clean all

ENV NVM_DIR=/opt/nvm

WORKDIR /app

COPY .nvmrc ./
COPY scripts/lib/nvm.sh ${NVM_DIR}/nvm.sh

RUN source "$NVM_DIR/nvm.sh" --no-use \
  && nvm install


COPY . .

WORKDIR /app/viestinvalitys-ui
RUN source "$NVM_DIR/nvm.sh" --no-use \
    && nvm use \
    && npm ci \
    && npm run build

WORKDIR /app
RUN ./mvnw --batch-mode -f viestinvalitys-service/pom.xml clean package -s ./codebuild-mvn-settings.xml -DskipTests

FROM amazoncorretto:21-al2023@sha256:3284a05cb82e9bceb4857f9d70788a6f72681d9070b550ae5f477b5e3dc81a5c
WORKDIR /app

COPY --from=build /app/viestinvalitys-service/target/viestinvalitys-service-1.0.0.jar application.jar

COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -Dspring.config.additional-location="classpath:/config/${ENV}.properties" -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
