module.exports = {
  preset: "ts-jest",
  moduleNameMapper: {
    "govuk-frontend": "<rootDir>/src/govuk-frontend.d.ts"
  },
  testEnvironment: 'jsdom',
  moduleFileExtensions: ['js', 'mjs', 'cjs', 'jsx', 'ts', 'tsx', 'json', 'node', 'd.ts'],
  globals: {
    TDR_IDENTITY_PROVIDER_NAME: "TEST_AUTH_URL",
    TDR_IDENTITY_POOL_ID: "TEST_IDENTITY_POOL",
    API_URL: true,
    STAGE: "stage",
    REGION: "region"
  },
}
