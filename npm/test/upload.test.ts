import { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import { FileUploader } from "../src/upload"
import { createMockKeycloakInstance, mockKeycloakInstance } from "./utils"
import { GraphqlClient } from "../src/graphql"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { IFrontEndInfo } from "../src"
import { UpdateConsignmentStatus } from "../src/updateconsignmentstatus"
import { DocumentNode, FetchResult } from "@apollo/client/core"
import { UpdateConsignmentStatusMutation } from "@nationalarchives/tdr-generated-graphql"
import { KeycloakInstance } from "keycloak-js"
jest.mock("../src/clientfileprocessing")
jest.mock("../src/graphql")

beforeEach(() => {
  jest.clearAllMocks()
  jest.clearAllMocks()
})

const dummyFile = {
  file: new File([], ""),
  path: "relativePath"
} as IFileWithPath

class ClientFileProcessingSuccess {
  processClientFiles: (
    consignmentId: string,
    files: IFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<void> = async (
    consignmentId: string,
    files: IFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => {}
}

class ClientFileProcessingFailure {
  processClientFiles: (
    consignmentId: string,
    files: IFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<void> = async (
    consignmentId: string,
    files: IFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => {
    throw Error("Some error")
  }
}

class GraphqlClientSuccess {
  mutation: (
    query: DocumentNode,
    variables: any
  ) => Promise<FetchResult<UpdateConsignmentStatusMutation>> = async (_, __) => {
    const data: UpdateConsignmentStatusMutation = {
      updateConsignmentStatus: 1
    }
    return { data }
  }
}

const mockUploadSuccess: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingSuccess()
  })
}

const mockUploadFailure: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingFailure()
  })
}

const mockGoToNextPage = jest.fn()

test("upload function submits redirect form on upload files success", async () => {
  mockUploadSuccess()

  const uploadFiles = setUpFileUploader()
  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })

  expect(consoleErrorSpy).not.toHaveBeenCalled()
  expect(mockGoToNextPage).toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
  consoleErrorSpy.mockRestore()
})

test("upload function throws an error when upload fails", async () => {
  mockUploadFailure()

  const uploadFiles = setUpFileUploader()

  await expect(uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })).rejects.toThrow("Some error")

  expect(mockGoToNextPage).not.toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
})

test("upload function refreshes idle session", async () => {
  mockUploadSuccess()
  const mockUpdateToken = jest.fn().mockImplementation((_: number) => {
    return new Promise((res, _) => res(true))
  })
  const isTokenExpired = true
  const refreshTokenParsed = {
    exp: Math.round(new Date().getTime() / 1000) + 60
  }
  const mockKeycloak = createMockKeycloakInstance(
    mockUpdateToken,
    isTokenExpired,
    refreshTokenParsed
  )

  const uploadFiles = setUpFileUploader(mockKeycloak)

  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})

  jest.useFakeTimers()
  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })
  jest.runAllTimers()

  expect(consoleErrorSpy).not.toHaveBeenCalled()
  expect(mockGoToNextPage).toHaveBeenCalled()
  expect(mockUpdateToken).toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
  consoleErrorSpy.mockRestore()
})

function setUpFileUploader(mockKeycloak?: KeycloakInstance): FileUploader {
  const keycloakInstance =
    mockKeycloak != undefined ? mockKeycloak : mockKeycloakInstance
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientSuccess())
  const client = new GraphqlClient("https://test.im", keycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  const frontendInfo: IFrontEndInfo = {
    apiUrl: "",
    region: "",
    stage: "test",
    uploadUrl: "https://example.com"
  }
  const updateConsignmentStatus = new UpdateConsignmentStatus(client)
  return new FileUploader(
    uploadMetadata,
    updateConsignmentStatus,
    frontendInfo,
    mockGoToNextPage,
    keycloakInstance
  )
}
