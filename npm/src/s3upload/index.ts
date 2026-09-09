import {
  PutObjectCommand,
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput,
  PutObjectCommandOutput,
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

// Number of files sent to S3 at the same time. Requests are multiplexed over HTTP/2 so
// they are not limited to the browser's six connections per origin.
export const defaultUploadConcurrency = 10

// Files below this size are sent with a single PutObject, which lets the browser stream
// the File rather than lib-storage buffering it in the JavaScript heap.
const multipartThresholdBytes = 5 * 1024 * 1024

// Parts of a file uploaded at the same time. A file holds this many parts in memory.
const uploadQueueSize = 4

const minPartSizeBytes = 5 * 1024 * 1024

const maxPartSizeBytes = 16 * 1024 * 1024

const maxPartCount = 10000

// Total file content that may be buffered for parts in flight across all files.
const maxTotalPartBytes = 256 * 1024 * 1024

// Larger parts let a single file use more bandwidth but cost proportionally more
// memory, so the size comes out of a fixed budget shared between concurrent large files.
export const partSizeForUpload = (
  fileSizeInBytes: number,
  concurrentLargeFiles: number
): number => {
  const budgetPerFile = maxTotalPartBytes / Math.max(1, concurrentLargeFiles)
  const partSize = Math.min(
    maxPartSizeBytes,
    Math.floor(budgetPerFile / uploadQueueSize),
    Math.ceil(fileSizeInBytes / uploadQueueSize)
  )
  return Math.max(
    partSize,
    minPartSizeBytes,
    Math.ceil(fileSizeInBytes / maxPartCount)
  )
}

// With If-None-Match: *, a 412 means the object already exists. Keys are unique per
// file, so the file is already uploaded and this is treated as a success. The SDK does
// not retry 412, so it surfaces as a thrown error.
const isAlreadyUploaded = (error: unknown): boolean => {
  if (typeof error !== "object" || error === null) {
    return false
  }

  const metadata = (error as { $metadata?: { httpStatusCode?: number } })
    .$metadata
  return metadata !== undefined && metadata.httpStatusCode === 412
}

// The error is not a result, so this stands in for the upload that already happened.
const alreadyUploadedResult: PutObjectCommandOutput = {
  $metadata: { httpStatusCode: 412 }
}

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
    _stage: string
  ) => Promise<IUploadResult | Error> = async (
    consignmentId,
    userId,
    iTdrFilesWithPath,
    callback,
    _stage
  ) => {
    if (!userId) {
      return Error("No valid user id found")
    }

    const totalFiles = iTdrFilesWithPath.length
    const fileChunks = iTdrFilesWithPath.map((tdrFileWithPath) =>
      tdrFileWithPath.fileWithPath.file.size
        ? tdrFileWithPath.fileWithPath.file.size
        : 1
    )
    const totalChunks = fileChunks.reduce(
      (fileSizeTotal, fileSize) => fileSizeTotal + fileSize,
      0
    )

    const largeFileCount = iTdrFilesWithPath.filter(
      (tdrFileWithPath) =>
        tdrFileWithPath.fileWithPath.file.size >= multipartThresholdBytes
    ).length
    const concurrentLargeFiles = Math.min(this.concurrency, largeFileCount)

    const sendData: ServiceOutputTypes[] = new Array(totalFiles)
    const failedFileIds: (string | undefined)[] = new Array(totalFiles)
    const reportedChunks: number[] = new Array(totalFiles).fill(0)
    let processedChunks = 0

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

    let uploadError: unknown = undefined

    const processFileUpload = async (index: number): Promise<void> => {
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
          // Stop the other workers, then rethrow once they have finished.
          uploadError = e
          return
        }
        sendData[index] = alreadyUploadedResult
        recordProgress(index, fileChunks[index])
        return
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

    const workerCount = Math.min(this.concurrency, totalFiles)
    if (workerCount === 0) {
      return {
        sendData: [],
        processedChunks: 0,
        totalChunks
      }
    }

    const batchSize = Math.ceil(totalFiles / workerCount)
    const fileIndexBatches = Array.from(
      { length: workerCount },
      (_, workerIndex) => {
        const startIndex = workerIndex * batchSize
        const endIndex = Math.min(startIndex + batchSize, totalFiles)
        return Array.from(
          { length: endIndex - startIndex },
          (_, offset) => startIndex + offset
        )
      }
    )

    const uploadWorker = async (fileIndexes: number[]) => {
      for (const index of fileIndexes) {
        if (uploadError !== undefined) {
          return
        }
        await processFileUpload(index)
      }
    }

    await Promise.all(
      fileIndexBatches.map((fileIndexes) => uploadWorker(fileIndexes))
    )

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
