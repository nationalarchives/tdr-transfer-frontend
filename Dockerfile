FROM openjdk:8-slim
WORKDIR play
COPY target/universal/tdr-transfer-frontend*.zip .
RUN apt-get update && apt-get install unzip && unzip -qq tdr-transfer-frontend-*.zip
CMD  tdr-transfer-frontend-*/bin/tdr-transfer-frontend \
                        -Dplay.http.secret.key=$PLAY_SECRET_KEY \
                        -Dconfig.resource=application.$ENVIRONMENT.conf \
                        -Dredis.password=$REDIS_PASSWORD \
                        -Dredis.host=$REDIS_HOST
