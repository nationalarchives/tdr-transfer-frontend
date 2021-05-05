const mockGraphqlClient = {
  updateProgressBar: jest.fn(),
  getConsignmentData: jest.fn(),
  getConsignmentId: jest.fn()
}
import {
  IFileCheckProcessed,
  updateProgressBar,
  getConsignmentData,
  getConsignmentId
} from "../src/filechecks/file-check-processing"
import {
  GetFileCheckProgressQueryVariables,
  GetFileCheckProgressQuery
} from "@nationalarchives/tdr-generated-graphql"
import { GraphqlClient } from "../src/graphql"
import { mockKeycloakInstance } from "./utils"
import { DocumentNode } from "graphql"
import { FetchResult } from "apollo-boost"

jest.mock("../src/graphql")

class GraphqlClientSuccess {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = async (_, __) => {
    const data: GetFileCheckProgressQuery = {
      getConsignment: {
        totalFiles: 10,
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
    const data: GetFileCheckProgressQuery = {
      getConsignment: null
    }
    return { data }
  }
}

class GraphqlClientFailure {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = (_, __) => {
    return Promise.reject("error")
  }
}

test.each`
  processed | total   | expected
  ${1}      | ${10}   | ${10}
  ${20}     | ${60}   | ${33}
  ${0}      | ${1000} | ${0}
  ${0}      | ${0}    | ${0}
`(
  "updateProgressBar sets the progress bar value to $expected for processed $processed and total $total",
  ({ processed, total, expected }) => {
    document.body.innerHTML = `<progress id="test" value="0" max="100"/>`
    updateProgressBar(processed, total, "#test")
    const progressBar: HTMLProgressElement | null = document.querySelector(
      "#test"
    )
    expect(progressBar).not.toBeNull()
    expect(progressBar!.value).toBe(expected)
  }
)

test("getConsignmentData returns the correct consignment data with a successful api call", done => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientSuccess())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  const callback: (
    fileCheckProcessed: IFileCheckProcessed | null
  ) => void = fileCheckProcessed => {
    expect(fileCheckProcessed!.antivirusProcessed).toBe(2)
    expect(fileCheckProcessed!.checksumProcessed).toBe(3)
    expect(fileCheckProcessed!.ffidProcessed).toBe(4)
    expect(fileCheckProcessed!.totalFiles).toBe(10)
    done()
  }
  getConsignmentData(client, callback)
})

test("getConsignmentData returns null with a failed api call", done => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientFailure())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  const callback: (
    fileCheckProcessed: IFileCheckProcessed | null
  ) => void = fileCheckProcessed => {
    expect(fileCheckProcessed).toBeNull()
    done()
  }
  getConsignmentData(client, callback)
})

test("getConsignmentData returns null with empty data from a successful api call", done => {
  const consignmentId = "7d4ae1dd-caeb-496d-b503-ab0e8d82a12c"
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientEmptyData())
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`

  const callback: (
    fileCheckProcessed: IFileCheckProcessed | null
  ) => void = fileCheckProcessed => {
    expect(fileCheckProcessed).toBeNull()
    done()
  }
  getConsignmentData(client, callback)
})

test("getConsignmentId returns the correct id when the hidden input is present", () => {
  const consignmentId = "91847ae4-3bfd-4fac-b448-68948dd95820"
  document.body.innerHTML = `<input id="consignmentId" type="hidden" value="${consignmentId}">`
  expect(getConsignmentId()).toBe(consignmentId)
})

test("getConsignmentId throws an error when the hidden input is absent", () => {
  document.body.innerHTML = ""
  expect(() => getConsignmentId()).toThrowError("No consignment provided")
})
