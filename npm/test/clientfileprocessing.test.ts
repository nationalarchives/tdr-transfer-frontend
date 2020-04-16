import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { ClientFileProcessing } from "../src/clientfileprocessing"
import { IFileMetadata, TdrFile } from "@nationalarchives/file-information"
import { ClientFileExtractMetadata } from "../src/clientfileextractmetadata"
import { mockKeycloakInstance } from "./utils"

jest.mock("../src/clientfilemetadataupload")
jest.mock("../src/clientfileextractmetadata")

beforeEach(() => jest.resetModules())

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

test("client file metadata successfully uploaded", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(metadataUpload)

  await expect(
    fileProcessing.processClientFiles("1", [])
  ).resolves.not.toThrow()
})

test("Error thrown if processing files fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadFileInformationFailure()

  const client = new GraphqlClient("test", mockKeycloakInstance)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(metadataUpload)

  await expect(
    fileProcessing.processClientFiles("1", [])
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
  const fileProcessing = new ClientFileProcessing(metadataUpload)

  await expect(
    fileProcessing.processClientFiles("1", [])
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
  const fileProcessing = new ClientFileProcessing(metadataUpload)

  await expect(
    fileProcessing.processClientFiles("1", [])
  ).rejects.toStrictEqual(
    Error(
      "Processing client files failed: client file metadata extraction error"
    )
  )
})
