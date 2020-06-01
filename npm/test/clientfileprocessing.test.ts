import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  IFileMetadata,
  TdrFile,
  TProgressFunction,
  IProgressInformation
} from "@nationalarchives/file-information"
import { ClientFileExtractMetadata } from "../src/clientfileextractmetadata"
import { S3Upload, ITdrFile } from "../src/s3upload"
import { ManagedUpload } from "aws-sdk/clients/s3"
import { mockKeycloakInstance } from "./utils"

jest.mock("../src/clientfilemetadataupload")
jest.mock("../src/clientfileextractmetadata")
jest.mock("../src/s3upload")

beforeEach(() => jest.resetModules())

class S3UploadMock extends S3Upload {
  constructor() {
    super("some Cognito user ID")
  }

  uploadToS3: (
    consignmentId: string,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string,
    chunkSize?: number
  ) => Promise<ManagedUpload.SendData[]> = jest.fn()
}

class ClientFileUploadSuccess {
  saveFileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.resolve(["1", "2"])
  }
  saveClientFileMetadata: (
    files: File[],
    fileIds: string[]
  ) => Promise<void> = async (files: File[], fileIds: string[]) => {
    return Promise.resolve()
  }
}

class ClientFileExtractMetadataSuccess {
  extract: (files: TdrFile[]) => Promise<IFileMetadata[]> = async (
    files: TdrFile[]
  ) => {
    return Promise.resolve([])
  }
}

class ClientFileUploadFileInformationFailure {
  saveFileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.reject(Error("upload client file information error"))
  }
  saveClientFileMetadata: (
    files: File[],
    fileIds: string[]
  ) => Promise<void> = async (files: File[], fileIds: string[]) => {
    return Promise.resolve()
  }
}

class ClientFileUploadMetadataFailure {
  saveFileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.resolve(["1", "2"])
  }
  saveClientFileMetadata: (
    files: File[],
    fileIds: string[]
  ) => Promise<void> = async (files: File[], fileIds: string[]) => {
    return Promise.reject(Error("upload client file metadata error"))
  }
}

class ClientFileExtractMetadataFailure {
  extract: (files: TdrFile[]) => Promise<IFileMetadata[]> = async (
    files: TdrFile[]
  ) => {
    return Promise.reject(Error("client file metadata extraction error"))
  }
}

const mockMetadataUploadSuccess: () => void = () => {
  const mock = ClientFileMetadataUpload as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileUploadSuccess()
  })
}

const mockMetadataExtractSuccess: () => void = () => {
  const mock = ClientFileExtractMetadata as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileExtractMetadataSuccess()
  })
}

const mockUploadFileInformationFailure: () => void = () => {
  const mock = ClientFileMetadataUpload as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileUploadFileInformationFailure()
  })
}

const mockUploadMetadataFailure: () => void = () => {
  const mock = ClientFileMetadataUpload as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileUploadMetadataFailure()
  })
}

const mockMetadataExtractFailure: () => void = () => {
  const mock = ClientFileExtractMetadata as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileExtractMetadataFailure()
  })
}

const mockS3UploadFailure: (message: string) => void = (message: string) => {
  const s3UploadMock = S3Upload as jest.Mock
  s3UploadMock.mockImplementation(() => {
    return {
      uploadToS3: (
        consignmentId: string,
        files: ITdrFile[],
        callback: TProgressFunction,
        stage: string,
        chunkSize?: number
      ) => {
        return Promise.reject(new Error(message))
      }
    }
  })
}

test("client file metadata successfully uploaded", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )
  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).resolves.not.toThrow()
})

test("metadataProgressBarCallback function updates the progress bar to a maximum of 50 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 100,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.metadataProgressCallback(progressInformation)

  checkExpectedPageState("50")
})

test("metadataProgressCallback function does not change the HTML state if no progress bar present", () => {
  setupUploadPageHTMLWithoutProgressBar()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 30,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.metadataProgressCallback(progressInformation)

  checkNoPageStateChangeExpected()
})

test("metadataProgressBarCallback function updates the progress bar with the percentage processed", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 30,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.metadataProgressCallback(progressInformation)

  checkExpectedPageState("15")
})

test("file successfully uploaded to s3", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const s3UploadMock = new S3UploadMock()
  const fileProcessing = new ClientFileProcessing(metadataUpload, s3UploadMock)
  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).resolves.not.toThrow()

  expect(s3UploadMock.uploadToS3).toHaveBeenCalledTimes(1)
})

test("s3ProgressBarCallback function updates the progress bar with the percentage processed", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 75,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.s3ProgressCallback(progressInformation)

  checkS3UploadProgressBarState("87.5")
})

test("s3ProgressBarCallback function updates progress bar from a minimum of 50 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 0,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.s3ProgressCallback(progressInformation)

  checkS3UploadProgressBarState("50")
})

test("s3ProgressBarCallback function updates the progress bar to a maximum of 100 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 100,
    processedFiles: 0
  }

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.s3ProgressCallback(progressInformation)

  checkS3UploadProgressBarState("100")
})

test("Error thrown if processing files fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadFileInformationFailure()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).rejects.toStrictEqual(
    Error(
      "Processing client files failed: upload client file information error"
    )
  )
})

test("Error thrown if processing file metadata fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadMetadataFailure()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).rejects.toStrictEqual(
    Error("Processing client files failed: upload client file metadata error")
  )
})

test("Error thrown if extracting file metadata fails", async () => {
  mockMetadataExtractFailure()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).rejects.toStrictEqual(
    Error(
      "Processing client files failed: client file metadata extraction error"
    )
  )
})

test("Error thrown if S3 upload fails", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()
  mockS3UploadFailure("Some S3 error")

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const s3Upload = new S3Upload("some Cognito user ID")
  const fileProcessing = new ClientFileProcessing(metadataUpload, s3Upload)

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn(), "")
  ).rejects.toStrictEqual(
    Error("Processing client files failed: Some S3 error")
  )
})

function setupUploadPageHTML() {
  document.body.innerHTML = `<div id="file-upload" class="govuk-grid-row"></div>
   <div id="upload-error" class="govuk-error-summary upload-error hide" aria-labelledby="error-summary-title"
            role="alert" tabindex="-1" data-module="govuk-error-summary">
            <h2 class="govuk-error-summary__title" id="error-summary-title"></h2></div>
    <div id="progress-bar" class="govuk-grid-row hide">
    <div> <progress class="progress-display" value="" max="50"></progress> </div>
    </div>`
}

function setupUploadPageHTMLWithoutProgressBar() {
  document.body.innerHTML = `<div id="file-upload" class="govuk-grid-row"></div>
  <div id="upload-error" class="govuk-error-summary upload-error hide" aria-labelledby="error-summary-title
          role="alert" tabindex="-1" data-module="govuk-error-summary">
          <h2 class="govuk-error-summary__title" id="error-summary-title"></h2></div>`
}

function checkExpectedPageState(percentage: String) {
  const fileUpload: HTMLDivElement | null = document.querySelector(
    "#file-upload"
  )
  const progressBar: HTMLDivElement | null = document.querySelector(
    "#progress-bar"
  )

  const progressBarElement: HTMLDivElement | null = document.querySelector(
    ".progress-display"
  )

  const uploadError: HTMLDivElement | null = document.querySelector(
    "#upload-error"
  )

  expect(progressBar && progressBar.classList.toString()).toEqual(
    "govuk-grid-row"
  )

  expect(fileUpload && fileUpload.classList.toString()).toEqual(
    "govuk-grid-row hide"
  )

  expect(uploadError && uploadError.classList.toString()).toEqual(
    "govuk-error-summary upload-error hide"
  )

  expect(
    progressBarElement && progressBarElement.getAttribute("value")
  ).toEqual(percentage)
}

function checkS3UploadProgressBarState(percentage: String) {
  const progressBarElement: HTMLDivElement | null = document.querySelector(
    ".progress-display"
  )

  expect(
    progressBarElement && progressBarElement.getAttribute("value")
  ).toEqual(percentage)
}

function checkNoPageStateChangeExpected() {
  const fileUpload: HTMLDivElement | null = document.querySelector(
    "#file-upload"
  )

  expect(fileUpload && fileUpload.classList.toString()).toEqual(
    "govuk-grid-row"
  )
}
