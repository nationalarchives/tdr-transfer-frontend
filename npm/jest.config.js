module.exports = {
  preset: "ts-jest",
  globals: {
    TDR_IDENTITY_PROVIDER_NAME: "TEST_AUTH_URL",
    TDR_IDENTITY_POOL_ID: "TEST_IDENTITY_POOL",
    API_URL: true,
    STAGE: "stage",
    REGION: "region",
    METADATA_UPLOAD_BATCH_SIZE: 3
  }
}
