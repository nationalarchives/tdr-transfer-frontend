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
    "e2d0fe1585a63ec6009c8016ff8dda8b17719a637405a4e23c0ff81339148249"
  )
})

test("returns the correct checksum for a file with a chunk size smaller than file size", async () => {
  const result = await extractFileMetadata([dummyLongerIFileWithPath], jest.fn(), 1)
  expect(result[0].checksum).toEqual(
    "1aa3cecdf8a1dfa8a89a0016ab986ef066128af9cb22e9391169c4878238de54"
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
  expect(result[0].size).toEqual(19)
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
