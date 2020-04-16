import { PutObjectRequest } from "aws-sdk/clients/s3"
import { ManagedUpload } from "aws-sdk/clients/s3"
import { S3Upload, ITdrFile } from "../src/s3upload"
import { IProgressInformation } from "@nationalarchives/file-information"

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
  private chunkSize: number
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
  arr: number[]
) => void = (callback, totalFiles, arr) => {
  for (let i = 0; i < arr.length; i++) {
    const percentageProcessed = arr[i]
    const expectedResult: IProgressInformation = {
      percentageProcessed,
      totalFiles,
      processedFiles: Math.floor((percentageProcessed / 100) * totalFiles)
    }
    expect(callback).toHaveBeenNthCalledWith(i + 1, expectedResult)
  }
}

test("a single file upload returns the correct key", async () => {
  const file = new File(["file1"], "file1")
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    jest.fn(),
    1
  )
  expect(result[0].Key).toEqual(
    "identityId/1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  )
})

test("a single file upload calls the callback correctly", async () => {
  const file = new File(["file1"], "file1")
  const callback = jest.fn()
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockSuccessfulS3()
  await s3Upload.uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    callback,
    1
  )
  checkCallbackCalls(callback, 1, [20, 40, 60, 80, 100])
})

test("multiple file uploads return the correct keys", async () => {
  const fileIds = [
    "1df92708-d66b-4b55-8c1e-bb945a5c4fb5",
    "5a99961c-cb5b-4c76-8c9d-d7d2ca4e85b1",
    "56b34fbb-2eac-401e-a89a-0dc9b2013863",
    "6b6694d0-814c-4978-8dee-56ec920a0102"
  ]
  const callback = jest.fn()
  const file1 = new File(["file1"], "file1")
  const file2 = new File(["file2"], "file2")
  const file3 = new File(["file3"], "file3")
  const file4 = new File(["file4"], "file4")
  const files: ITdrFile[] = [
    { fileId: fileIds[0], file: file1 },
    { fileId: fileIds[1], file: file2 },
    { fileId: fileIds[2], file: file3 },
    { fileId: fileIds[3], file: file4 }
  ]
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockSuccessfulS3()
  const result = await s3Upload.uploadToS3(files, callback)
  expect(result[0].Key).toEqual(`identityId/${fileIds[0]}`)
  expect(result[1].Key).toEqual(`identityId/${fileIds[1]}`)
  expect(result[2].Key).toEqual(`identityId/${fileIds[2]}`)
  expect(result[3].Key).toEqual(`identityId/${fileIds[3]}`)
})

test("multiple file uploads call the callback correctly", async () => {
  const files: ITdrFile[] = [
    { fileId: "", file: new File(["file1"], "file1") },
    { fileId: "", file: new File(["file2"], "file2") },
    { fileId: "", file: new File(["file3"], "file3") },
    { fileId: "", file: new File(["file4"], "file4") }
  ]
  const callback = jest.fn()
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockSuccessfulS3()
  await s3Upload.uploadToS3(files, callback, 1)
  checkCallbackCalls(callback, 4, [
    5,
    10,
    15,
    20,
    25,
    30,
    35,
    40,
    45,
    50,
    55,
    60,
    65,
    70,
    75,
    80,
    85,
    90,
    95,
    100
  ])
})

test("when there is an error with the upload, an error is returned", async () => {
  const fileId = "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  const file = new File(["file1"], "file1")
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockFailedS3()
  const result = s3Upload.uploadToS3([{ file, fileId }], jest.fn())
  await expect(result).rejects.toEqual("error")
})

test("a single file upload calls the callback correctly with a different chunk size", async () => {
  const file = new File(["file1"], "file1")
  const callback = jest.fn()
  const s3Upload = new S3Upload("identityId")
  s3Upload.s3 = new MockSuccessfulS3(2)
  await s3Upload.uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    callback,
    2
  )
  checkCallbackCalls(callback, 1, [20, 60, 100])
})
