import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { ClientFileProcessing } from "../src/clientfileprocessing"
import { TProgressFunction } from "@nationalarchives/file-information"
import { FileUploader, pageUnloadAction } from "../src/upload"
import { createMockKeycloakInstance, mockKeycloakInstance } from "./utils"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { IFrontEndInfo } from "../src"
import Keycloak from "keycloak-js"
import { EntryKind, IEntry } from "../src/upload/form/file-types"
jest.mock("../src/clientfileprocessing")
jest.mock("uuid", () => "eb7b7961-395d-4b4c-afc6-9ebcadaf0150")

beforeEach(() => {
  document.body.innerHTML = '<input name="csrfToken" value="abcde">'
  fetchMock.resetMocks()
  jest.clearAllMocks()
})

const dummyFile = {
  file: new File([], ""),
  path: "relativePath",
  kind: EntryKind.File
} as IEntry

const mockUploadSuccess: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => ({
    processClientFiles: async (
      _consignmentId: string,
      _files: IEntry[],
      _callback: TProgressFunction,
      _stage: string
    ): Promise<void> => {}
  }))
}

const mockUploadFailure: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => ({
    processClientFiles: async (
      _consignmentId: string,
      _files: IEntry[],
      _callback: TProgressFunction,
      _stage: string
    ): Promise<void | Error> => {
      return Promise.resolve(Error("Some error"))
    }
  }))
}

const mockUploadRejection: (error: unknown) => void = (error) => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => ({
    processClientFiles: async (
      _consignmentId: string,
      _files: IEntry[],
      _callback: TProgressFunction,
      _stage: string
    ): Promise<void> => {
      return Promise.reject(error)
    }
  }))
}

const mockGoToFileChecksPage = jest.fn()

test("upload function will redirect to the file checks page with uploadFailed set to true if the upload fails", async () => {
  mockUploadFailure()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToFileChecksPage).toHaveBeenLastCalledWith(
    "12345",
    "true",
    false
  )

  mockGoToFileChecksPage.mockRestore()
})

test("upload function redirects to the file checks page with uploadFailed set to false if the upload succeeds", async () => {
  mockUploadSuccess()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToFileChecksPage).toHaveBeenLastCalledWith(
    "12345",
    "false",
    false
  )

  mockGoToFileChecksPage.mockRestore()
})

test("upload function refreshes idle session", async () => {
  mockUploadSuccess()

  const mockUpdateToken = jest.fn().mockImplementation((_: number) => {
    return new Promise((res, _rej) => res(true))
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

test("upload function redirects to the file checks page with uploadFailed set to true when a file upload rejects", async () => {
  mockUploadRejection(Error("Access Denied"))

  const uploadFiles = setUpFileUploader()

  await expect(
    uploadFiles.uploadFiles([dummyFile], {
      consignmentId: "12345",
      parentFolder: "TEST PARENT FOLDER NAME",
      includeTopLevelFolder: false
    })
  ).resolves.toBeUndefined()

  expect(mockGoToFileChecksPage).toHaveBeenLastCalledWith(
    "12345",
    "true",
    false
  )
})

test("upload function redirects to the file checks page when a file upload rejects with a non error value", async () => {
  mockUploadRejection("something went wrong")

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToFileChecksPage).toHaveBeenLastCalledWith(
    "12345",
    "true",
    false
  )
})

test("upload function stops warning the user about leaving the page when a file upload rejects", async () => {
  mockUploadRejection(Error("Access Denied"))
  const removeEventListener = jest.spyOn(window, "removeEventListener")

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(removeEventListener).toHaveBeenCalledWith(
    "beforeunload",
    pageUnloadAction
  )

  removeEventListener.mockRestore()
})

test("upload function leaves the logged out message in place instead of redirecting", async () => {
  // An expired refresh token is the only way a LoggedOutError arises.
  mockUploadSuccess()
  const expiredRefreshToken = {
    exp: Math.round(new Date().getTime() / 1000) - 60
  }
  const keycloak = createMockKeycloakInstance(
    jest.fn(),
    true,
    expiredRefreshToken
  )

  const uploadFiles = setUpFileUploader(keycloak)

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME",
    includeTopLevelFolder: false
  })

  expect(mockGoToFileChecksPage).not.toHaveBeenCalled()
})

function setUpFileUploader(mockKeycloak?: Keycloak): FileUploader {
  const keycloakInstance =
    mockKeycloak != undefined ? mockKeycloak : mockKeycloakInstance

  const uploadMetadata = new ClientFileMetadataUpload()
  const frontendInfo: IFrontEndInfo = {
    apiUrl: "",
    region: "",
    stage: "test",
    uploadUrl: "https://example.com",
    authUrl: "",
    clientId: "",
    realm: "",
    ifNoneMatchHeaderValue: "*",
    aclHeaderValue: "bucket-owner-full-control"
  }

  return new FileUploader(
    uploadMetadata,
    frontendInfo,
    keycloakInstance,
    mockGoToFileChecksPage
  )
}
