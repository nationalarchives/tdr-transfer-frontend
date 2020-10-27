FROM openjdk:16-alpine
WORKDIR play
COPY target/universal/tdr-transfer-frontend*.zip .
RUN apk update && apk add bash unzip && unzip -qq tdr-transfer-frontend-*.zip
CMD  tdr-transfer-frontend-*/bin/tdr-transfer-frontend \
                        -Dplay.http.secret.key=$PLAY_SECRET_KEY \
                        -Dconfig.resource=application.$ENVIRONMENT.conf \
                        -Dplay.cache.redis.host=$REDIS_HOST \
                        -Dauth.secret=$AUTH_SECRET
