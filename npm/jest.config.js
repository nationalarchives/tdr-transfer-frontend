module.exports = {
  preset: "ts-jest",
  moduleNameMapper: {
    "govuk-frontend": "<rootDir>/src/govuk-frontend.d.ts",
    "@nationalarchives/tdr-components": "<rootDir>/node_modules/@nationalarchives/tdr-components/dist",
    '^sinon$': require.resolve('sinon')
  },
  testEnvironment: 'jsdom',
  globals: {
    TDR_IDENTITY_PROVIDER_NAME: "TEST_AUTH_URL",
    TDR_IDENTITY_POOL_ID: "TEST_IDENTITY_POOL",
    API_URL: true,
    STAGE: "stage",
    REGION: "region"
  },
  transform: {
    "^.+\\.[j]sx?$": "babel-jest"
  },
  transformIgnorePatterns: ["<rootDir>/node_modules/(?!keycloak-js)/"],
}
