import {vi} from "vitest";

const mockFileInformation = {
    extractFileMetadata: vi.fn()
}

import { IFileMetadata } from "@nationalarchives/file-information"
import { ClientFileExtractMetadata } from "../src/clientfileextractmetadata"
import { isError } from "../src/errorhandling"

vi.mock("@nationalarchives/file-information", () => mockFileInformation)
vi.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')

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

beforeEach(() => vi.resetModules())

test("extract function returns list of client file metadata", async () => {
  mockFileInformation.extractFileMetadata.mockImplementation(() => {
    return Promise.resolve([mockMetadata1, mockMetadata2])
  })

  const extractMetadata = new ClientFileExtractMetadata()
  const result = await extractMetadata.extract([], vi.fn())
  expect(isError(result)).toBe(false)
  if(!isError(result)) {
    expect(result).toHaveLength(2)
    expect(result[0]).toStrictEqual(mockMetadata1)
    expect(result[1]).toStrictEqual(mockMetadata2)
  }
})

test("extract function throws error if client file metadata extraction failed", async () => {
  mockFileInformation.extractFileMetadata.mockImplementation(() => {
    return Promise.reject(Error("Some error"))
  })

  const extractMetadata = new ClientFileExtractMetadata()
  await expect(
    extractMetadata.extract([], function() {})
  ).resolves.toStrictEqual(
    Error("Client file metadata extraction failed: Some error")
  )
})
