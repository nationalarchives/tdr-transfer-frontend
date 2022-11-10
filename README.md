# tdr-transfer-frontend
Repository for TDR transfer code

## Running locally

There are two ways to develop this project:

- Frontend development only, using the AWS integration environment for everything else. This is the default
- Full stack local development, using a local dev copy of the API, Keycloak, etc

### Prerequisites

Regardless of how you set up the development environment, you will need:

- IntelliJ with the Scala plugin (or equivalent Scala dev environment)
- Docker

### Frontend development only

Follow these instructions if you want to make changes to the frontend application without needing to set up a full
development environment for the other TDR services.

- Run Redis using Docker:
  ```
  docker run -d --name redis -p 6379:6379 redis
  ```
- If you don't already have an admin user account for the [Integration Keycloak][auth-admin] site, ask another member of
  the TDR development team to create one for you
- Look up the integration environment variables in the AWS Integration account:
  - Keycloak client secret
    - In the AWS console:
      - Go the Systems Manager service
      - Go the Parameter Store in the left-hand menu
      - Find the `/intg/keycloak/client/secret` parameter
      - Copy the parameter's value
    - With the AWS CLI:
      - Run:
        ```
        aws ssm get-parameter --name "/intg/keycloak/client/secret" --with-decryption
        ```
      - Copy the `Value` from the object returned 
- In IntelliJ, create a new sbt run configuration:
  - Set the Tasks parameter to `run`
  - Configure the environment variables:
    - AUTH_SECRET=\<the secret for the Keycloak client that you copied above\>
- Follow the Static Assets steps below to build the CSS and JS
- Run the project from IntelliJ
- Visit `http://localhost:9000`

When you log into the site, you will need to log in as a user from the Integration environment.

#### Troubleshooting

### java.io.IOException

If you encounter the following error when trying to run in Intellij:

`java.io.IOException: Cannot run program "npm" (in directory "/home/{username}/Tdr/tdr-transfer-frontend/npm"): error=2, No such file or directory`

This is because NVM installs npm in a non-standard location.

To resolve this, you need to create a sym link with the install version of npm. Run the following commands:

- `sudo ln -s /home/{username}/.nvm/versions/node/{version}/bin/npm /usr/bin/npm`; and
-  `sudo ln -s /home/{username}/.nvm/versions/node/{version}/bin/node /usr/bin/node`

[auth-admin]: https://auth.tdr-integration.nationalarchives.gov.uk/auth/admin

### Incognito mode
If you are using the AWS integration environment, uploads to S3 go through upload.tdr-integration.nationalarchives.gov.uk If you try to upload using incognito/private browsing, the upload will fail. This is because the browser blocks third party cookies by default in incognito mode. To allow the cookies, you can add an exception for the url *upload.tdr-integration.nationalarchives.gov.uk*

[Instructions for Chrome](https://support.google.com/chrome/answer/95647?hl=en-GB&co=GENIE.Platform%3DDesktop#zippy=%2Callow-or-block-cookies-for-a-specific-site)

[Instructions for firefox](https://support.mozilla.org/en-US/kb/third-party-cookies-firefox-tracking-protection#w_enable-third-party-cookies-for-specific-sites)

### Firefox state partitioning
You may get this error when running locally in Firefox

`Partitioned cookie or storage access was provided to "https://auth..." because it is loaded in the third party context and dynamic state partitioning is enabled.`  

Because the domains are different, Firefox blocks the cookie request. The request is the way Keycloak JS works so we can't stop it. You can disable the setting for these sites though.

Go to `about:config` in the Firefox URL bar. Select the `privacy.restrict3rdpartystorage.skip_list` option and change the setting to `http://localhost:9000,https://auth.tdr-integration.nationalarchives.gov.uk`

This should allow the Keycloak cookies on localhost.

### Full stack local development

Follow these instructions if you want to make changes to the API, database and/or auth system at the same time as
updating the frontend.

#### Local auth server

-  Log into Docker with credentials from ECR and start the auth server. This will need AWS CLI version 2 to work.
  ```
  export MANAGEMENT_ACCOUNT=management_account_number
  aws ecr get-login-password --region eu-west-2 --profile management | docker login --username AWS --password-stdin $MANAGEMENT_ACCOUNT.dkr.ecr.eu-west-2.amazonaws.com
  docker run -d --name keycloak -p 8081:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin -e KEYCLOAK_IMPORT=/tmp/tdr-realm.json -e CLIENT_SECRET=[some value] -e BACKEND_CHECKS_CLIENT_SECRET=[some value] -e REALM_ADMIN_CLIENT_SECRET=[some value] -e KEYCLOAK_CONFIGURATION_PROPERTIES=intg_properties.json -e USER_ADMIN_CLIENT_SECRET=[some value] -e DB_VENDOR=h2 $MANAGEMENT_ACCOUNT.dkr.ecr.eu-west-2.amazonaws.com/auth-server:intg
  ```
- Go to `http://localhost:8081/auth/admin` and log in with username *admin* and password *admin*.  
- Create a transferring body user:
  - Click Users in the menu on the left
  - Click Add User
  - Set a Username (all the other fields are optional) and click Save
  - Click the Groups tab
  - In the "Available Groups" box select the `Mock 1 Department` sub-group, and click `Join`
    The `transferring_body_user/Mock 1 Department` group should now appear in the "Group Membership" box
  - In the "Available Groups" box select the relevant "user type" sub-type depending on the type of user required, and click `Join`
    The `user_type/standard_user` or `user_type/judgment_user` group should now appear in the "Group Membership" box depending on which  sub-group was selected
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

Create a new empty directory that the S3 emulator will save files in.

**If you are running Linux**, change the owner of this directory to user 2000, to give the user in the S3 ninja Docker
container permission to save files there. Do not do this on a Mac, because Docker handles file permissions differently
on Linux and macOS, and this step will prevent uploads from working.

```
sudo chown 2000:2000 /your/new/upload/directory
```

Run an [S3 ninja] Docker container, specifying a local directory in which to save the files:

```
docker run -d -p 9444:9000 -v /your/new/upload/directory:/home/sirius/data --name=s3ninja scireum/s3-ninja:6.4
```

Visit http://localhost:9444/ui and check you can create a bucket and upload a test file through the S3 ninja UI. Check
that the file appears in the folder that you mounted.

[S3 ninja]: https://s3ninja.net/
[tdr-local-aws]: https://github.com/nationalarchives/tdr-local-aws

#### Local backend checks

Follow the instructions in [tdr-local-aws] to run the `FakeBackendChecker` application, making sure you set
the environment variable for the monitored folder to the same path as the mount directory that you set in
the `docker run` command when you started the S3 ninja container. This lets the fake backend checker detect
and scan files as they are uploaded to the S3 emulator.

#### Frontend project

* Start Redis locally.

    `docker run -d --name redis -p 6379:6379 redis`
* Ensure you have set the `AUTH_SECRET` environment variable, as described above. Set it in the command line or in the
  IntelliJ run configuration
* Run the frontend, specifying the local full stack configuration file:
  ```
  sbt -Dconfig.file=conf/application.local-full-stack.conf run
  ```
  or set the IntelliJ SBT run configuration to `-Dconfig.file=conf/application.local-full-stack.conf run`
* Visit `http://localhost:9000`

### Static assets

**Note:** The TDR static assets are used by the TDR Auth Server. When updating the static assets, including the Sass, ensure that any changes are also implemented in the tdr-auth-server repo: https://github.com/nationalarchives/tdr-auth-server
* This includes any changes to the `.stylelintrc.json`

* If npm is not installed install [nvm](https://github.com/nvm-sh/nvm) in root directory.

* Once nvm is installed:
    `nvm install 14.9`
    
* `cd` into tdr-transfer-frontend/npm in terminal

* run  `npm install` then `npm run build`

If you're working on the Sass files and just want to regenerate the CSS without rebuilding all the JavaScript code, you
can run `npm run sass-watch` to monitor and rebuild the CSS files, or run `npm run build-css` to run a single build.

To run the Sass linter (stylelint) before commits, run the following command: from the npm folder `npx stylelint **/*.scss`

Full details of stylelint are available here: https://stylelint.io/

### Running unit tests

We have two types of unit tests: Spec tests for our Scala code, which are located within the `test` directory and Jest tests (for our Typescript code) which are located in the `npm > test` directory

#### Running Spec tests
IntelliJ

In order to run Spec tests via IntelliJ, you could either: right-click on a Spec file and select "Run {Test name}" or
go into the Spec file and at the top of the test, click the green play button on the left. You can also run individual tests.

Via CLI

In order to run the tests via CLI, you just navigate to the `tdr-transfer-frontend` directory and:

* to run all tests - run `sbt test`
* to run all tests of one Spec file - run `sbt "testOnly *{class name}"`
  * e.g `sbt "testOnly *SeriesDetailsControllerSpec"`
* to run a specific test of a Spec file - run `sbt "testOnly *{class name} -- -t \"should {test name}\"`
  * e.g. `sbt "testOnly *SeriesDetailsControllerSpec -- -t \"should display errors when an invalid\""`
* to run a test or multiple tests of a Spec file that contain a word/words in their name - run `sbt "testOnly *SeriesDetailsControllerSpec -- -z \"{word/words in test name}\""`
  * e.g `sbt "testOnly *SeriesDetailsControllerSpec -- -z \"display errors\""`

[See here](https://www.scalatest.org/user_guide/using_the_runner) for more information about arguments you can use

#### Running Jest tests

IntelliJ

In order to run Jest tests via IntelliJ, you could either: right-click on a test.ts file and select "Run {Test name}" to run all or
go into the test file and click one of the green play buttons (for the test you want to run) on the left to run one.

Via CLI

In order to run the tests via CLI, you just navigate to the `tdr-transfer-frontend/npm` directory and:

* to run all tests - run `npm test`
* to run all tests of one test file - run `npm test -- {file name and extension}"`
  * e.g `npm test-- clientfileextractmetadata.test.ts"`
* to run a specific test of a Spec file - run `npm test -- {file name and extension} -t "{test name}"`
  * e.g. `npm test -- clientfileextractmetadata.test.ts -t "extract function returns list of client"`
* to run a test or multiple tests of a test file that contain a word/words in their name - run `npm test -- {file name and extension} -t "{words in test names}"`
  * e.g. `npm test -- clientfileextractmetadata.test.ts -t "extract"`
* to run tests in multiple test files that contain a word/words in their name - run `npm test -- -t "{word/words in test names}"`
  * e.g. `npm test -- -t "throw"`

Bonus: Jest test Coverage - In order to see if how much of your code is covered by the Jest tests, run `npm test -- --coverage --verbose`

### Pre-commit checks

We have pre-commit checks that will run Scalafmt and run the unit tests associated with the files you've edited,
only on Scala/Typescript files, if you have any staged. In order to run this script, you need to install Husky; to do this, run

`npx husky install`

## Generated GraphQL classes

There is a separate repository which contains the generated case classes needed to query the Consignment API.
These classes will be needed by more than one project which is why they are in a separate project.
If you need to add a new query:

* Run `git clone https://github.com/nationalarchives/tdr-generated-graphql.git`
* Add the new query to the `src/main/graphql` directory
* Run `sbt package publishLocal`
* Set the version for `tdr-generated-graphql` in this projects build.sbt to be the snapshot version.

## Notes
* Each environment has its own secret for the auth server. These cannot be generated inside AWS in any way and so it's difficult to get them into the Terraform scripts. At the moment, these are stored in a parameter store variable called /${env}/auth/secret although this may change.
