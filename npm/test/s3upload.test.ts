import { ITdrFileWithPath, S3Upload } from "../src/s3upload"
import { isError } from "../src/errorhandling"
import { enableFetchMocks } from "jest-fetch-mock"
import {
  IFileWithPath,
  IProgressInformation
} from "@nationalarchives/file-information"
import { mockClient, mockLibStorageUpload } from "aws-sdk-client-mock"
import { S3Client, ServiceInputTypes } from "@aws-sdk/client-s3"

enableFetchMocks()

interface createTdrFileParameters {
  fileId?: string
  bits?: string
  filename?: string
  fileSize?: number
}

interface ITdrFileWithPathAndBits extends ITdrFileWithPath {
  bits: Buffer
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
      if (count == 0) {
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

  const fileWithPath: IFileWithPath = {
    file: file,
    path: filename
  }
  return {
    fileId,
    bits: Buffer.from(bits),
    fileWithPath: fileWithPath
  } as ITdrFileWithPathAndBits
}

test("a single file upload returns the correct key", async () => {
  const tdrFileWithPath = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFileWithPath],
    jest.fn(),
    ""
  )
  let input = mockUpload.call(0).args[0].input as { Key: string }

  expect(input.Key).toEqual(
    `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/1df92708-d66b-4b55-8c1e-bb945a5c4fb5`
  )
})

test("a single file upload calls the callback correctly", async () => {
  const tdrFileWithPath = createTdrFile({})
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFileWithPath],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [100])
})

test("multiple file uploads return the correct params", async () => {
  const callback = jest.fn()
  const tdrFileWithPath1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const tdrFileWithPath2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1"
  })
  const tdrFileWithPath3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863"
  })
  const tdrFileWithPath4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102"
  })
  const files: ITdrFileWithPathAndBits[] = [
    tdrFileWithPath1,
    tdrFileWithPath2,
    tdrFileWithPath3,
    tdrFileWithPath4
  ]
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.reset()
  mockUpload.resolves({})
  const s3Upload = new S3Upload(
    s3Mock as unknown as S3Client,
    "https://tdr-fake-url.com/fake"
  )
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    files,
    callback,
    ""
  )

  const getPutObjectParamsUploaded: (index: number) => ServiceInputTypes = (
    index
  ) => {
    return mockUpload.call(index).args[0].input as ServiceInputTypes
  }
  const fileIds = [
    "1df92708-d66b-4b55-8c1e-bb945a5c4fb5",
    "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1",
    "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    "6b6694d0-814c-4978-8dee-56ec920a0102"
  ]

  fileIds.forEach((fileId) => {
    const fileIndex = fileIds.indexOf(fileId)
    const tdrFileWithPath = files[fileIndex]
    const fileObject = tdrFileWithPath.fileWithPath
    const putObjectParams = getPutObjectParamsUploaded(fileIndex)

    expect(putObjectParams).toEqual({
      Key: `${userId}/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/${fileId}`,
      Bucket: "tdr-fake-url.com/fake",
      ACL: "bucket-owner-read",
      Body: tdrFileWithPath.bits,
      Tagging: `filePath=${encodeURIComponent(fileObject.path)}`
    })
  })
})

test("multiple file uploads call the callback correctly", async () => {
  const files: ITdrFileWithPath[] = [
    createTdrFile({}),
    createTdrFile({}),
    createTdrFile({}),
    createTdrFile({})
  ]
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    files,
    callback,
    ""
  )
  checkCallbackCalls(callback, 4, [25, 50, 75, 100])
})

test("when there is an error with the upload, an error is returned", async () => {
  const tdrFileWithPath = createTdrFile({})
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.rejects("error")
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  const result = s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFileWithPath],
    jest.fn(),
    ""
  )
  await expect(result).rejects.toEqual(Error("error"))
})

test("a single file upload calls the callback correctly with a different chunk size", async () => {
  const tdrFileWithPath = createTdrFile({ fileSize: 10 * 1024 * 1024 })
  const callback = jest.fn()
  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    [tdrFileWithPath],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [50, 100])
})

test("multiple file uploads of more than 0 bytes returns the correct, same number of bytes provided as uploaded", async () => {
  const callback = jest.fn()
  const tdrFileWithPath1 = createTdrFile({})
  const tdrFileWithPath2 = createTdrFile({})
  const tdrFileWithPath3 = createTdrFile({})
  const tdrFileWithPath4 = createTdrFile({})
  const tdrFilesWithPath: ITdrFileWithPath[] = [
    tdrFileWithPath1,
    tdrFileWithPath2,
    tdrFileWithPath3,
    tdrFileWithPath4
  ]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFilesWithPath,
    callback,
    ""
  )
  const byteSizeofAllFiles = tdrFilesWithPath.reduce(
    (fileIdTotal, tdrFileWithPath) =>
      fileIdTotal + tdrFileWithPath.fileWithPath.file.size,
    0
  )
  expect(isError(result)).toBe(false)
  if (!isError(result)) {
    expect(result.totalChunks).toEqual(result.processedChunks)
    expect(result.totalChunks).toEqual(byteSizeofAllFiles)
  }
})

test("multiple 0-byte file uploads returns a totalChunks value that equals the same as the length of 'files'", async () => {
  const callback = jest.fn()
  const tdrFileWithPath1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5",
    bits: ""
  })
  const tdrFileWithPath2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1",
    bits: ""
  })
  const tdrFileWithPath3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    bits: ""
  })
  const tdrFileWithPath4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102",
    bits: ""
  })
  const tdrFilesWithPath: ITdrFileWithPath[] = [
    tdrFileWithPath1,
    tdrFileWithPath2,
    tdrFileWithPath3,
    tdrFileWithPath4
  ]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFilesWithPath,
    callback,
    ""
  )
  expect(isError(result)).toBe(false)
  if (!isError(result)) {
    expect(result.totalChunks).toEqual(4)
    expect(result.totalChunks).toEqual(result.processedChunks)
  }
})

test(`multiple file uploads (some with 0 bytes, some not) returns processedChunks value,
            equal to byte size of files + length of 'files' with 0 bytes`, async () => {
  const callback = jest.fn()
  const tdrFileWithPath1 = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const tdrFileWithPath2 = createTdrFile({
    fileId: "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1"
  })
  const tdrFileWithPath3 = createTdrFile({
    fileId: "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    bits: "bits3"
  })
  const tdrFileWithPath4 = createTdrFile({
    fileId: "6b6694d0-814c-4978-8dee-56ec920a0102",
    bits: "bits4"
  })
  const tdrFilesWithPath: ITdrFileWithPath[] = [
    tdrFileWithPath1,
    tdrFileWithPath2,
    tdrFileWithPath3,
    tdrFileWithPath4
  ]

  const mockUpload = mockLibStorageUpload(s3Mock)
  mockUpload.resolves({})
  const s3Upload = new S3Upload(s3Mock as unknown as S3Client, "")
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    userId,
    tdrFilesWithPath,
    callback,
    ""
  )

  const byteSizeofAllFiles = tdrFilesWithPath.reduce(
    (fileIdTotal, tdrFileWithPath) =>
      fileIdTotal + tdrFileWithPath.fileWithPath.file.size,
    0
  )

  const numberOfFilesWithZeroBytes = tdrFilesWithPath.filter(
    (tdrFileWithPath) => tdrFileWithPath.fileWithPath.file.size == 0
  ).length

  expect(isError(result)).toBe(false)
  if (!isError(result)) {
    expect(result.processedChunks).toEqual(
      byteSizeofAllFiles + numberOfFilesWithZeroBytes
    )
    expect(result.totalChunks).toEqual(
      byteSizeofAllFiles + numberOfFilesWithZeroBytes
    )
  }
})
