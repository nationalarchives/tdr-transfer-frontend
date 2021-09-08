import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import { FileUploader } from "../src/upload"
import { mockKeycloakInstance } from "./utils"
import { GraphqlClient } from "../src/graphql"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { IFrontEndInfo } from "../src"
import {UpdateConsignmentStatus} from "../src/updateconsignmentstatus";
import {DocumentNode, FetchResult} from "@apollo/client/core";
import {
  MarkUploadAsCompletedMutation
} from "@nationalarchives/tdr-generated-graphql";
import {KeycloakInstance} from "keycloak-js";
jest.mock("../src/clientfileprocessing")
jest.mock("../src/graphql")

beforeEach(() => {
  jest.resetAllMocks()
  jest.resetModules()
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
  ) => Promise<FetchResult<MarkUploadAsCompletedMutation>> = async (_, __) => {
    const data: MarkUploadAsCompletedMutation = {
      markUploadAsCompleted: 1
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

const mockKeycloak: (
    tokenExpired: boolean,
    updateTokenFunc: Function
) => KeycloakInstance = (tokenExpired= false, updateTokenFunc = jest.fn) => {
  const updateToken = updateTokenFunc()
  const isTokenExpired = jest.fn().mockImplementation(() => tokenExpired)
  const fixedExp = Math.round(new Date().getTime() / 1000) + 60
  return {
    refreshTokenParsed: { exp: fixedExp },
    init: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
    register: jest.fn(),
    accountManagement: jest.fn(),
    createLoginUrl: jest.fn(),
    createLogoutUrl: jest.fn(),
    createRegisterUrl: jest.fn(),
    createAccountUrl: jest.fn(),
    isTokenExpired,
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }
}

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
  const updateToken = jest.fn()
  const isTokenExpired = jest.fn().mockImplementation(() => true)
  const mockKeycloak: KeycloakInstance = {
    refreshTokenParsed: { exp: Math.round(new Date().getTime() / 1000) + 60 },
    init: jest.fn(),
    login: jest.fn(),
    logout: jest.fn(),
    register: jest.fn(),
    accountManagement: jest.fn(),
    createLoginUrl: jest.fn(),
    createLogoutUrl: jest.fn(),
    createRegisterUrl: jest.fn(),
    createAccountUrl: jest.fn(),
    isTokenExpired,
    updateToken,
    clearToken: jest.fn(),
    hasRealmRole: jest.fn(),
    hasResourceRole: jest.fn(),
    loadUserInfo: jest.fn(),
    loadUserProfile: jest.fn(),
    token: "fake-auth-token"
  }

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
  expect(updateToken).toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
  consoleErrorSpy.mockRestore()
})

function setUpFileUploader(mockKeycloak?: KeycloakInstance): FileUploader {
  const keycloakInstance = mockKeycloak != undefined ? mockKeycloak : mockKeycloakInstance
  const clientMock = GraphqlClient as jest.Mock
  clientMock.mockImplementation(() => new GraphqlClientSuccess())
  const client = new GraphqlClient("https://test.im", keycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  const frontendInfo: IFrontEndInfo = {
    apiUrl: "",
    cognitoRoleArn: "",
    identityPoolId: "",
    identityProviderName: "",
    region: "",
    stage: "test"
  }
  const updateConsignmentStatus = new UpdateConsignmentStatus(client)
  return new FileUploader(
    uploadMetadata,
    updateConsignmentStatus,
    "identityId",
    frontendInfo,
    mockGoToNextPage,
    keycloakInstance
  )
}
