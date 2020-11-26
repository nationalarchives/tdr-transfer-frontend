import { ClientFileProcessing } from "../src/clientfileprocessing"
import { IFileWithPath, TProgressFunction } from "@nationalarchives/file-information"
import { FileUploader } from "../src/upload"
import { mockKeycloakInstance } from "./utils"
import { GraphqlClient } from "../src/graphql"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
jest.mock("../src/clientfileprocessing")

beforeEach(() => jest.resetModules())

const dummyFile = {
  webkitRelativePath: "relativePath"
} as TdrFile

class ClientFileProcessingSuccess {
  processClientFiles: (
    consignmentId: string,
    files: TdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<void> = async (
    consignmentId: string,
    files: TdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => {}
}

class ClientFileProcessingFailure {
  processClientFiles: (
    consignmentId: string,
    files: TdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<void> = async (
    consignmentId: string,
    files: TdrFile[],
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

const mockUploadFailure: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingFailure()
  })
}

const mockGoToNextPage = jest.fn()

test("upload function submits redirect form on upload files success", async () => {
  mockUploadSuccess()

  const uploadFiles = setUpUploadFiles()
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

test("upload function console logs error when upload fails", async () => {
  mockUploadFailure()

  const uploadFiles = setUpUploadFiles()
  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})

  await uploadFiles.uploadFiles([dummyFile], {
    consignmentId: "12345",
    parentFolder: "TEST PARENT FOLDER NAME"
  })

  expect(consoleErrorSpy).toHaveBeenCalled()
  expect(mockGoToNextPage).not.toHaveBeenCalled()

  mockGoToNextPage.mockRestore()
  consoleErrorSpy.mockRestore()
})

function setUpUploadFiles(): FileUploader {
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  return new FileUploader(uploadMetadata, "identityId", "test", mockGoToNextPage)
}
