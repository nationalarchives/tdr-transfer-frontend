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
 * Number of parts of a file that are uploaded at the same time. This is the
 * @aws-sdk/lib-storage default, set explicitly because the part size is worked out from
 * it: a part is buffered in the JavaScript heap while it is in flight, so a file being
 * uploaded holds queueSize parts in memory at once.
 */
const uploadQueueSize = 4

/**
 * The smallest part S3 accepts for any part other than the last one.
 */
const minPartSizeBytes = 5 * 1024 * 1024

/**
 * The largest part worth using. A part has to be buffered before it can be sent, so
 * beyond this the memory costs more than the extra throughput is worth.
 */
const maxPartSizeBytes = 16 * 1024 * 1024

/**
 * The most a file may be split into, imposed by S3.
 */
const maxPartCount = 10000

/**
 * The total amount of file content that may be buffered for parts in flight across all
 * of the files being uploaded at once.
 */
const maxTotalPartBytes = 256 * 1024 * 1024

/**
 * How large the parts of a file should be.
 *
 * A file is only ever sent queueSize parts at a time, so the fastest it can go is
 * queueSize parts per round trip however much bandwidth is free. At the 5MB minimum
 * that caps a single file at around 33MB/s on a connection with a 600ms round trip,
 * regardless of the connection's actual speed. That is invisible while thousands of
 * small files are still using the other workers, but a multi gigabyte file left
 * uploading on its own at the end of a consignment is limited by nothing else, and it
 * becomes the tail of the whole transfer.
 *
 * Larger parts raise that ceiling in proportion, but they also raise the memory in
 * proportion, so the size is worked out from a fixed overall budget shared between the
 * files that can be uploading at the same time. A consignment with one large file gives
 * it the maximum, while one made up entirely of large files falls back towards the
 * minimum rather than holding a part for each of them in memory at once.
 */
export const partSizeForUpload = (
  fileSizeInBytes: number,
  concurrentLargeFiles: number
): number => {
  const budgetPerFile = maxTotalPartBytes / Math.max(1, concurrentLargeFiles)
  const partSize = Math.min(
    maxPartSizeBytes,
    Math.floor(budgetPerFile / uploadQueueSize),
    // A file split into fewer parts than can be sent at once cannot fill the queue, so
    // parts beyond that size buy no throughput and only cost memory.
    Math.ceil(fileSizeInBytes / uploadQueueSize)
  )
  return Math.max(
    partSize,
    minPartSizeBytes,
    // A file large enough to exceed the part limit has to use larger parts whatever the
    // rest of this says, otherwise S3 rejects the upload part way through.
    Math.ceil(fileSizeInBytes / maxPartCount)
  )
}

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

    // Only files large enough to be uploaded in parts hold part buffers in memory, so
    // only they share the part budget.
    const largeFileCount = iTdrFilesWithPath.filter(
      (tdrFileWithPath) =>
        tdrFileWithPath.fileWithPath.file.size >= multipartThresholdBytes
    ).length
    const concurrentLargeFiles = Math.min(this.concurrency, largeFileCount)

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
            concurrentLargeFiles,
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
    concurrentLargeFiles: number,
    onProgress: (loaded: number) => void
  ) => Promise<ServiceOutputTypes> = (
    consignmentId,
    userId,
    tdrFileWithPath,
    concurrentLargeFiles,
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

    const progress = new Upload({
      client: this.client,
      params,
      queueSize: uploadQueueSize,
      partSize: partSizeForUpload(fileWithPath.file.size, concurrentLargeFiles)
    })

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
