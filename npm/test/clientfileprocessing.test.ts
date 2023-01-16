import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  TProgressFunction,
  IProgressInformation, IFileWithPath, IFileMetadata,
} from "@nationalarchives/file-information"
import { S3Upload, ITdrFileWithPath, IUploadResult } from "../src/s3upload";
import { S3Client, ServiceOutputTypes } from "@aws-sdk/client-s3"
import fetchMock, {enableFetchMocks} from "jest-fetch-mock"
import {FileUploadInfo} from "../src/upload/form/upload-form";
import {ClientFileExtractMetadata} from "../src/clientfileextractmetadata";
enableFetchMocks()

jest.mock("../src/clientfilemetadataupload")
jest.mock("../src/clientfileextractmetadata")
jest.mock("../src/s3upload")
jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

beforeEach(() => {
  fetchMock.resetMocks()
  jest.resetModules()
})

class S3UploadMock extends S3Upload {
  constructor() {
    super(new S3Client({}), "")
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    files: ITdrFileWithPath[],
    callback: TProgressFunction,
    stage: string,
    chunkSize?: number
  ) => Promise<IUploadResult> = jest.fn()
}

class ClientFileUploadSuccess {
  startUpload: (uploadFilesInfo: FileUploadInfo) => Promise<void> = async (
    uploadFilesInfo: FileUploadInfo
  ) => {
    return Promise.resolve()
  }
  saveClientFileMetadata: (files: File[], fileIds: string[]) => Promise<void> =
    async (files: File[], fileIds: string[]) => {
      return Promise.resolve()
    }
}

class ClientFileExtractMetadataSuccess {
  extract: (files: IFileWithPath[]) => Promise<IFileMetadata[]> = async (
    files: IFileWithPath[]
  ) => {
    return Promise.resolve([])
  }
}

class ClientFileUploadFileInformationFailure {
  startUpload: (uploadFilesInfo: FileUploadInfo) => Promise<string[]> = async (
    uploadFilesInfo: FileUploadInfo
  ) => {
    return Promise.reject(Error("upload client file information error"))
  }
  saveClientFileMetadata: (files: File[], fileIds: string[]) => Promise<void> =
    async (files: File[], fileIds: string[]) => {
      return Promise.resolve()
    }
}

class ClientFileUploadMetadataFailure {
  startUpload: (uploadFilesInfo: FileUploadInfo) => Promise<string[]> = async (
    uploadFilesInfo: FileUploadInfo
  ) => {
    return Promise.resolve(["1", "2"])
  }
  saveClientFileMetadata: (files: File[], fileIds: string[]) => Promise<void> =
    async (files: File[], fileIds: string[]) => {
      return Promise.reject(Error("upload client file metadata error"))
    }
}

class ClientFileExtractMetadataFailure {
  extract: (files: IFileWithPath[]) => Promise<IFileMetadata[]> = async (
    files: IFileWithPath[]
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
        files: ITdrFileWithPath[],
        stage: string,
        chunkSize?: number
      ) => {
        return Promise.reject(new Error(message))
      }
    }
  })
}

const userId = "22579624-3eb9-4453-9b41-dd53a58fcfe7"

test("client file metadata successfully uploaded", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )
  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).resolves.not.toThrow()
})

test("metadataProgressCallback function updates the progress bar to a maximum of 50 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 100,
    processedFiles: 0
  }

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

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

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.metadataProgressCallback(progressInformation)

  checkNoPageStateChangeExpected()
})

test("metadataProgressCallback function updates the progress bar with the percentage processed", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 30,
    processedFiles: 0
  }

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

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
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const s3UploadMock = new S3UploadMock()
  const fileProcessing = new ClientFileProcessing(metadataUpload, s3UploadMock)
  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).resolves.not.toThrow()

  expect(s3UploadMock.uploadToS3).toHaveBeenCalledTimes(1)
})

test("s3ProgressCallback function updates the progress bar with the percentage processed", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 75,
    processedFiles: 0
  }

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.s3ProgressCallback(progressInformation)

  checkS3UploadProgressBarState("87")
})

test("s3ProgressCallback function updates progress bar from a minimum of 50 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 0,
    processedFiles: 0
  }

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  fileProcessing.s3ProgressCallback(progressInformation)

  checkS3UploadProgressBarState("50")
})

test("s3ProgressCallback function updates the progress bar to a maximum of 100 percent", () => {
  setupUploadPageHTML()

  const progressInformation: IProgressInformation = {
    totalFiles: 1,
    percentageProcessed: 100,
    processedFiles: 0
  }

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()

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

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).rejects.toStrictEqual(Error("upload client file information error"))
})

test("Error thrown if processing file metadata fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadMetadataFailure()

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).rejects.toStrictEqual(Error("upload client file metadata error"))
})

test("Error thrown if extracting file metadata fails", async () => {
  mockMetadataExtractFailure()
  mockMetadataUploadSuccess()

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock()
  )

  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).rejects.toStrictEqual(Error("client file metadata extraction error"))
})

test("Error thrown if S3 upload fails", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()
  mockS3UploadFailure("Some S3 error")

  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload()
  const s3Upload = new S3Upload(new S3Client({}), "")
  const fileProcessing = new ClientFileProcessing(metadataUpload, s3Upload)

  await expect(
    fileProcessing.processClientFiles(
      [],
      { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
      "",
      userId
    )
  ).rejects.toStrictEqual(Error("Some S3 error"))
})

test("empty folders are passed correctly to the save metadata function", async () => {
  const saveMetadataFn = jest.fn()
  const metadataUpload: jest.Mock = ClientFileMetadataUpload as jest.Mock
  metadataUpload.mockImplementation(() => {
    return {
      startUpload: jest.fn(),
      saveClientFileMetadata: saveMetadataFn
    }
  })

  const fileProcessing = new ClientFileProcessing(
    metadataUpload(),
    new S3UploadMock()
  )

  await fileProcessing.processClientFiles(
    [{path: "directoryPath"}, {path: "filePath", file: new File(["a"], "test")}],
    { consignmentId: "1", parentFolder: "TEST PARENT FOLDER NAME" },
    "",
    userId
  )
  const saveMetadataArgs = saveMetadataFn.mock.calls[0]
  await expect(saveMetadataArgs[2]).toEqual(["directoryPath"])
})

const showUploadingRecordsPage = () => {
  // At the point of the client file processing, the file-upload part should be hidden and progress bar revealed
  const fileUploadPage: HTMLDivElement | null =
    document.querySelector("#file-upload")
  const uploadProgressPage: HTMLDivElement | null =
    document.querySelector("#upload-progress")

  if (fileUploadPage && uploadProgressPage) {
    fileUploadPage.setAttribute("hidden", "true")
    uploadProgressPage.removeAttribute("hidden")
  }
}

function setupUploadPageHTML() {
  document.body.innerHTML =
    `<div id="file-upload" class="govuk-grid-row">
        <div id="upload-error" class="govuk-error-summary upload-error" hidden aria-labelledby="error-summary-title"
            role="alert" tabindex="-1" data-module="govuk-error-summary">
            <h2 class="govuk-error-summary__title" id="error-summary-title"></h2>
        </div>
        <div id="logged-out-error" class="govuk-error-summary logged-out-error" hidden aria-labelledby="logged-out-error-title"
            role="alert" tabindex="-1" data-module="govuk-error-summary">
        </div>
        <form id="file-upload-form" data-consignment-id="@consignmentId"></form>
    </div>
    <div id="upload-progress" class="govuk-grid-row" hidden>
        <div class="govuk-grid-column-two-thirds" role="status" aria-live="assertive">
            <div id="progress-bar-and-message">
                <p class="govuk-body">Do not close your browser window while your files are being uploaded. This could take a few minutes.</p>
                <div>
                    <span id="upload-status-screen-reader">
                        <label for="upload-records-progress-bar" class="govuk-label progress-label">
                            Uploading records <span id="upload-percentage" role="status" aria-live="polite"></span>
                        </label>
                    </span>
                    <div class="progress-bar">
                        <div class="progress-display" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>`

  showUploadingRecordsPage()
}

function setupUploadPageHTMLWithoutProgressBar() {
  document.body.innerHTML =
    `<div id="file-upload" class="govuk-grid-row"></div>
    <div id="upload-error" class="govuk-error-summary upload-error" hidden aria-labelledby="error-summary-title
        role="alert" tabindex="-1" data-module="govuk-error-summary">
        <h2 class="govuk-error-summary__title" id="error-summary-title"></h2>
    </div>`

  showUploadingRecordsPage()
}

function checkExpectedPageState(percentage: String) {
  const fileUploadPage: HTMLDivElement | null =
    document.querySelector("#file-upload")
  const uploadProgressPage: HTMLDivElement | null =
    document.querySelector("#upload-progress")

  const progressLabelElement: HTMLDivElement | null =
    document.querySelector("#upload-percentage")

  const progressBarElement: HTMLDivElement | null =
    document.querySelector(".progress-display")

  const uploadError: HTMLDivElement | null =
    document.querySelector("#upload-error")

  expect(uploadProgressPage && uploadProgressPage.classList.toString()).toEqual(
    "govuk-grid-row"
  )

  expect(fileUploadPage && fileUploadPage.getAttribute("hidden")).toEqual(
    "true"
  )

  expect(uploadError && uploadError.getAttribute("hidden")).toEqual("")

  expect(progressBarElement).not.toBeNull()

  expect(progressLabelElement && progressLabelElement?.innerText).toEqual(
    `${percentage}%`
  )
}

function checkS3UploadProgressBarState(percentage: String) {
  const progressLabelElement: HTMLDivElement | null =
    document.querySelector("#upload-percentage")

  const progressBarElement: HTMLDivElement | null =
    document.querySelector(".progress-display")

  expect(progressBarElement).not.toBeNull()

  expect(progressLabelElement && progressLabelElement?.innerText).toEqual(
    `${percentage}%`
  )
}

function checkNoPageStateChangeExpected() {
  const fileUploadPage: HTMLDivElement | null =
    document.querySelector("#file-upload")

  expect(fileUploadPage && fileUploadPage.classList.toString()).toEqual(
    "govuk-grid-row"
  )
}
