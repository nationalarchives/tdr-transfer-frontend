import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "apollo-boost"
import { KeycloakInstance } from "keycloak-js"
import { ClientFileProcessing } from "../src/clientprocessing"
//import * as fileinformation from "@nationalarchives/file-information";
import {
  TChunkProgressFunction,
  TdrFile,
  TFileMetadata
} from "@nationalarchives/file-information"

jest.mock("../src/graphql")
jest.mock("@nationalarchives/file-information")

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

type IFileMetadata = {}

type TMockVariables = string

class FileInformationSuccess {
  extractFileMetadata: (
    files: File[],
    progressFunction: TChunkProgressFunction,
    chunkSizeBytes: number
  ) => Promise<IFileMetadata[]> = async (
    files: File[],
    progressFunction: TChunkProgressFunction,
    chunkSizeBytes: number
  ) => {
    const data: IFileMetadata[] = []
    return data
  }
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

// const mockSuccessExtractFileMetadata: () => void = () => {
//   const mock = TFileMetadata as jest.Mock
//
// }

const mockSuccessAddMetadata: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientSuccessAddMetadata()
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

test("extractClientFileMetadata returns list of file metadata", async () => {
  //const spy = jest.spyOn(fileinformation, "extractFileMetadata")
  //spy.mockResolvedValue(IFileMetadata[]).

  mockSuccessAddMetadata()
  const clientSideFiles: TdrFile[] = []
  const fileIds: number[] = []
  const client = new GraphqlClient("https://test.im", mockKeycloak)
  const processing = new ClientFileProcessing(client)
  const result = await processing.processMetadata(clientSideFiles, fileIds)

  //expect(spy).toBeCalledTimes(1)

  //spy.mockRestore()
})
