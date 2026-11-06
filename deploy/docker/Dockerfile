FROM harbor.nori.local/library/eclipse-temurin:21-jre-jammy

ARG GIT_SHA
ARG APP_JAR_SHA256
ARG RUNTIME_CLASSPATH_SHA256
ARG MIGRATIONS_SHA256

USER root
RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 2000 aigw && useradd --uid 3000 --gid 2000 --no-create-home aigw
WORKDIR /app
COPY build/package/app.jar /app/app.jar
RUN printf '%s  /app/app.jar\n' "$APP_JAR_SHA256" | sha256sum --check --status \
    && printf '%s\n' "$GIT_SHA" | grep -Eq '^[0-9a-f]{40}$' \
    && printf '%s\n' "$RUNTIME_CLASSPATH_SHA256" | grep -Eq '^[0-9a-f]{64}$' \
    && printf '%s\n' "$MIGRATIONS_SHA256" | grep -Eq '^[0-9a-f]{64}$'
LABEL aigw.git-sha=$GIT_SHA \
      aigw.app-jar.sha256=$APP_JAR_SHA256 \
      aigw.runtime-classpath.sha256=$RUNTIME_CLASSPATH_SHA256 \
      aigw.migrations.sha256=$MIGRATIONS_SHA256

USER 3000:2000
# P15final의 safety 검사 main이 없으면 빌드가 실패해야 합니다. label은 이미지 digest 증거를 대체하지 않습니다.
RUN java -Dai.djl.offline=true \
    -Dloader.main=com.nori.ai.gateway.app.safety.NativeTokenizerOfflineCheck \
    -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dai.djl.offline=true -jar /app/app.jar"]
