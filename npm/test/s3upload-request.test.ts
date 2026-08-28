import { S3Client } from "@aws-sdk/client-s3"
import { HttpResponse } from "@aws-sdk/protocol-http"
import { S3Upload } from "../src/s3upload"
import { EntryKind, IFileEntry } from "../src/upload/form/file-types"
import { FileUploader } from "../src/upload"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import Keycloak from "keycloak-js"
import { IFrontEndInfo } from "../src"

/**
 * These tests send commands through the real S3 client middleware stack, rather than
 * stubbing the client, so that they cover how the request is actually put on the wire.
 */

interface ICapturedRequest {
  headers: Record<string, string>
  body: unknown
}

const capturedRequests: ICapturedRequest[] = []

const captureRequestHandler = {
  destroy() {},
  updateHttpClientConfig() {},
  httpHandlerConfigs() {
    return {}
  },
  async handle(request: ICapturedRequest) {
    capturedRequests.push(request)
    return {
      response: new HttpResponse({
        statusCode: 200,
        headers: {},
        body: undefined
      })
    }
  }
}

const createFileEntry: (contents: string) => IFileEntry = (contents) => ({
  file: new File([contents], "file1"),
  path: "file1",
  kind: EntryKind.File
})

const uploadWithClient = async (client: S3Client) => {
  capturedRequests.length = 0
  const s3Upload = new S3Upload(
    client,
    "https://upload.tdr-integration.nationalarchives.gov.uk",
    "*",
    "bucket-owner-full-control"
  )
  return s3Upload.uploadToS3(
    "16b73cc7-a81e-4317-a7a4-9bbb5fa1cc4e",
    "b088d123-1280-4959-91ca-74858f7ba226",
    [
      {
        fileId: "1df92708-d66b-4b55-8c1e-bb945a5c4fb5",
        fileWithPath: createFileEntry("hello world")
      }
    ],
    jest.fn(),
    ""
  )
}

const createClient = (config = {}) =>
  new S3Client({
    region: "eu-west-2",
    credentials: { accessKeyId: "a", secretAccessKey: "b" },
    requestHandler: captureRequestHandler as never,
    ...config
  })

const createFileUploader = () => {
  const frontEndInfo: IFrontEndInfo = {
    apiUrl: "",
    uploadUrl: "https://upload.tdr-integration.nationalarchives.gov.uk",
    authUrl: "",
    stage: "test",
    region: "eu-west-2",
    clientId: "",
    realm: "",
    ifNoneMatchHeaderValue: "*",
    aclHeaderValue: "bucket-owner-full-control"
  }
  return new FileUploader(
    new ClientFileMetadataUpload(),
    frontEndInfo,
    { tokenParsed: {} } as Keycloak,
    jest.fn()
  )
}

beforeEach(() => {
  document.body.innerHTML = '<input name="csrfToken" value="abcde">'
})

test("the file is sent as the file itself so the browser streams it", async () => {
  await uploadWithClient(
    createClient({ requestChecksumCalculation: "WHEN_REQUIRED" })
  )

  expect(capturedRequests).toHaveLength(1)
  expect(capturedRequests[0].body).toBeInstanceOf(Blob)
  expect(capturedRequests[0].headers["content-length"]).toEqual("11")
})

test("no per request checksum is calculated when checksums are only added when required", async () => {
  await uploadWithClient(
    createClient({ requestChecksumCalculation: "WHEN_REQUIRED" })
  )

  const headers = capturedRequests[0].headers
  expect(
    Object.keys(headers).filter((header) =>
      header.startsWith("x-amz-checksum-")
    )
  ).toEqual([])
  expect(headers["x-amz-sdk-checksum-algorithm"]).toBeUndefined()
  expect(headers["x-amz-content-sha256"]).toEqual("UNSIGNED-PAYLOAD")
})

test("the request does not use headers a browser is not allowed to send", async () => {
  await uploadWithClient(
    createClient({ requestChecksumCalculation: "WHEN_REQUIRED" })
  )

  const headers = capturedRequests[0].headers
  expect(headers["transfer-encoding"]).toBeUndefined()
  expect(headers["content-encoding"]).toBeUndefined()
  expect(headers["x-amz-decoded-content-length"]).toBeUndefined()
})

test("the SDK default of calculating a checksum cannot send a file body", async () => {
  // Guards the reason the client sets requestChecksumCalculation. The SDK default of
  // WHEN_SUPPORTED pushes a file body onto its aws-chunked encoding path, which a
  // browser cannot send.
  await expect(uploadWithClient(createClient())).rejects.toThrow()
})

test("the file uploader configures the S3 client to only checksum when required", async () => {
  const client = createFileUploader().clientFileProcessing.s3Upload.client
  await expect(client.config.requestChecksumCalculation()).resolves.toEqual(
    "WHEN_REQUIRED"
  )
})

test("the file uploader allows more than the SDK default number of attempts", async () => {
  const client = createFileUploader().clientFileProcessing.s3Upload.client
  await expect(client.config.maxAttempts()).resolves.toEqual(5)
})

test("a request that keeps failing with a network error is attempted five times", async () => {
  let attempts = 0
  const alwaysFailingHandler = {
    ...captureRequestHandler,
    async handle() {
      attempts += 1
      // The error a browser raises when a request cannot be made at all.
      throw new TypeError("Failed to fetch")
    }
  }

  await expect(
    uploadWithClient(
      createClient({
        requestChecksumCalculation: "WHEN_REQUIRED",
        maxAttempts: 5,
        requestHandler: alwaysFailingHandler as never
      })
    )
  ).rejects.toThrow("Failed to fetch")

  expect(attempts).toEqual(5)
})

test("a file that recovers within the attempt limit still uploads", async () => {
  let attempts = 0
  const flakyHandler = {
    ...captureRequestHandler,
    async handle(request: ICapturedRequest) {
      attempts += 1
      if (attempts < 5) {
        throw new TypeError("Failed to fetch")
      }
      return captureRequestHandler.handle(request)
    }
  }

  const result = await uploadWithClient(
    createClient({
      requestChecksumCalculation: "WHEN_REQUIRED",
      maxAttempts: 5,
      requestHandler: flakyHandler as never
    })
  )

  expect(result).not.toBeInstanceOf(Error)
  expect(attempts).toEqual(5)
})
