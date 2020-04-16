import { KeycloakInstance } from "keycloak-js"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { ClientFileProcessing } from "../src/clientfileprocessing"
import {
  IFileMetadata,
  TdrFile,
  TProgressFunction
} from "@nationalarchives/file-information"
import { ClientFileExtractMetadata } from "../src/clientfileextractmetadata"
import { S3Upload, ITdrFile } from "../src/s3upload"
import { ManagedUpload } from "aws-sdk/clients/s3"

jest.mock("../src/clientfilemetadataupload")
jest.mock("../src/clientfileextractmetadata")
jest.mock("../src/s3upload")

beforeEach(() => jest.resetModules())

class S3UploadMock extends S3Upload {
  uploadToS3: (
    files: ITdrFile[],
    callback: TProgressFunction,
    chunkSize?: number
  ) => Promise<ManagedUpload.SendData[]> = jest.fn()
}

class ClientFileUploadSuccess {
  fileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.resolve(["1", "2"])
  }
  clientFileMetadata: (
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
  fileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.reject(Error("upload client file information error"))
  }
  clientFileMetadata: (
    files: File[],
    fileIds: string[]
  ) => Promise<void> = async (files: File[], fileIds: string[]) => {
    return Promise.resolve()
  }
}

class ClientFileUploadMetadataFailure {
  fileInformation: (
    consignmentId: string,
    numberOfFiles: number
  ) => Promise<string[]> = async (
    consignmentId: string,
    numberOfFiles: number
  ) => {
    return Promise.resolve(["1", "2"])
  }
  clientFileMetadata: (
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

const mockKeycloak: KeycloakInstance<"native"> = {
  init: jest.fn(),
  login: jest.fn(),
  logout: jest.fn(),
  register: jest.fn(),
  accountManagement: jest.fn(),
  createLoginUrl: jest.fn(),
  createLogoutUrl: jest.fn(),
  createRegisterUrl: jest.fn(),
  createAccountUrl: jest.fn(),
  isTokenExpired: jest.fn(),
  updateToken: jest.fn(),
  clearToken: jest.fn(),
  hasRealmRole: jest.fn(),
  hasResourceRole: jest.fn(),
  loadUserInfo: jest.fn(),
  loadUserProfile: jest.fn(),
  token: "fake-auth-token"
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

  const client = new GraphqlClient("test", mockKeycloak)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock("")
  )
  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn())
  ).resolves.not.toThrow()
})

test("file successfully uploaded to s3", async () => {
  mockMetadataExtractSuccess()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloak)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const s3UploadMock = new S3UploadMock("")
  const fileProcessing = new ClientFileProcessing(metadataUpload, s3UploadMock)
  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn())
  ).resolves.not.toThrow()
  expect(s3UploadMock.uploadToS3).toHaveBeenCalledTimes(1)
})

test("Error thrown if processing files fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadFileInformationFailure()

  const client = new GraphqlClient("test", mockKeycloak)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock("")
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn())
  ).rejects.toStrictEqual(
    Error(
      "Processing client files failed: upload client file information error"
    )
  )
})

test("Error thrown if processing file metadata fails", async () => {
  mockMetadataExtractSuccess()
  mockUploadMetadataFailure()

  const client = new GraphqlClient("test", mockKeycloak)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock("")
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn())
  ).rejects.toStrictEqual(
    Error("Processing client files failed: upload client file metadata error")
  )
})

test("Error thrown if extracting file metadata fails", async () => {
  mockMetadataExtractFailure()
  mockMetadataUploadSuccess()

  const client = new GraphqlClient("test", mockKeycloak)
  const metadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )
  const fileProcessing = new ClientFileProcessing(
    metadataUpload,
    new S3UploadMock("")
  )

  await expect(
    fileProcessing.processClientFiles("1", [], jest.fn())
  ).rejects.toStrictEqual(
    Error(
      "Processing client files failed: client file metadata extraction error"
    )
  )
})
