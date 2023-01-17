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
import { UpdateConsignmentStatus } from "../src/updateconsignmentstatus"
import { KeycloakInstance } from "keycloak-js"
import { TriggerBackendChecks } from "../src/triggerbackendchecks"
jest.mock("../src/clientfileprocessing")
jest.mock("../src/triggerbackendchecks")
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

class TriggerBackendChecksSuccess {
  triggerBackendChecks: (consignmentId: string) => Promise<Response | Error> =
    async (consignmentId) => {
      const response = new Response(null, { status: 200 })
      return Promise.resolve(response)
    }
}

class TriggerBackendChecksFailure {
  triggerBackendChecks: (consignmentId: string) => Promise<Response | Error> =
    async (_) => {
      return Promise.resolve(
        new Error("An error occurred triggering the backend checks")
      )
    }
}

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

const mockUploadSuccess: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingSuccess()
  })
}

const mockTriggerBackendChecksSuccess: () => void = () => {
  const mock = TriggerBackendChecks as jest.Mock
  mock.mockImplementation(() => {
    return new TriggerBackendChecksSuccess()
  })
}

const mockTriggerBackendChecksFailure: () => void = () => {
  const mock = TriggerBackendChecks as jest.Mock
  mock.mockImplementation(() => {
    return new TriggerBackendChecksFailure()
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
  fetchMock.mockResponse(JSON.stringify({ updateConsignmentStatus: 1 }))

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

  await expect(
    uploadFiles.uploadFiles([dummyFile], {
      consignmentId: "12345",
      parentFolder: "TEST PARENT FOLDER NAME"
    })
  ).rejects.toThrow("Some error")

  expect(mockGoToNextPage).not.toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
})

test("upload function will not redirect to the next page if the backend checks trigger fails", async () => {
  fetchMock.mockResponse(JSON.stringify({ updateConsignmentStatus: 1 }))
  mockUploadSuccess()
  mockTriggerBackendChecksFailure()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })

  expect(mockGoToNextPage).not.toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
})

test("upload function redirects to the next page when backend checks trigger succeeds", async () => {
  fetchMock.mockResponse(JSON.stringify({ updateConsignmentStatus: 1 }))
  mockUploadSuccess()
  mockTriggerBackendChecksSuccess()

  const uploadFiles = setUpFileUploader()

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })

  expect(mockGoToNextPage).toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
})

test("upload function refreshes idle session", async () => {
  mockUploadSuccess()
  fetchMock.mockResponse(JSON.stringify({ updateConsignmentStatus: 1 }))

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

  const uploadMetadata = new ClientFileMetadataUpload()
  const frontendInfo: IFrontEndInfo = {
    apiUrl: "",
    region: "",
    stage: "test",
    uploadUrl: "https://example.com"
  }
  const updateConsignmentStatus = new UpdateConsignmentStatus()
  const triggerBackendChecks = new TriggerBackendChecks()
  return new FileUploader(
    uploadMetadata,
    updateConsignmentStatus,
    frontendInfo,
    mockGoToNextPage,
    keycloakInstance,
    triggerBackendChecks
  )
}
