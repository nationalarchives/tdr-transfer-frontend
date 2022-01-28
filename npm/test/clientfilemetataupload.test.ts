import { GraphqlClient } from "../src/graphql"
import { DocumentNode, FetchResult } from "@apollo/client/core"
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

const mockMetadataWithoutPath = <IFileMetadata>{
  file: new File([], ""),
  checksum: "checksum3",
  size: 10,
  path: "",
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
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
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
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
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
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const result = await uploadMetadata.saveClientFileMetadata("", metadata, [])

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
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  await expect(
    uploadMetadata.saveClientFileMetadata("", metadata, [])
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

  const inputs: ClientSideMetadataInput[] = [
    input1,
    input2,
    input3,
    input4,
    input5
  ]
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const result = uploadMetadata.createMetadataInputBatches(inputs)
  expect(result).toHaveLength(2)
  expect(result[0]).toEqual([input1, input2, input3])
  expect(result[1]).toEqual([input4, input5])
})

test("createMetadataInputsAndFileMap returns metadata inputs and file map", () => {
  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const { metadataInputs, matchFileMap } =
    uploadMetadata.createMetadataInputsAndFileMap(metadata)

  expect(metadataInputs).toHaveLength(2)
  for (let i = 0; i < metadataInputs.length; i += 1) {
    expect(metadataInputs[i].checksum).toEqual(metadata[i].checksum)
    expect(metadataInputs[i].fileSize).toEqual(metadata[i].size)
    expect(metadataInputs[i].originalPath).toEqual(metadata[i].path)
    expect(metadataInputs[i].lastModified).toEqual(
      metadata[i].lastModified.getTime()
    )
  }

  expect(matchFileMap.size).toEqual(2)
  for (let i = 0; i < metadataInputs.length; i += 1) {
    expect(matchFileMap.get(i)).toEqual(metadata[i].file)
  }
})

test("createMetadataInputsAndFileMap removes slash at beginning of metadata input's file path", async () => {
  /*Files selected via drag-and-drop have a leading slash applied to their file path but files added using the 'Browse'
   button do not. This test is to check that any leading slashes are be removed for consistency.*/

  const mockMetadata1WithSlashInPath = <IFileMetadata>{
    ...mockMetadata1,
    path: "/path/to/file1"
  }
  const metadata: IFileMetadata[] = [
    mockMetadata1WithSlashInPath,
    mockMetadata2
  ]
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const { metadataInputs } =
    uploadMetadata.createMetadataInputsAndFileMap(metadata)

  expect(metadataInputs).toHaveLength(2)
  expect(metadataInputs[0].originalPath).toEqual("path/to/file1")
  expect(metadataInputs[1].originalPath).toEqual("path/to/file2")
})

test("createMetadataInputsAndFileMap sets the 'originalPath' to the file name when the 'path' is empty", () => {
  const metadata: IFileMetadata[] = [mockMetadataWithoutPath, mockMetadata1]
  const client = new GraphqlClient("https://example.com", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)

  const { metadataInputs, matchFileMap } =
    uploadMetadata.createMetadataInputsAndFileMap(metadata)

  expect(metadataInputs).toHaveLength(2)

  expect(metadataInputs[0].originalPath).toEqual(metadata[0].file.name)
  expect(metadataInputs[1].originalPath).toEqual(metadata[1].path)
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
