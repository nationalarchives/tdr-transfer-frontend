const mockFileInformation = {
  extractFileMetadata: jest.fn()
}

import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "apollo-boost"
import { KeycloakInstance } from "keycloak-js"
import { ClientFileProcessing } from "../src/clientprocessing"
import { IFileMetadata, TdrFile } from "@nationalarchives/file-information"
import { GraphQLError } from "graphql"

jest.mock("../src/graphql")
jest.mock("@nationalarchives/file-information", () => mockFileInformation)

type IMockAddFileData = { addFiles: { fileIds: number[] } } | null
type IMockAddFileMetadata = {
  addClientFileMetadata: {
    fileId: number
    checksum: string
    filePath: string
    lastModified: number
    size: number
  }
} | null

type TMockVariables = string

const mockMetadata1 = <IFileMetadata>{
  checksum: "checksum1",
  size: 10,
  path: "path/to/file1",
  lastModified: new Date()
}
const mockMetadata2 = <IFileMetadata>{
  checksum: "checksum2",
  size: 10,
  path: "path/to/file2",
  lastModified: new Date()
}

class GraphqlClientSuccessAddFiles {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockAddFileData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockAddFileData = { addFiles: { fileIds: [1, 2, 3] } }
    return { data }
  }
}

class GraphqlClientSuccessAddMetadata {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockAddFileMetadata>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockAddFileMetadata = {
      addClientFileMetadata: {
        fileId: 1,
        checksum: "checksum123",
        filePath: "file/path",
        lastModified: 1,
        size: 10
      }
    }
    return { data }
  }
}

class GraphqlClientFailureAddMetadata {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockAddFileMetadata>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    return {
      errors: [new GraphQLError("error 1"), new GraphQLError("error 2")]
    }
  }
}

class GraphqlClientFailureAddFiles {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockAddFileData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    const data: IMockAddFileData = null
    return { data }
  }
}

beforeEach(() => jest.resetModules())

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

const mockSuccessAddFile: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientSuccessAddFiles()
  })
}

const mockFailureAddFiles: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailureAddFiles()
  })
}

const mockSuccessAddMetadata: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientSuccessAddMetadata()
  })
}

const mockFailureAddMetadata: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailureAddMetadata()
  })
}

test("processFiles returns list of file ids", async () => {
  mockSuccessAddFile()
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)
  const result = await processing.processFiles(1, 3)

  expect(result).toEqual([1, 2, 3])
})

test("processFiles returns error if no data returned", async () => {
  mockFailureAddFiles()
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)
  await expect(processing.processFiles(1, 3)).rejects.toStrictEqual(
    Error("Add files failed")
  )
})

test("processClientFileMetadata adds extracted client file metadata to database", async () => {
  const spyExtractFileMetadata = jest
    .spyOn(mockFileInformation, "extractFileMetadata")
    .mockImplementation(() => Promise.resolve([mockMetadata1, mockMetadata2]))

  mockSuccessAddMetadata()

  const clientSideFiles: TdrFile[] = []
  const fileIds: number[] = [1, 2]
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)
  await processing.processClientFileMetadata(clientSideFiles, fileIds)

  expect(spyExtractFileMetadata).toBeCalledTimes(1)

  spyExtractFileMetadata.mockRestore()
})

test("extractFileMetadata throws error", async () => {
  jest
    .spyOn(mockFileInformation, "extractFileMetadata")
    .mockImplementation(() => {
      throw Error("some error message")
    })

  mockSuccessAddMetadata()

  const clientSideFiles: TdrFile[] = []
  const fileIds: number[] = [1, 2]
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)

  await expect(
    processing.processClientFileMetadata(clientSideFiles, fileIds)
  ).rejects.toStrictEqual(Error("some error message"))
})

test("processClientFileMetadata fails to add extracted client file metadata to database", async () => {
  const spyExtractFileMetadata = jest
    .spyOn(mockFileInformation, "extractFileMetadata")
    .mockImplementation(() => Promise.resolve([mockMetadata1, mockMetadata2]))

  mockFailureAddMetadata()

  const clientSideFiles: TdrFile[] = []
  const fileIds: number[] = [1, 2]
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)

  const expectedErrorMessage =
    "Add client file metadata failed for file 1: error 1,error 2"

  await expect(
    processing.processClientFileMetadata(clientSideFiles, fileIds)
  ).rejects.toStrictEqual(Error(expectedErrorMessage))
})
