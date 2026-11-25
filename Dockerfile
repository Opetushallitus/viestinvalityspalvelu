FROM amazoncorretto:21-al2023@sha256:fe9b50d12d33a499fb382d47370017a8dee73a2ab80ea6d5aa9a30ee72c82caa AS build

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

FROM amazoncorretto:21-al2023@sha256:fe9b50d12d33a499fb382d47370017a8dee73a2ab80ea6d5aa9a30ee72c82caa
WORKDIR /app

COPY --from=build /app/viestinvalitys-service/target/viestinvalitys-service-1.0.0.jar application.jar

COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -Dspring.config.additional-location="classpath:/config/${ENV}.properties" -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
