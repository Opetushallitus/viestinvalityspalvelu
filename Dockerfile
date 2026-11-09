FROM maven:3.9.16-amazoncorretto-21-al2023@sha256:efdf660ec74911594e2a7f642e177ffba21b741c8e848223bf41a6d0fc367692 AS build

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

FROM amazoncorretto:21-al2023@sha256:90100f194336ef2326f5efe1ee747395fd87d8210ad6d60463196aa0c4b0ad4e
WORKDIR /app

COPY --from=build /app/viestinvalitys-service/target/viestinvalitys-service-1.0.0.jar application.jar

COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -Dspring.config.additional-location="classpath:/config/${ENV}.properties" -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
