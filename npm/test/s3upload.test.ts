import { ITdrFile, S3Upload } from "../src/s3upload"
import { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { IProgressInformation } from "@nationalarchives/file-information"
import { mockLibStorageUpload, mockClient } from "aws-sdk-client-mock"
import { S3Client } from "@aws-sdk/client-s3"

interface createTdrFileParameters {
  fileId?: string
  bits?: string
  filename?: string
  fileSize?: number
}

const s3Mock = mockClient(S3Client)

const userId = "b088d123-1280-4959-91ca-74858f7ba226"

const checkCallbackCalls: (
  callback: jest.Mock,
  totalFiles: number,
  percentages: number[]
) => void = (callback, totalFiles, percentages) => {
  for (let i = 0; i < percentages.length; i++) {
    const percentageProcessed = percentages[i]
    const expectedResult: IProgressInformation = {
      percentageProcessed,
      totalFiles,
      processedFiles: Math.floor((percentageProcessed / 100) * totalFiles)
    }
    expect(callback).toHaveBeenNthCalledWith(i + 1, expectedResult)
  }
}

const createTdrFile = ({
  fileId = "",
  fileSize = 5,
  bits = "a".repeat(fileSize),
  filename = "file1"
}: createTdrFileParameters) => {
  let count = 1
  const mockReader: ReadableStreamDefaultReader = {
    cancel(_: any) {
      return Promise.resolve()
    },
    closed: Promise.resolve(undefined),
    read() {
      if(count == 0) {
        return Promise.resolve({
          done: true,
          value: undefined
        })
      } else {
        count = count - 1
        return Promise.resolve({
          done: false,
          value: bits
        })
      }
    },
    releaseLock(): void {}
  }

  const mockStream: ReadableStream = {
    getReader() {
      return mockReader
    },
    pipeThrough<T>(
      _: ReadableWritablePair<T>,
      __: StreamPipeOptions | undefined
    ) {
      return this
    },
    pipeTo(_: WritableStream, __: StreamPipeOptions | undefined) {
      return Promise.resolve()
    },
    tee() {
      return [this, this]
    },
    locked: false,
    cancel(_?: any) {
      return Promise.resolve()
    }
  }
  const file = new File([bits], filename)
  file.stream = () => mockStream
  return {
    fileId,
    file: file
  }
}

test("a single file upload returns the correct key", async () => {
  const tdrFile = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFile],
    jest.fn(),
    ""
  )
  let input = mockUpload.call(0).args[0].input as {Key: string}

  expect(input.Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/1df92708-d66b-4b55-8c1e-bb945a5c4fb5`
  )
})

test("a single file upload calls the callback correctly", async () => {
  const tdrFile = createTdrFile({})
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFile],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [100])
})

test("multiple file uploads return the correct keys", async () => {
  const callback = jest.fn()
  const tdrFile1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const tdrFile2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1"
  })
  const tdrFile3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863"
  })
  const tdrFile4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102"
  })
  const files: ITdrFile[] = [tdrFile1, tdrFile2, tdrFile3, tdrFile4]
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.reset()
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    files,
    callback,
    ""
  )
  const input: (count: number) => { Key: string } = count => mockUpload.call(count).args[0].input as {Key: string}
  expect(input(0).Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/1df92708-d66b-4b55-8c1e-bb945a5c4fb5`
  )
  expect(input(1).Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1`
  )
  expect(input(2).Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/56b34fbb-2eac-401e-a89a-0dc9b2013863`
  )
  expect(input(3).Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/6b6694d0-814c-4978-8dee-56ec920a0102`
  )
})

test("multiple file uploads call the callback correctly", async () => {
  const files: ITdrFile[] = [
    createTdrFile({}),
    createTdrFile({}),
    createTdrFile({}),
    createTdrFile({})
  ]
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    files,
    callback,
    ""
  )
  checkCallbackCalls(
    callback,
    4,
    [25, 50, 75, 100]
  )
})

test("when there is an error with the upload, an error is returned", async () => {
  const tdrFile = createTdrFile({})
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.rejects("error")
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  const result = s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFile],
    jest.fn(),
    ""
  )
  await expect(result).rejects.toEqual(Error("error"))
})

test("a single file upload calls the callback correctly with a different chunk size", async () => {
  const tdrFile = createTdrFile({fileSize: 10 * 1024 * 1024})
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFile],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [50, 100])
})

test("multiple file uploads of more than 0 bytes returns the correct, same number of bytes provided as uploaded", async () => {
  const callback = jest.fn()
  const tdrFile1 = createTdrFile({})
  const tdrFile2 = createTdrFile({})
  const tdrFile3 = createTdrFile({})
  const tdrFile4 = createTdrFile({})
  const tdrFiles: ITdrFile[] = [tdrFile1, tdrFile2, tdrFile3, tdrFile4]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFiles,
    callback,
    ""
  )
  const byteSizeofAllFiles = tdrFiles.reduce(
    (fileIdTotal, tdrFile) => fileIdTotal + tdrFile.file.size,
    0
  )

  expect(result.totalChunks).toEqual(result.processedChunks)
  expect(result.totalChunks).toEqual(byteSizeofAllFiles)
})

test("multiple 0-byte file uploads returns a totalChunks value that equals the same as the length of 'files'", async () => {
  const callback = jest.fn()
  const tdrFile1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5",
    bits: ""
  })
  const tdrFile2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1",
    bits: ""
  })
  const tdrFile3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    bits: ""
  })
  const tdrFile4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102",
    bits: ""
  })
  const tdrFiles: ITdrFile[] = [tdrFile1, tdrFile2, tdrFile3, tdrFile4]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFiles,
    callback,
    ""
  )

  expect(result.totalChunks).toEqual(4)
  expect(result.totalChunks).toEqual(result.processedChunks)
})

test(`multiple file uploads (some with 0 bytes, some not) returns processedChunks value,
            equal to byte size of files + length of 'files' with 0 bytes`, async () => {
  const callback = jest.fn()
  const tdrFile1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const tdrFile2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1"
  })
  const tdrFile3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    bits: "bits3"
  })
  const tdrFile4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102",
    bits: "bits4"
  })
  const tdrFiles: ITdrFile[] = [tdrFile1, tdrFile2, tdrFile3, tdrFile4]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client)
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFiles,
    callback,
    ""
  )

  const byteSizeofAllFiles = tdrFiles.reduce(
    (fileIdTotal, tdrFile) => fileIdTotal + tdrFile.file.size,
    0
  )

  const numberOfFilesWithZeroBytes = tdrFiles.filter(
    (tdrFile) => tdrFile.file.size == 0
  ).length

  expect(result.processedChunks).toEqual(
    byteSizeofAllFiles + numberOfFilesWithZeroBytes
  )
  expect(result.totalChunks).toEqual(
    byteSizeofAllFiles + numberOfFilesWithZeroBytes
  )
})
