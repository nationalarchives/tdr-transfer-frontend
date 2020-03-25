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
import { getToken } from "../src/auth"

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

const mockSuccess: () => void = () => {
  const token = getToken as jest.Mock
  token.mockImplementation(() => {
    return new Promise((resolve, _) => resolve("token"))
  })
  const mock = ApolloClient as jest.Mock
  mock.mockImplementation(() => {
    return new MockApolloClientSuccess()
  })
}

const mockFailure: () => void = () => {
  const token = getToken as jest.Mock
  token.mockImplementation(() => {
    return new Promise((resolve, _) => resolve("token"))
  })
  const mock = ApolloClient as jest.Mock
  mock.mockImplementation(() => {
    return new MockApolloClientFailure()
  })
}

test("Returns the correct data for a query", async () => {
  mockSuccess()
  const client = new GraphqlClient("test")
  const result = await client.query<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.data!.data).toEqual("expectedData")
})

test("Returns the correct data for a mutation", async () => {
  mockSuccess()
  const client = new GraphqlClient("test")
  const result = await client.mutation<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.data!.data).toEqual("expectedData")
})

test("Rejects the query if there is a problem getting the token", async () => {
  ;(getToken as jest.Mock).mockImplementation(() => {
    return new Promise((_, reject) => reject("Cannot fetch token"))
  })
  const client = new GraphqlClient("test")
  await expect(
    client.query({ definitions: [], kind: "Document" }, "")
  ).rejects.toStrictEqual(Error("Cannot fetch token"))
})

test("Rejects the mutation if there is a problem getting the token", async () => {
  ;(getToken as jest.Mock).mockImplementation(() => {
    return new Promise((_, reject) => reject("Cannot fetch token"))
  })
  const client = new GraphqlClient("test")
  await expect(
    client.mutation({ definitions: [], kind: "Document" }, "")
  ).rejects.toStrictEqual(Error("Cannot fetch token"))
})

test("Returns errors if the query was not successful", async () => {
  mockFailure()
  const client = new GraphqlClient("test")
  const result = await client.query<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.errors).toHaveLength(1)
  expect(result.errors![0].message).toBe("error")
})

test("Returns errors if the mutation was not successful", async () => {
  mockFailure()
  const client = new GraphqlClient("test")
  const result = await client.mutation<IMockData, TMockVariables>(
    { definitions: [], kind: "Document" },
    ""
  )
  expect(result.errors).toHaveLength(1)
  expect(result.errors![0].message).toBe("error")
})
