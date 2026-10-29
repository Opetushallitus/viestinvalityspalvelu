FROM maven:3.9.15-amazoncorretto-21-al2023@sha256:e3c1928769e2cd7df6ba55f0afb7c711a6e0a2b7b994e7b2b27316f19d893b0b AS build

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

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

FROM amazoncorretto:21-al2023@sha256:1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b
WORKDIR /app

COPY --from=build /app/viestinvalitys-service/target/viestinvalitys-service-1.0.0.jar application.jar

COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -Dspring.config.additional-location="classpath:/config/${ENV}.properties" -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
