# Constrói o JAR dentro da imagem, sem depender do target/ da máquina local.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S unescshare && adduser -S unescshare -G unescshare \
    && mkdir -p /var/lib/unescshare/torrents \
    && chown -R unescshare:unescshare /app /var/lib/unescshare

COPY --from=build --chown=unescshare:unescshare /workspace/target/unesc-share-0.0.1-SNAPSHOT.jar /app/app.jar

USER unescshare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
