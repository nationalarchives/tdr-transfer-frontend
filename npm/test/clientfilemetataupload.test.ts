import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { isError } from "../src/errorhandling"
import { IFileMetadata } from "@nationalarchives/file-information"
import fetchMock, {enableFetchMocks} from "jest-fetch-mock"
enableFetchMocks()

jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

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

beforeEach(() => {
  document.body.innerHTML='<input name="csrfToken" value="abcde">'
  fetchMock.resetMocks()
  jest.resetModules()
})

test("startUpload returns error if the request to the frontend fails ", async () => {
  fetchMock.mockReject(Error("Error from frontend"))
  const uploadMetadata = new ClientFileMetadataUpload()

  await expect(
    uploadMetadata.startUpload({
      consignmentId: "1",
      parentFolder: "TEST PARENT FOLDER NAME",
      includeTopLevelFolder: false
    })
  ).resolves.toStrictEqual(Error("Error: Error from frontend"))
})

test("startUpload returns error if the http status is not OK", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})
  const uploadMetadata = new ClientFileMetadataUpload()
  await expect(
    uploadMetadata.startUpload({
      consignmentId: "1",
      parentFolder: "TEST PARENT FOLDER NAME",
      includeTopLevelFolder: false
    })
  ).resolves.toStrictEqual(Error("Start upload failed: There was an error"))
})

test("saveClientFileMetadata uploads client file metadata", async () => {
  fetchMock.mockResponse(JSON.stringify([
    { fileId: "0", matchId: 0 },
    { fileId: "1", matchId: 1 }
  ]))

  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const uploadMetadata = new ClientFileMetadataUpload()

  const result = await uploadMetadata.saveClientFileMetadata("", metadata, [])

  expect(fetchMock).toHaveBeenCalled()
  expect(result).toHaveLength(2)

  expect(isError(result)).toBe(false)
  if(!isError(result)) {
    expect(result[0].fileId).toBe("0")
    expect(result[1].fileId).toBe("1")
  }
})

test("saveClientFileMetadata uploads any empty folders", async () => {
  fetchMock.mockResponse(JSON.stringify([
    { fileId: "0", matchId: 0 },
    { fileId: "1", matchId: 1 }
  ]))
  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const uploadMetadata = new ClientFileMetadataUpload()

  const emptyDirectories = ["empty1", "empty2"];
  await uploadMetadata.saveClientFileMetadata("", metadata, emptyDirectories)
  const requestBody = JSON.parse(fetchMock.mock.calls[0][1]!.body!.toString()) as {emptyDirectories: string}
  expect(requestBody.emptyDirectories).toEqual(emptyDirectories)
})

test("saveClientFileMetadata fails to upload client file metadata", async () => {
  fetchMock.mockResponse("error", {statusText: "There was an error", status: 500})

  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const uploadMetadata = new ClientFileMetadataUpload()

  await expect(
    uploadMetadata.saveClientFileMetadata("", metadata, [])
  ).resolves.toStrictEqual(
    Error("Add client file metadata failed: There was an error")
  )
})

test("createMetadataInputsAndFileMap returns metadata inputs and file map", () => {
  const metadata: IFileMetadata[] = [mockMetadata1, mockMetadata2]
  const uploadMetadata = new ClientFileMetadataUpload()

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
    expect(matchFileMap.get(i)?.file).toEqual(metadata[i].file)
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
  const uploadMetadata = new ClientFileMetadataUpload()

  const { metadataInputs } =
    uploadMetadata.createMetadataInputsAndFileMap(metadata)

  expect(metadataInputs).toHaveLength(2)
  expect(metadataInputs[0].originalPath).toEqual("path/to/file1")
  expect(metadataInputs[1].originalPath).toEqual("path/to/file2")
})

test("createMetadataInputsAndFileMap sets the 'originalPath' to the file name when the 'path' is empty", () => {
  const metadata: IFileMetadata[] = [mockMetadataWithoutPath, mockMetadata1]
  const uploadMetadata = new ClientFileMetadataUpload()

  const { metadataInputs } =
    uploadMetadata.createMetadataInputsAndFileMap(metadata)

  expect(metadataInputs).toHaveLength(2)

  expect(metadataInputs[0].originalPath).toEqual(metadata[0].file.name)
  expect(metadataInputs[1].originalPath).toEqual(metadata[1].path)
})
