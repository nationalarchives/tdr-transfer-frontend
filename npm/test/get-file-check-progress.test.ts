import { ExecutionResult } from "graphql/execution/execute"
import {
  getConsignmentId,
  getFileChecksProgress,
  IFileCheckProgress
} from "../src/filechecks/get-file-check-progress"
import {
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"
import { GraphqlClient } from "../src/graphql"
import { isError } from "../src/errorhandling"
import { mockKeycloakInstance } from "./utils"
import { DocumentNode, GraphQLError } from "graphql"
import { FetchResult } from "@apollo/client/core"

jest.mock("../src/graphql")

class GraphqlClientSuccess {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = async (_, __) => {
    const data: GetFileCheckProgressQuery = {
      getConsignment: {
        totalFiles: 10,
        allChecksSucceeded: false,
        fileChecks: {
          antivirusProgress: { filesProcessed: 2 },
          ffidProgress: { filesProcessed: 4 },
          checksumProgress: { filesProcessed: 3 }
        }
      }
    }
    return { data }
  }
}

class GraphqlClientEmptyData {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = async (_, __) => {
    throw new Error("No progress metadata found for consignment")
  }
}

class GraphqlClientFailure {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = async (_, __) => {
    throw new Error("error")
  }
}

class GraphqlClientErrors {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<ExecutionResult> = async (_, __) => {
    return {
      data: null,
      errors: [new GraphQLError("error 1")]
    }
  }
}

test("getFileChecksProgress returns the correct consignment data with a successful api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientSuccess())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  const fileChecksProgress: IFileCheckProgress | Error =
    await getFileChecksProgress(client)
  expect(isError(fileChecksProgress)).toBe(false)
  if(!isError(fileChecksProgress)) {
    expect(fileChecksProgress!.antivirusProcessed).toBe(2)
    expect(fileChecksProgress!.checksumProcessed).toBe(3)
    expect(fileChecksProgress!.ffidProcessed).toBe(4)
    expect(fileChecksProgress!.totalFiles).toBe(10)
  }
})

test("getFileChecksProgress throws an exception with a failed api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientFailure())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  await expect(getFileChecksProgress(client)).rejects.toThrow("error")
})

test("getFileChecksProgress throws an exception when empty data from a successful api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientEmptyData())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  await expect(getFileChecksProgress(client)).rejects.toThrow("No progress metadata found for consignment")
})

test("getFileChecksProgress throws a exception after errors from a successful api call", async () => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientErrors())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  await expect(getFileChecksProgress(client)).resolves.toEqual(Error("Add files failed: error 1"))
})

test("getConsignmentId returns the correct id when the hidden input is present", () => {
  const consignmentId = "91847ae4-3bfd-4fac-b448-68948dd95820"
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`
  expect(getConsignmentId()).toBe(consignmentId)
})

test("getConsignmentId throws an error when the hidden input is absent", () => {
  document.body.innerHTML = ""
  expect(getConsignmentId()).toEqual(Error("No consignment provided"))
})
