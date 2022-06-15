import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "@apollo/client/core"
import { UpdateConsignmentStatus } from "../src/updateconsignmentstatus"
import { GraphQLError } from "graphql"
import { mockKeycloakInstance } from "./utils"
import {
  GetFileCheckProgressQuery,
  GetFileCheckProgressQueryVariables
} from "@nationalarchives/tdr-generated-graphql"

jest.mock("../src/graphql")
jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

type IMockUpdateConsignmentStatusData = {
  updateConsignmentStatus: number
} | null

type TMockVariables = string

class GraphqlClientSuccessUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockUpdateConsignmentStatusData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockUpdateConsignmentStatusData = {
      updateConsignmentStatus: 1
    }
    return { data }
  }
}

class GraphqlClientFailureUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockUpdateConsignmentStatusData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockUpdateConsignmentStatusData = null
    return { data }
  }
}

class GraphqlClientDataErrorUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockUpdateConsignmentStatusData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    return {
      errors: [new GraphQLError("error 1"), new GraphQLError("error 2")]
    }
  }
}

class GraphqlClientFailure {
  mutation: (
    query: DocumentNode,
    variables: GetFileCheckProgressQueryVariables
  ) => Promise<FetchResult<GetFileCheckProgressQuery>> = async (_, __) => {
    return Promise.reject(Error("error"))
  }
}

beforeEach(() => jest.resetModules())

const mockSuccessUpdateStatusToComplete: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientSuccessUpdateStatusToComplete()
  })
}

const mockFailureUpdateStatusToComplete: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailureUpdateStatusToComplete()
  })
}

const mockDataErrorsUpdateStatusToComplete: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientDataErrorUpdateStatusToComplete()
  })
}

const mockFailure: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailure()
  })
}

const uploadFilesInfo = {
  consignmentId: "2o4i5u4ywd5g4",
  parentFolder: "TEST PARENT FOLDER NAME"
}

test("markUploadStatusAsCompleted returns the status of 1", async () => {
  mockSuccessUpdateStatusToComplete()

  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const updateConsignmentStatus = new UpdateConsignmentStatus(client)
  const result = await updateConsignmentStatus.markUploadStatusAsCompleted(
    uploadFilesInfo
  )

  expect(result).toEqual(1)
})

test("markUploadStatusAsCompleted returns error if no data returned", async () => {
  mockFailureUpdateStatusToComplete()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markUploadStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("no data"))
})

test("markUploadStatusAsCompleted returns error if returned data contains errors", async () => {
  mockDataErrorsUpdateStatusToComplete()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markUploadStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("error 1,error 2"))
})

test("markUploadStatusAsCompleted returns error if client fails", async () => {
  mockFailure()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markUploadStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("error"))
})
