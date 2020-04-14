jest.mock("../src/auth")

import { uploadToS3, ITdrFile } from "../src/upload"
import { getAuthenticatedUploadObject } from "../src/auth"
import { PutObjectRequest } from "aws-sdk/clients/s3"
import { ManagedUpload } from "aws-sdk/clients/s3"

class MockSuccessfulS3 {
  private chunkSize: number
  constructor(chunkSize: number) {
    this.chunkSize = chunkSize
  }
  upload(obj: PutObjectRequest) {
    return {
      promise: () => {
        return {
          Key: obj.Key
        }
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

class MockFailedS3 {
  upload(_: PutObjectRequest) {
    return {
      on: jest.fn(),
      promise: () => new Promise((_, reject) => reject("error"))
    }
  }
}

beforeEach(() => {})

afterEach(() => {
  jest.resetAllMocks()
})

const mockSuccessfulS3Upload: (chunkSize?: number) => void = (
  chunkSize = 1
) => {
  const authenticationUploadObject = getAuthenticatedUploadObject as jest.Mock
  const identityId = "identityId"
  const s3 = new MockSuccessfulS3(chunkSize)
  authenticationUploadObject.mockImplementation(() => {
    return {
      s3,
      identityId
    }
  })
}

const mockFailedS3Upload: () => void = () => {
  const authenticationUploadObject = getAuthenticatedUploadObject as jest.Mock
  const s3 = new MockFailedS3()
  const identityId = "identityId"
  authenticationUploadObject.mockImplementation(() => {
    return {
      s3,
      identityId
    }
  })
}

const checkCallbackCalls: (callback: jest.Mock, arr: number[]) => void = (
  callback,
  arr
) => {
  for (let i = 0; i < arr.length; i++) {
    expect(callback).toHaveBeenNthCalledWith(i + 1, arr[i])
  }
}

test("a single file upload returns the correct key", async () => {
  mockSuccessfulS3Upload()
  const file = new File(["file1"], "file1")
  const result = await uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    "",
    jest.fn(),
    1
  )
  expect(result[0].Key).toEqual(
    "identityId/1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  )
})

test("a single file upload calls the callback correctly", async () => {
  mockSuccessfulS3Upload()
  const file = new File(["file1"], "file1")
  const callback = jest.fn()
  await uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    "",
    callback,
    1
  )
  checkCallbackCalls(callback, [20, 40, 60, 80, 100])
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
  mockSuccessfulS3Upload()
  const result = await uploadToS3(files, "", callback, 1)
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
  mockSuccessfulS3Upload()
  await uploadToS3(files, "", callback, 1)
  checkCallbackCalls(callback, [
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
  mockFailedS3Upload()
  const fileId = "1df92708-d66b-4b55-8c1e-bb945a5c4fb5"
  const file = new File(["file1"], "file1")
  const result = uploadToS3([{ file, fileId }], "", jest.fn())
  await expect(result).rejects.toEqual("error")
})

test("a single file upload calls the callback correctly with a different chunk size", async () => {
  mockSuccessfulS3Upload(2)
  const file = new File(["file1"], "file1")
  const callback = jest.fn()
  await uploadToS3(
    [{ fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5", file }],
    "",
    callback,
    2
  )
  checkCallbackCalls(callback, [20, 60, 100])
})
