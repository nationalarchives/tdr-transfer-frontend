# tdr-transfer-frontend
Repository for TDR transfer code

## Running locally

There are two ways to develop this project:

- Frontend development only, using the AWS integration environment for everything else
- Full stack local development, using a local dev copy of the API, Keycloak, etc.

### Prerequisites

Regardless of how you set up the development environment, you will need:

- IntelliJ with the Scala plugin (or equivalent Scala dev environment)
- Docker

### Frontend development only

Follow these instructions if you want to make changes to the frontend application without needing to set up a full
development environment for the other TDR services.

- Run redis using Docker:
  ```
  docker run -d --name redis -p 6379:6379 redis
  ```
- Get the TDR client secret for integration by logging into the [Keycloak admin site][auth-admin], and going to Clients,
  then "tdr", then Credentials, and copying the UUID in the Secret field
- In IntelliJ, create a new sbt run configuration:
  - Set the Tasks parameter to `run`
  - Configure environment variables:
    - AUTH_URL=https://auth.tdr-integration.nationalarchives.gov.uk
    - AUTH_SECRET=\<insert the secret for the tdr client that you copied above\>
    - API_URL=https://api.tdr-integration.nationalarchives.gov.uk/graphql
    - TDR_IDENTITY_POOL_ID=secret-from-/mgmt/identitypoolid_intg in the management account
- Follow the Static Assets steps below to build the CSS and JS
- Run the project from IntelliJ
- Visit `http://localhost:9000`

When you log into the site, you will need to log in as a user from the Integration environment.

[auth-admin]: https://auth.tdr-integration.nationalarchives.gov.uk/auth/admin

### Full stack local development

Follow these instructions if you want to make changes to the API, database and/or auth system at the same time as
updating the frontend.

#### Local auth server

-  Start the auth server
  ```
  docker run -d --name keycloak -p 8081:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin -e KEYCLOAK_IMPORT=/tmp/tdr-realm.json -e CLIENT_SECRET=[some value] -e BACKEND_CHECKS_CLIENT_SECRET=[some value] -e REALM_ADMIN_CLIENT_SECRET=[some value] -e KEYCLOAK_CONFIGURATION_PROPERTIES=intg_properties.json nationalarchives/tdr-auth-server:intg
  ```
- Go to `http://localhost:8081/auth/admin` and log in with username *admin* and password *admin*.  
- Create a transferring body user:
  - Click Users in the menu on the left
  - Click Add User
  - Set a Username (all the other fields are optional) and click Save
  - Click the Groups tab
  - In the "Available Groups" box select the `Mock 1 Department` sub-group, and click `Join`
    The `transferring_body_user/Mock 1 Department` group should now appear in the "Group Membership" box
  - Click the Credentials tab
  - Set a non-temporary password for the user
  - For full details about managing transferring body users and transferring body groups see: [Tdr User Administrator Manual](https://github.com/nationalarchives/tdr-dev-documentation/blob/master/tdr-admins/tdr-user-administrator.md)
- Set AUTH_SECRET as an environment variable in IntelliJ and/or the command line (depending on how you plan to run the
  frontend project) with the secret as its value:
  ```
  AUTH_SECRET=[CLIENT_SECRET value from the docker run command]
  ```

#### Local API

Clone and run the [tdr-consignment-api] project.

[tdr-consignment-api]: https://github.com/nationalarchives/tdr-consignment-api

#### Local S3 emulator

Run an [S3 ninja] Docker container, specifying a local directory in which to save the files:

```
docker run -d -p 9444:9000 -v <some directory path>:/home/sirius/data --name=s3ninja scireum/s3-ninja:6.4
```

Set the `S3_ENDPOINT_OVERRIDE` environment variable to the upload URL of the emulator, in this case
`http://localhost:9444/s3`.

Requests to S3 ninja need to be authenticated with a Cognito token. To emulate this endpoint, clone the
[tdr-local-aws] project and follow the instructions there to run the `FakeCognitoServer` application.

Set the `COGNITO_ENDPOINT_OVERRIDE` environment variable to the URL of the fake Cognito token provider.
The default is `http://localhost:4600`.

[S3 ninja]: https://s3ninja.net/
[tdr-local-aws]: https://github.com/nationalarchives/tdr-local-aws

#### Local backend checks

Follow the instructions in [tdr-local-aws] to run the `FakeBackendChecker` application, making sure you set
the environment variable for the monitored folder to the same path as the mount directory that you set in
the `docker run` command when you started the S3 ninja container. This lets the fake backend checker detect
and scan files as they are uploaded to the S3 emulator.

#### Frontend project

* Start redis locally.

    `docker run -d --name redis -p 6379:6379 redis`
* Run the frontend with `sbt run` from IntelliJ or the command line
* Visit `http://localhost:9000`

### Static assets

* If npm is not installed install [nvm](https://github.com/nvm-sh/nvm) in root directory.

* Once nvm is installed:
    `nvm install 14.9`
    
* `cd` into tdr-transfer-frontend in terminal

* run  `npm install` then `npm run build`

If you're working on the Sass files and just want to regenerate the CSS without rebuilding all the JavaScript code, you
can run `npm run sass-watch` to monitor and rebuild the CSS files, or run `npm run build-css` to run a single build.

To run the sass linter (stylelint) before commits, run the following command: from the npm folder `npx stylelint **/*.scss`

Full details of stylelint are available here: https://stylelint.io/

## Generated GraphQL classes

There is a separate repository which contains the generated case classes needed to query the consignment API. 
These classes will be needed by more than one project which is why they are in a separate project.
If you need to add a new query:

* Run `git clone https://github.com/nationalarchives/tdr-generated-graphql.git`
* Add the new query to the `src/main/graphql` directory
* Run `sbt package publishLocal`
* Set the version for `tdr-generated-graphql` in this projects build.sbt to be the snapshot version.

## Notes
* Each environment has its own secret for the auth server. These cannot be generated inside aws in any way and so it's difficult to get them into the terraform scripts. At the moment, these are stored in a parameter store variable called /${env}/auth/secret although this may change.
