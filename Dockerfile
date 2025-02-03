FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
RUN addgroup --system frontendgroup && adduser --system frontenduser -G frontendgroup
WORKDIR play
COPY target/universal/tdr-transfer-frontend*.zip .
RUN apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && apk add bash unzip && \
    apk add openjdk17 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community && \
    unzip -qq tdr-transfer-frontend-*.zip
ADD https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v1.32.6/aws-opentelemetry-agent.jar /opt/aws-opentelemetry-agent.jar
ENV JAVA_TOOL_OPTIONS=-javaagent:/opt/aws-opentelemetry-agent.jar
RUN chown -R frontenduser /play /opt/aws-opentelemetry-agent.jar

USER frontenduser

CMD  tdr-transfer-frontend-*/bin/tdr-transfer-frontend \
                        -Dplay.http.secret.key=$PLAY_SECRET_KEY \
                        -Dconfig.resource=application.$ENVIRONMENT.conf \
                        -Dplay.cache.redis.host=$REDIS_HOST \
                        -Dauth.secret=$AUTH_SECRET
