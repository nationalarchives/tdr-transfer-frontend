import { ClientFileProcessing } from "../src/clientfileprocessing"
import { TdrFile, TProgressFunction } from "@nationalarchives/file-information"
import { UploadFiles } from "../src/upload"
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

test("upload function submits redirect form on upload files success", async () => {
  mockUploadSuccess()
  setUpUploadHTML()

  const uploadFiles = setUpUploadFiles()
  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})

  const mockRetrieveFiles = jest
    .spyOn(uploadFiles, "retrieveFiles")
    .mockImplementation(() => {
      return [dummyFile]
    })

  const mockUploadFilesSuccess = jest
    .spyOn(uploadFiles, "uploadFilesSuccess")
    .mockImplementation(() => {})
  uploadFiles.upload()

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const uploadDataForm: HTMLFormElement | null = document.querySelector(
    "#upload-data-form"
  )

  if (uploadForm && uploadDataForm) {
    await uploadForm.submit()
    expect(mockRetrieveFiles).toBeCalledTimes(1)
    expect(mockUploadFilesSuccess).toBeCalledTimes(1)
  }

  expect(consoleErrorSpy).not.toHaveBeenCalled()

  mockRetrieveFiles.mockRestore()
  mockUploadFilesSuccess.mockRestore()
  consoleErrorSpy.mockRestore()
})

test("upload function console logs error when upload fails", async () => {
  mockUploadFailure()
  setUpUploadHTML()

  const uploadFiles = setUpUploadFiles()
  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})

  const mockRetrieveFiles = jest
    .spyOn(uploadFiles, "retrieveFiles")
    .mockImplementation(() => {
      return [dummyFile]
    })

  const mockUploadFilesSuccess = jest
    .spyOn(uploadFiles, "uploadFilesSuccess")
    .mockImplementation(() => {})

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  const uploadDataForm: HTMLFormElement | null = document.querySelector(
    "#upload-data-form"
  )
  uploadFiles.upload()
  if (uploadForm && uploadDataForm) {
    await uploadForm.submit()
    expect(mockRetrieveFiles).toBeCalledTimes(1)
    expect(mockUploadFilesSuccess).toBeCalledTimes(0)
  }

  expect(consoleErrorSpy).toHaveBeenCalled()

  mockRetrieveFiles.mockRestore()
  mockUploadFilesSuccess.mockRestore()
  consoleErrorSpy.mockRestore()
})

test("upload function console logs error when no consignment id provided", async () => {
  const consoleErrorSpy = jest
    .spyOn(console, "error")
    .mockImplementation(() => {})
  const uploadFiles = setUpUploadFiles()

  const mockRetrieveFiles = jest
    .spyOn(uploadFiles, "retrieveFiles")
    .mockImplementation(() => {
      return [dummyFile]
    })

  const mockUploadFilesSuccess = jest
    .spyOn(uploadFiles, "uploadFilesSuccess")
    .mockImplementation(() => {})
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  uploadFiles.upload()
  if (uploadForm) {
    await uploadForm.submit()
    expect(mockRetrieveFiles).toBeCalledTimes(0)
    expect(mockUploadFilesSuccess).toBeCalledTimes(0)
  }

  expect(consoleErrorSpy).toHaveBeenCalled()

  mockRetrieveFiles.mockRestore()
  mockUploadFilesSuccess.mockRestore()
  consoleErrorSpy.mockRestore()
})

function setUpUploadFiles(): UploadFiles {
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  return new UploadFiles(uploadMetadata, "identityId", "test")
}

function setUpUploadHTML() {
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form" data-consignment-id="12345">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    '<form id="upload-data-form"></form>'
  "</div>"
}
