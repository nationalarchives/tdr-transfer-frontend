import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "apollo-boost"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { IFileMetadata } from "@nationalarchives/file-information"
import { GraphQLError } from "graphql"
import { mockKeycloakInstance } from "./utils"
import { ClientSideMetadataInput } from "@nationalarchives/tdr-generated-graphql"

jest.mock("../src/graphql")

type IMockAddFileData = {
  addFilesAndMetadata: [{ fileId: string; matchId: number }]
} | null
type IMockFileMatches = {
  fileId: string
  matchId: number
}
type IMockAddFileMetadata = {
  addFilesAndMetadata: IMockFileMatches[]
}

type TMockVariables = string

const mockMetadata1 = <IFileMetadata>{
  file: new File([], ""),
  checksum: "checksum1",
  size: 10,
  path: "path/to/file1",
  lastModified: new Date()
}
const mockMetadata2 = <IFileMetadata>{
  file: new File([], ""),
  checksum: "checksum2",
  size: 10,
  path: "path/to/file2",
  lastModified: new Date()
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
      addFilesAndMetadata: [
        { fileId: "0", matchId: 0 },
        { fileId: "1", matchId: 1 }
      ]
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

class GraphqlClientDataErrorAddFiles {
  mutation: (
    query: DocumentNode,
    variables: TMockVariables
  ) => Promise<FetchResult<IMockAddFileData>> = async (
    query: DocumentNode,
    variables: TMockVariables
  ) => {
    return {
      errors: [new GraphQLError("error 1"), new GraphQLError("error 2")]
    }
  }
}

beforeEach(() => jest.resetModules())

const mockFailureAddFiles: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailureAddFiles()
  })
}

const mockDataErrorsAddFiles: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientDataErrorAddFiles()
  })
}

const mockFailureAddMetadata: () => void = () => {
  const mock = GraphqlClient as jest.Mock
  mock.mockImplementation(() => {
    return new GraphqlClientFailureAddMetadata()
  })
}

test("startUpload returns error if no data returned", async () => {
  mockFailureAddFiles()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  await expect(
    uploadMetadata.startUpload({
      consignmentId: "1",
      parentFolder: "TEST PARENT FOLDER NAME"
    })
  ).rejects.toStrictEqual(Error("Start upload failed: no data"))
})

test("startUpload returns error if returned data contains errors", async () => {
  mockDataErrorsAddFiles()
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  await expect(
    uploadMetadata.startUpload({
      consignmentId: "1",
      parentFolder: "TEST PARENT FOLDER NAME"
    })
  ).rejects.toStrictEqual(Error("Start upload failed: error 1,error 2"))
})

test("saveClientFileMetadata uploads client file metadata", async () => {
  const mockGraphqlClient = GraphqlClient as jest.Mock
  mockGraphqlClient.mockImplementation(() => {
    return new GraphqlClientSuccessAddMetadata()
  })

  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const result = await uploadMetadata.saveClientFileMetadata("", metadata)

  expect(mockGraphqlClient).toHaveBeenCalled()
  expect(result).toHaveLength(2)

  const result1 = result[0]
  expect(result1.fileId).toBe("0")

  const result2 = result[1]
  expect(result2.fileId).toBe("1")
})

test("saveClientFileMetadata fails to upload client file metadata", async () => {
  mockFailureAddMetadata()

  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  await expect(
    uploadMetadata.saveClientFileMetadata("", metadata)
  ).rejects.toStrictEqual(
    Error("Add client file metadata failed: error 1,error 2")
  )
})

test("createMetadataInputBatches generates batches of the defined size", () => {
  const input1 = generateMockMetadataInput(1)
  const input2 = generateMockMetadataInput(2)
  const input3 = generateMockMetadataInput(3)
  const input4 = generateMockMetadataInput(4)
  const input5 = generateMockMetadataInput(5)

  const inputs: ClientSideMetadataInput[] = [input1, input2, input3, input4, input5]
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const result = uploadMetadata.createMetadataInputBatches(inputs)
  expect(result).toHaveLength(2)
  expect(result[0]).toEqual([input1, input2, input3])
  expect(result[1]).toEqual([input4, input5])
})

function generateMockMetadataInput(matchId: number): ClientSideMetadataInput {
  return {
    lastModified: Date.now(),
    fileSize: 10,
    originalPath: "path",
    checksum: "checksum",
    matchId
  }
}
