import {
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput
} from "@aws-sdk/client-s3"

import { Upload } from "@aws-sdk/lib-storage"
import {
  IFileMetadata,
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import {v4 as uuidv4} from 'uuid';

export interface ITdrFileWithPath {
  fileId: string
  fileWithPath: IFileWithPath
}

interface IFileProgressInfo {
  processedChunks: number
  totalChunks: number
  totalFiles: number
}

export interface IUploadResult {
  sendData: ServiceOutputTypes[]
  processedChunks: number
  totalChunks: number
}

export class S3Upload {
  client: S3Client
  uploadUrl: string

  constructor(client: S3Client, uploadUrl: string) {
    this.client = client
    this.uploadUrl = uploadUrl.split("//")[1]
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    iTdrFilesWithPath: IFileMetadata[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<IUploadResult | Error> = async (
    consignmentId,
    userId,
    iTdrFilesWithPath,
    callback,
    stage
  ) => {
    if (userId) {
      const totalFiles = iTdrFilesWithPath.length
      const totalChunks: number = iTdrFilesWithPath.reduce(
        (fileSizeTotal, tdrFileWithPath) =>
          fileSizeTotal +
          (tdrFileWithPath.file.size
            ? tdrFileWithPath.file.size
            : 1),
        0
      )
      let processedChunks = 0
      const sendData: ServiceOutputTypes[] = []
      const fileIdsOfFilesThatFailedToUpload: string[] = []
      for (const tdrFileWithPath of iTdrFilesWithPath) {
        const {checksum, lastModified, size, path} = tdrFileWithPath

        const fileId = uuidv4()
        await this.uploadMetadata(
            consignmentId,
            userId,
            fileId,
            JSON.stringify({checksum, lastModified, size, path, fileId, consignmentId})
        )
        const uploadResult = await this.uploadSingleFile(
          consignmentId,
          userId,
          stage,
          tdrFileWithPath,
          callback,
          {
            processedChunks,
            totalChunks,
            totalFiles
          },
          fileId
        )
        if (
          uploadResult?.$metadata !== undefined &&
          uploadResult.$metadata.httpStatusCode != 200
        ) {
          fileIdsOfFilesThatFailedToUpload.push(fileId)
        }
        sendData.push(uploadResult)
        processedChunks += tdrFileWithPath.file.size
          ? tdrFileWithPath.file.size
          : 1
      }
      const csrfInput: HTMLInputElement = document.querySelector(
          "input[name='csrfToken']"
      )!
      await fetch(`/consignment/${consignmentId}/upload-status/Completed/${iTdrFilesWithPath.length}`,
          {
            method: "POST",
            credentials: "include",
            headers: {"Content-Type": "application/json", "Csrf-Token": csrfInput.value}
          })
      return fileIdsOfFilesThatFailedToUpload.length === 0
        ? {
            sendData,
            processedChunks,
            totalChunks
          }
        : Error(
            `User's files have failed to upload. fileIds of files: ${fileIdsOfFilesThatFailedToUpload.toString()}`
          )
    } else {
      return Error("No valid user id found")
    }
  }

  uploadMetadata: (
      consignmentId: string,
      userId: string,
      fileId: string,
      body: string
  ) => Promise<ServiceOutputTypes> = (consignmentId, userId, fileId, body) => {
    const key = `${userId}/${consignmentId}/${fileId}.metadata`
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: "dp-sam-test-bucket",
      ACL: "bucket-owner-read",
      Body: body
    }
    const progress = new Upload({ client: this.client, params })
    return progress.done()
  }

  uploadSingleFile: (
    consignmentId: string,
    userId: string,
    stage: string,
    tdrFileWithPath: IFileMetadata,
    updateProgressCallback: TProgressFunction,
    progressInfo: IFileProgressInfo,
    fileId: string
  ) => Promise<ServiceOutputTypes> = (
    consignmentId,
    userId,
    stage,
    tdrFileWithPath,
    updateProgressCallback,
    progressInfo,
    fileId
  ) => {

    const key = `${userId}/${consignmentId}/${fileId}.file`
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: "dp-sam-test-bucket",
      ACL: "bucket-owner-read",
      Body: tdrFileWithPath.file
    }

    const progress = new Upload({ client: this.client, params })

    const { processedChunks, totalChunks, totalFiles } = progressInfo
    if (tdrFileWithPath.file.size >= 1) {
      // httpUploadProgress seems to only trigger if file size is greater than 0
      progress.on("httpUploadProgress", (ev) => {
        const loaded = ev.loaded
        if (loaded) {
          const chunks = loaded + processedChunks
          this.updateUploadProgress(
            chunks,
            totalChunks,
            totalFiles,
            updateProgressCallback
          )
        }
      })
    } else {
      const chunks = 1 + processedChunks
      this.updateUploadProgress(
        chunks,
        totalChunks,
        totalFiles,
        updateProgressCallback
      )
    }
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
}
