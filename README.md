# tdr-transfer-frontend
Repository for TDR transfer code

## Running locally

* Start the auth server

`docker run -d  --name keycloak -p 8080:8080 nationalarchives/tdr-auth-server:intg`

* Set up a [realm](https://www.keycloak.org/docs/latest/getting_started/index.html#creating-a-realm-and-user)

* Set up a [client](https://www.keycloak.org/docs/latest/server_admin/#oidc-clients)

* Generate a [secret](https://www.keycloak.org/docs/latest/server_admin/#_client-credentials)

* Set the secret in auth.secret in [application.conf](conf/application.conf)

* Start redis locally

`docker run -d --name redis -p 6379:6379 redis`

* Start the application using `sbt run`

## Notes
* Each environment has its own secret for the auth server. These cannot be generated inside aws in any way and so it's difficult to get them into the terraform scripts. At the moment, these are stored in a parameter store variable called /${env}/auth/secret although this may change.