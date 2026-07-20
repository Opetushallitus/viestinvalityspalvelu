FROM maven:3.9.15-amazoncorretto-21-al2023@sha256:e3c1928769e2cd7df6ba55f0afb7c711a6e0a2b7b994e7b2b27316f19d893b0b AS build

RUN dnf install -y nodejs24 \
  && alternatives --install /usr/bin/node node /usr/bin/node-24 90 \
  && alternatives --install /usr/bin/npm npm /usr/bin/npm-24 90 \
  && alternatives --install /usr/bin/npx npx /usr/bin/npx-24 90

WORKDIR /app
COPY . .

WORKDIR /app/viestinvalitys-ui
RUN npm ci
RUN npm run build

WORKDIR /app
RUN ./mvnw --batch-mode -f viestinvalitys-service/pom.xml clean package -s ./codebuild-mvn-settings.xml -DskipTests

FROM amazoncorretto:21-al2023
WORKDIR /app

COPY --from=build /app/viestinvalitys-service/target/viestinvalitys-service-1.0.0.jar application.jar

COPY --chmod=755 <<"EOF" /app/entrypoint.sh
#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
exec java -Dspring.config.additional-location="classpath:/config/${ENV}.properties" -jar application.jar
EOF

ENTRYPOINT [ "/app/entrypoint.sh" ]
