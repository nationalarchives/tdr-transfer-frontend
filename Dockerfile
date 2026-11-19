FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
RUN addgroup --system frontendgroup && adduser --system frontenduser -G frontendgroup
WORKDIR play
COPY target/universal/tdr-transfer-frontend*.zip .
RUN apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && apk add bash unzip && \
    wget -O /etc/apk/keys/amazoncorretto.rsa.pub https://apk.corretto.aws/amazoncorretto.rsa.pub && \
    echo "https://apk.corretto.aws/" >> /etc/apk/repositories && \
    apk update && apk add --no-cache 'amazon-corretto-21>=21.0.13.1.2' && \
    unzip -qq tdr-transfer-frontend-*.zip
RUN chown -R frontenduser /play

USER frontenduser

CMD  tdr-transfer-frontend-*/bin/tdr-transfer-frontend \
                        -Dplay.http.secret.key=$PLAY_SECRET_KEY \
                        -Dconfig.resource=application.$ENVIRONMENT.conf \
                        -Dplay.cache.redis.host=$REDIS_HOST \
                        -Dauth.secret=$AUTH_SECRET
