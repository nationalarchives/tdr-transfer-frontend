FROM openjdk:8-slim
WORKDIR play
COPY target/universal/tdr-transfer-frontend-1.0-SNAPSHOT.zip .
RUN apt-get update && apt-get install unzip && unzip -qq tdr-transfer-frontend-1.0-SNAPSHOT.zip
CMD printenv && AWS_ACCESS_KEY_ID=$ACCESS_KEY_ID \
      AWS_SECRET_ACCESS_KEY=$SECRET_ACCESS_KEY \
      transfer-digital-records-1.0.1-SNAPSHOT/bin/transfer-digital-records \
      -Dplay.http.secret.key=$PLAY_SECRET_KEY \
      -DTDR_GRAPHQL_URI=$TDR_GRAPHQL_URI \
      -DENVIRONMENT=$ENVIRONMENT \
      -DSENDGRID_API_KEY=$SENDGRID_API_KEY \
      -Dhttp.port=9000
