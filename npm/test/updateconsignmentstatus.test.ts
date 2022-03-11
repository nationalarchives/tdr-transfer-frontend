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

type IMockMarkUploadAsCompletedData = { markUploadAsCompleted: number } | null

type TMockVariables = string

class GraphqlClientSuccessUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockMarkUploadAsCompletedData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockMarkUploadAsCompletedData = { markUploadAsCompleted: 1 }
    return { data }
  }
}

class GraphqlClientFailureUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockMarkUploadAsCompletedData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockMarkUploadAsCompletedData = null
    return { data }
  }
}

class GraphqlClientDataErrorUpdateStatusToComplete {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockMarkUploadAsCompletedData>> = async (
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

test("markConsignmentStatusAsCompleted returns the status of 1", async () => {
  mockSuccessUpdateStatusToComplete()

  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const updateConsignmentStatus = new UpdateConsignmentStatus(client)
  const result = await updateConsignmentStatus.markConsignmentStatusAsCompleted(
    uploadFilesInfo
  )

  expect(result).toEqual(1)
})

test("markConsignmentStatusAsCompleted returns error if no data returned", async () => {
  mockFailureUpdateStatusToComplete()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markConsignmentStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("no data"))
})

test("markConsignmentStatusAsCompleted returns error if returned data contains errors", async () => {
  mockDataErrorsUpdateStatusToComplete()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markConsignmentStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("error 1,error 2"))
})

test("markConsignmentStatusAsCompleted returns error if client fails", async () => {
  mockFailure()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new UpdateConsignmentStatus(client)
  await expect(
    uploadMetadata.markConsignmentStatusAsCompleted(uploadFilesInfo)
  ).resolves.toStrictEqual(Error("error"))
})
