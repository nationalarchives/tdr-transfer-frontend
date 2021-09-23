import { ManagedUpload, PutObjectRequest } from "aws-sdk/clients/s3"
import { ITdrFile, S3Upload } from "../src/s3upload"
import { IProgressInformation } from "@nationalarchives/file-information"

interface createTdrFileParameters {
  fileId?: string
  bits?: string
  filename?: string
}

class MockFailedS3 {
  upload(_: PutObjectRequest) {
    return {
      abort: jest.fn(),
      send: jest.fn(),
      on: jest.fn(),
      promise: () =>
        new Promise<ManagedUpload.SendData>((_, reject) => reject("error"))
    }
  }
}

class MockSuccessfulS3 {
  private readonly chunkSize: number

  constructor(chunkSize?: number) {
    this.chunkSize = chunkSize ? chunkSize : 1
  }

  upload(obj: PutObjectRequest) {
    return {
      abort: jest.fn(),
      send: jest.fn(),
      promise: () => {
        return new Promise<ManagedUpload.SendData>((resolve, _) =>
          resolve({ Key: obj.Key, Location: "", ETag: "", Bucket: "" })
        )
      },
      on: (
        _: "httpUploadProgress",
        listener: (progress: ManagedUpload.Progress) => void
      ) => {
        const total = (obj.Body as File).size
        for (let i = 0; i < total; i += this.chunkSize) {
          listener({ loaded: i + 1, total })
        }
      }
    }
  }
}

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
  bits = "file1",
  filename = "file1"
}: createTdrFileParameters) => {
  const file = new File([bits], filename)
  return {
    fileId: fileId,
    file: file
  }
}

test("a single file upload returns the correct key", async () => {
  const tdrFile = createTdrFile({
    fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  })
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    [tdrFile],
    jest.fn(),
    ""
  )
  expect(result.sendData[0].Key).toEqual(
    "identityId/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  )
})

test("a single file upload calls the callback correctly", async () => {
  const tdrFile = createTdrFile({})
  const callback = jest.fn()
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    [tdrFile],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [20, 40, 60, 80, 100])
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
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    files,
    callback,
    ""
  )
  expect(result.sendData[0].Key).toEqual(
    `identityId/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/1df92708-d66b-4b55-8c1e-bb945a5c4fb5`
  )
  expect(result.sendData[1].Key).toEqual(
    `identityId/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1`
  )
  expect(result.sendData[2].Key).toEqual(
    `identityId/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/56b34fbb-2eac-401e-a89a-0dc9b2013863`
  )
  expect(result.sendData[3].Key).toEqual(
    `identityId/16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e/6b6694d0-814c-4978-8dee-56ec920a0102`
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
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    files,
    callback,
    ""
  )
  checkCallbackCalls(
    callback,
    4,
    [
      5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95,
      100
    ]
  )
})

test("when there is an error with the upload, an error is returned", async () => {
  const tdrFile = createTdrFile({})
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockFailedS3()
  const result = s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    [tdrFile],
    jest.fn(),
    ""
  )
  await expect(result).rejects.toEqual("error")
})

test("a single file upload calls the callback correctly with a different chunk size", async () => {
  const tdrFile = createTdrFile({})
  const callback = jest.fn()
  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3(2)
  await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    [tdrFile],
    callback,
    ""
  )
  checkCallbackCalls(callback, 1, [20, 60, 100])
})

test("multiple file uploads of more than 0 bytes returns the correct, same number of bytes provided as uploaded", async () => {
  const callback = jest.fn()
  const tdrFile1 = createTdrFile({})
  const tdrFile2 = createTdrFile({})
  const tdrFile3 = createTdrFile({})
  const tdrFile4 = createTdrFile({})
  const tdrFiles: ITdrFile[] = [tdrFile1, tdrFile2, tdrFile3, tdrFile4]

  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
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

  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
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

  const s3Upload = new S3Upload("identityId", "region")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
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
