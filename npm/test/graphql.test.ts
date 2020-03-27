import { GraphqlClient } from "../src/graphql"
jest.mock("apollo-boost")
jest.mock("../src/auth")
import {
  ApolloClient,
  QueryOptions,
  ApolloQueryResult,
  NetworkStatus,
  MutationOptions,
  FetchResult
} from "apollo-boost"
import { GraphQLError } from "graphql"
import { KeycloakInstance } from "keycloak-js"

type IMockData = { [index: string]: string } | null
type TMockVariables = string

const queryOptions: Omit<ApolloQueryResult<IMockData>, "data"> = {
  loading: false,
  networkStatus: NetworkStatus.loading,
  stale: false
}

class MockApolloClientSuccess {
  query: (
    options: QueryOptions<TMockVariables>
  ) => Promise<ApolloQueryResult<IMockData>> = async (
    _: QueryOptions<TMockVariables>
  ) => {
    const data: IMockData = { data: "expectedData" }
    return { data, ...queryOptions }
  }

  mutate: (
    options: MutationOptions<TMockVariables>
  ) => Promise<FetchResult<IMockData>> = async (
    _: MutationOptions<TMockVariables>
  ) => {
    const data: IMockData = { data: "expectedData" }
    return { data }
  }
}

class MockApolloClientFailure {
  query: (
    options: QueryOptions<TMockVariables>
  ) => Promise<ApolloQueryResult<IMockData>> = async (
    _: QueryOptions<TMockVariables>
  ) => {
    return {
      data: null,
      errors: [new GraphQLError("error")],
      ...queryOptions
    }
  }
  mutate: (
    options: MutationOptions<TMockVariables>
  ) => Promise<FetchResult<IMockData>> = async (
    _: MutationOptions<TMockVariables>
  ) => {
    return { errors: [new GraphQLError("error")] }
  }
}
beforeEach(() => jest.resetModules())

const mockKeycloak: KeycloakInstance<"native"> = {
  init: jest.fn(),
  login: jest.fn(),
  logout: jest.fn(),
  register: jest.fn(),
  accountManagement: jest.fn(),
  createLoginUrl: jest.fn(),
  createLogoutUrl: jest.fn(),
  createRegisterUrl: jest.fn(),
  createAccountUrl: jest.fn(),
  isTokenExpired: jest.fn(),
  updateToken: jest.fn(),
  clearToken: jest.fn(),
  hasRealmRole: jest.fn(),
  hasResourceRole: jest.fn(),
  loadUserInfo: jest.fn(),
  loadUserProfile: jest.fn(),
  token: "fake-auth-token"
}

const mockSuccess: () => void = () => {
  const mock = ApolloClient as jest.Mock
  mock.mockImplementation(() => {
    return new MockApolloClientSuccess()
  })
}

const mockFailure: () => void = () => {
  const mock = ApolloClient as jest.Mock
  mock.mockImplementation(() => {
    return new MockApolloClientFailure()
  })
}

test("Returns the correct data for a query", async () => {
  mockSuccess()
  const client = new GraphqlClient("test", mockKeycloak)
  const result = await client.query<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.data!.data).toEqual("expectedData")
})

test("Returns the correct data for a mutation", async () => {
  mockSuccess()
  const client = new GraphqlClient("test", mockKeycloak)
  const result = await client.mutation<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.data!.data).toEqual("expectedData")
})

test("Returns errors if the query was not successful", async () => {
  mockFailure()
  const client = new GraphqlClient("test", mockKeycloak)
  const result = await client.query<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.errors).toHaveLength(1)
  expect(result.errors![0].message).toBe("error")
})

test("Returns errors if the mutation was not successful", async () => {
  mockFailure()
  const client = new GraphqlClient("test", mockKeycloak)
  const result = await client.mutation<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.errors).toHaveLength(1)
  expect(result.errors![0].message).toBe("error")
})

test("Calls refresh if the token for the query has expired", async () => {
  const refreshToken = mockKeycloak.updateToken as jest.MockedFunction<
    (minValidity: number) => Promise<boolean>
  >
  const isTokenExpired = mockKeycloak.isTokenExpired as jest.MockedFunction<
    (minValidity: number) => boolean
  >
  isTokenExpired.mockImplementation(_ => true)

  const client = new GraphqlClient("test", mockKeycloak)
  await client.query<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(refreshToken).toHaveBeenCalled()
})

test("Calls refresh if the token for the mutation has expired", async () => {
  const refreshToken = mockKeycloak.updateToken as jest.MockedFunction<
    (minValidity: number) => Promise<boolean>
  >
  const isTokenExpired = mockKeycloak.isTokenExpired as jest.MockedFunction<
    (minValidity: number) => boolean
  >
  isTokenExpired.mockImplementation(_ => true)

  const client = new GraphqlClient("test", mockKeycloak)
  await client.mutation<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(refreshToken).toHaveBeenCalled()
})
