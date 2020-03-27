import { processFiles } from "../src/clientprocessing"
import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "apollo-boost"

jest.mock("../src/graphql")

type IMockFileData = { addFiles: { fileIds: number[] } } | null
type TMockVariables = string

class GraphqlClientSuccess {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockFileData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockFileData = { addFiles: { fileIds: [1, 2, 3] } }
    return { data }
  }
}

class GraphqlClientFailure {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockFileData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockFileData = null
    return { data }
  }
}

beforeEach(() => jest.resetModules())

const mockSuccess: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientSuccess()
  })
}

const mockFailure: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailure()
  })
}

test("On success returns list of file ids", async () => {
  mockSuccess()
  const client = new GraphqlClient("https://test.im", "token")
  const result = await processFiles(client, 1, 3)

  expect(result).toEqual([1, 2, 3])
})

test("Returns error if no data returned", async () => {
  mockFailure()
  const client = new GraphqlClient("https://test.im", "token")

  await expect(processFiles(client, 1, 3)).rejects.toStrictEqual(
    Error("Add files failed")
  )
})
