import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import { FileUploader } from "../src/upload"
import { createMockKeycloakInstance, mockKeycloakInstance } from "./utils"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { IFrontEndInfo } from "../src"
import { KeycloakInstance } from "keycloak-js"
jest.mock("../src/clientfileprocessing")
jest.mock("uuid", () => "eb7b7961-395d-4b4c-afc6-9ebcadaf0150")

beforeEach(() => {
  document.body.innerHTML = '<input name="csrfToken" value="abcde">'
  fetchMock.resetMocks()
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
  ) => Promise<void | Error> = async (
    consignmentId: string,
    files: IFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => {
    return Promise.resolve(Error("Some error"))
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

test("upload function will redirect to the next page with uploadFailed set to true if the upload fails", async () => {
  mockUploadFailure()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToNextPage).lastCalledWith("12345", "true", false)

  mockGoToNextPage.mockRestore()
})

test("upload function redirects to the next page with uploadFailed set to false if the upload succeeds", async () => {
  mockUploadSuccess()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToNextPage).lastCalledWith("12345", "false", false)

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
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })
  jest.runAllTimers()

  expect(mockUpdateToken).toHaveBeenCalled()

  consoleErrorSpy.mockRestore()
})


function setUpFileUploader(mockKeycloak?: KeycloakInstance): FileUploader {
  const keycloakInstance =
    mockKeycloak != undefined ? mockKeycloak : mockKeycloakInstance

  const uploadMetadata = new ClientFileMetadataUpload()
  const frontendInfo: IFrontEndInfo = {
    apiUrl: "",
    region: "",
    stage: "test",
    uploadUrl: "https://example.com"
  }

  return new FileUploader(
    uploadMetadata,
    frontendInfo,
    keycloakInstance,
    mockGoToNextPage
  )
}
