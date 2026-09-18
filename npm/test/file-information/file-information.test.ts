import { extractFileMetadata } from "../../src/file-information/index"
import { IFileWithPath } from "../../src/file-information/types"
import * as fs from "fs"

const blob = new Blob([new Uint8Array(fs.readFileSync("test/file-information/testfile"))])
const file: File = new File([blob], "", { lastModified: 1584027654000 })

const blobLonger = new Blob([
  new Uint8Array(fs.readFileSync("test/file-information/testfilelonger"))
])
const fileLonger = new File([blobLonger], "")

const dummyIFileWithPath = {
  file: file,
  path: "/a/path",
  webkitRelativePath: "/a/path"
} as IFileWithPath

const dummyLongerIFileWithPath = {
  file: fileLonger,
  path: "/a/path",
  webkitRelativePath: "/a/path"
} as IFileWithPath

test("returns the correct checksum for a file", async () => {
  const result = await extractFileMetadata([dummyIFileWithPath])
  expect(result[0].checksum).toEqual(
    "c87e2ca771bab6024c269b933389d2a92d4941c848c52f155b9b84e1f109fe35"
  )
})

test("returns the correct checksum for a file with a chunk size smaller than file size", async () => {
  const result = await extractFileMetadata([dummyLongerIFileWithPath], jest.fn(), 1)
  expect(result[0].checksum).toEqual(
    "d924eb4c776c9443ab3b020f0fdcfeeff3b89bc4e27c62076364588cd0adf527"
  )
})

test("returns correct number of results", async () => {
  const result = await extractFileMetadata([
    dummyIFileWithPath,
    dummyIFileWithPath,
    dummyIFileWithPath
  ])
  expect(result).toHaveLength(3)
})

test("returns the correct file size", async () => {
  const result = await extractFileMetadata([dummyIFileWithPath])
  expect(result[0].size).toEqual(20)
})

test("returns the correct last modified date", async () => {
  const result = await extractFileMetadata([dummyIFileWithPath])
  expect(result[0].lastModified).toEqual(new Date("2020-03-12T15:40:54.000Z"))
})

test("returns the correct path", async () => {
  const result = await extractFileMetadata([dummyIFileWithPath])
  expect(result[0].path).toEqual("/a/path")
})

test("returns the original file", async () => {
  const result = await extractFileMetadata([dummyIFileWithPath])
  expect(result[0].file).toEqual(file)
})

test("calls the callback function correctly", async () => {
  const callback = jest.fn()
  await extractFileMetadata(
    [dummyIFileWithPath, dummyIFileWithPath],
    callback,
    10
  )
  const calls = callback.mock.calls
  expect(calls).toHaveLength(4)
  console.log(calls[0])
  const expectedResults = [
    [0, 25],
    [0, 50],
    [1, 75],
    [1, 100]
  ]

  checkCallbackFunctionCalls(calls, expectedResults)
})

test("calls the callback function correctly for two differently sized files", async () => {
  const callback = jest.fn()
  await extractFileMetadata(
    [dummyIFileWithPath, dummyLongerIFileWithPath],
    callback,
    5
  )
  const calls = callback.mock.calls

  expect(calls).toHaveLength(12)
  const expectedResults = [
    [0, 8],
    [0, 17],
    [0, 25],
    [0, 33],
    [1, 42],
    [1, 50],
    [1, 58],
    [1, 67],
    [1, 75],
    [1, 83],
    [1, 92],
    [1, 100]
  ]
  checkCallbackFunctionCalls(calls, expectedResults)
})

const checkCallbackFunctionCalls: (
  calls: any[],
  expectedResults: number[][]
) => void = (calls, expectedResults) => {
  const totalFiles = 2
  for (let i = 0; i < calls.length; i += 1) {
    const processedFiles = expectedResults[i][0]
    const percentageProcessed = expectedResults[i][1]
    const expectedResult = { totalFiles, processedFiles, percentageProcessed }
    expect(calls[i][0]).toStrictEqual(expectedResult)
  }
}
