import {
  PutObjectCommand,
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput,
  ObjectCannedACL
} from "@aws-sdk/client-s3"

import { Upload } from "@aws-sdk/lib-storage"
import { TProgressFunction } from "@nationalarchives/file-information"
import { isError } from "../errorhandling"
import {
  AddFileStatusInput,
  FileStatus
} from "@nationalarchives/tdr-generated-graphql"
import { IFileEntry } from "../upload/form/file-types"

export interface ITdrFileWithPath {
  fileId: string
  fileWithPath: IFileEntry
}

export interface IUploadResult {
  sendData: ServiceOutputTypes[]
  processedChunks: number
  totalChunks: number
}

/**
 * Number of files sent to S3 at the same time. Uploading one file at a time makes a
 * consignment of many small files latency bound: every file costs a full request round
 * trip before the next one starts. The upload endpoint is served by CloudFront over
 * HTTP/2, so these requests are multiplexed onto a single connection rather than being
 * limited to the browser's six connections per origin.
 */
export const defaultUploadConcurrency = 10

/**
 * Files smaller than this are sent with a single PutObject rather than through
 * @aws-sdk/lib-storage. lib-storage always reads the body through a ReadableStream and
 * concatenates it into a Buffer in the JavaScript heap before sending, which for a
 * consignment of thousands of small files copies gigabytes for no benefit. Passing the
 * File straight to PutObject lets the browser stream it to the network instead.
 * Anything at or above the size is still uploaded with lib-storage so that large files
 * continue to use multipart uploads.
 */
const multipartThresholdBytes = 5 * 1024 * 1024

/**
 * S3 rejects a request with 412 when If-None-Match is set and the object already
 * exists. Each file is uploaded to a key containing its own newly generated file id, so
 * nothing else can be writing to that key. A 412 therefore means an earlier attempt of
 * this same upload reached S3 and only its response was lost, which the SDK cannot tell
 * apart from the request never arriving. 412 is not retryable, so without this the
 * retry that follows a dropped response would fail the whole transfer.
 */
const isAlreadyUploaded = (error: unknown): boolean =>
  (error as { $metadata?: { httpStatusCode?: number } } | undefined)?.$metadata
    ?.httpStatusCode === 412

export class S3Upload {
  client: S3Client
  uploadUrl: string
  ifNoneMatchHeaderValue: string
  aclHeaderValue: string
  concurrency: number

  constructor(
    client: S3Client,
    uploadUrl: string,
    ifNoneMatchHeaderValue: string,
    aclHeaderValue: string,
    concurrency: number = defaultUploadConcurrency
  ) {
    this.client = client
    this.uploadUrl = uploadUrl.split("//")[1]
    this.ifNoneMatchHeaderValue = ifNoneMatchHeaderValue
    this.aclHeaderValue = aclHeaderValue
    this.concurrency = Math.max(1, concurrency)
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    iTdrFilesWithPath: ITdrFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<IUploadResult | Error> = async (
    consignmentId,
    userId,
    iTdrFilesWithPath,
    callback,
    stage
  ) => {
    if (!userId) {
      return Error("No valid user id found")
    }

    const totalFiles = iTdrFilesWithPath.length
    // Empty files still need to move the progress bar, so they count as a single chunk.
    const fileChunks = iTdrFilesWithPath.map((tdrFileWithPath) =>
      tdrFileWithPath.fileWithPath.file.size
        ? tdrFileWithPath.fileWithPath.file.size
        : 1
    )
    const totalChunks = fileChunks.reduce(
      (fileSizeTotal, fileSize) => fileSizeTotal + fileSize,
      0
    )

    const sendData: ServiceOutputTypes[] = new Array(totalFiles)
    const failedFileIds: (string | undefined)[] = new Array(totalFiles)
    const reportedChunks: number[] = new Array(totalFiles).fill(0)
    let processedChunks = 0

    // Files are uploaded concurrently so progress is accumulated from each file's
    // reported total rather than from a running count of completed files.
    const recordProgress = (index: number, loaded: number) => {
      const chunksForFile = Math.min(loaded, fileChunks[index])
      const newChunks = chunksForFile - reportedChunks[index]
      if (newChunks <= 0) {
        return
      }
      reportedChunks[index] = chunksForFile
      processedChunks += newChunks
      this.updateUploadProgress(
        processedChunks,
        totalChunks,
        totalFiles,
        callback
      )
    }

    let nextFileIndex = 0
    let uploadError: unknown = undefined

    const uploadWorker = async () => {
      while (uploadError === undefined) {
        const index = nextFileIndex++
        if (index >= totalFiles) {
          return
        }
        const tdrFileWithPath = iTdrFilesWithPath[index]
        let uploadResult: ServiceOutputTypes
        try {
          uploadResult = await this.uploadSingleFile(
            consignmentId,
            userId,
            tdrFileWithPath,
            (loaded) => recordProgress(index, loaded)
          )
        } catch (e) {
          if (!isAlreadyUploaded(e)) {
            // Stop the other workers picking up more files, then rethrow once they
            // have finished so the error is not lost in an unhandled rejection.
            uploadError = e
            return
          }
          sendData[index] = e as ServiceOutputTypes
          recordProgress(index, fileChunks[index])
          continue
        }

        sendData[index] = uploadResult
        recordProgress(index, fileChunks[index])
        if (
          uploadResult?.$metadata !== undefined &&
          uploadResult.$metadata.httpStatusCode != 200
        ) {
          await this.addFileStatus(tdrFileWithPath.fileId, "Failed")
          failedFileIds[index] = tdrFileWithPath.fileId
        }
      }
    }

    const workerCount = Math.min(this.concurrency, totalFiles)
    await Promise.all(Array.from({ length: workerCount }, () => uploadWorker()))

    if (uploadError !== undefined) {
      throw uploadError
    }

    const fileIdsOfFilesThatFailedToUpload = failedFileIds.filter(
      (fileId): fileId is string => fileId !== undefined
    )

    return fileIdsOfFilesThatFailedToUpload.length === 0
      ? {
          sendData,
          processedChunks,
          totalChunks
        }
      : Error(
          `User's files have failed to upload. fileIds of files: ${fileIdsOfFilesThatFailedToUpload.toString()}`
        )
  }

  private uploadSingleFile: (
    consignmentId: string,
    userId: string,
    tdrFileWithPath: ITdrFileWithPath,
    onProgress: (loaded: number) => void
  ) => Promise<ServiceOutputTypes> = (
    consignmentId,
    userId,
    tdrFileWithPath,
    onProgress
  ) => {
    const { fileWithPath, fileId } = tdrFileWithPath
    const key = `${userId}/${consignmentId}/${fileId}`
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: this.uploadUrl,
      ACL: this.aclHeaderValue as ObjectCannedACL,
      Body: fileWithPath.file,
      IfNoneMatch: this.ifNoneMatchHeaderValue
    }

    if (fileWithPath.file.size < multipartThresholdBytes) {
      // The caller reports the whole file as processed once this resolves, so there is
      // no need for intermediate progress events on a single request.
      return this.client.send(new PutObjectCommand(params))
    }

    const progress = new Upload({ client: this.client, params })

    progress.on("httpUploadProgress", (ev) => {
      const loaded = ev.loaded
      if (loaded) {
        onProgress(loaded)
      }
    })
    return progress.done()
  }

  private updateUploadProgress: (
    chunks: number,
    totalChunks: number,
    totalFiles: number,
    callback: TProgressFunction
  ) => void = (
    chunks: number,
    totalChunks: number,
    totalFiles: number,
    updateProgressFunction: TProgressFunction
  ) => {
    const percentageProcessed = Math.round((chunks / totalChunks) * 100)
    const processedFiles = Math.floor((chunks / totalChunks) * totalFiles)

    updateProgressFunction({ processedFiles, percentageProcessed, totalFiles })
  }

  private async addFileStatus(
    fileId: string,
    status: string
  ): Promise<FileStatus | Error> {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!
    const input: AddFileStatusInput = {
      fileId,
      statusType: "Upload",
      statusValue: status
    }
    const result: Response | Error = await fetch("/add-file-status", {
      credentials: "include",
      method: "POST",
      body: JSON.stringify(input),
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Add file status failed: ${result.statusText}`)
    } else {
      return (await result.json()) as FileStatus
    }
  }
}
