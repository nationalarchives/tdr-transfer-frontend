import {
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput
} from "@aws-sdk/client-s3"

import { Upload } from "@aws-sdk/lib-storage"
import { TProgressFunction } from "@nationalarchives/file-information"
import { ITdrFileWithPath } from "../clientfilemetadataupload"

export interface ITdrFile {
  fileId: string
  file: File
}

interface IFileProgressInfo {
  processedChunks: number
  totalChunks: number
  totalFiles: number
}

export class S3Upload {
  client: S3Client
  constructor(client: S3Client) {
    this.client = client
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    iTdrFilesWithPath: ITdrFileWithPath[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<{
    sendData: ServiceOutputTypes[]
    processedChunks: number
    totalChunks: number
  }> = async (consignmentId, userId, iTdrFilesWithPath, callback, stage) => {
    if (userId) {
      const totalFiles = iTdrFilesWithPath.length
      const totalChunks: number = iTdrFilesWithPath.reduce(
        (fileSizeTotal, tdrFileWithPath) =>
          fileSizeTotal +
          (tdrFileWithPath.fileWithPath.file.size
            ? tdrFileWithPath.fileWithPath.file.size
            : 1),
        0
      )
      let processedChunks = 0
      const sendData: ServiceOutputTypes[] = []
      for (const tdrFileWithPath of iTdrFilesWithPath) {
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
          }
        )
        sendData.push(uploadResult)
        processedChunks += tdrFileWithPath.fileWithPath.file.size
          ? tdrFileWithPath.fileWithPath.file.size
          : 1
      }
      return { sendData, processedChunks, totalChunks }
    } else {
      throw new Error("No valid user id found")
    }
  }

  private uploadSingleFile: (
    consignmentId: string,
    userId: string,
    stage: string,
    tdrFileWithPath: ITdrFileWithPath,
    updateProgressCallback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => Promise<ServiceOutputTypes> = (
    consignmentId,
    userId,
    stage,
    tdrFileWithPath,
    updateProgressCallback,
    progressInfo
  ) => {
    const { fileWithPath, fileId } = tdrFileWithPath
    const key = `${userId}/${consignmentId}/${fileId}`
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: `tdr-upload-files-cloudfront-dirty-${stage}`,
      ACL: "bucket-owner-read",
      Body: fileWithPath.file,
      Tagging: `filePath=test` //${encodeURIComponent(fileWithPath.path)}
    }

    const s3Tags = [
      { Key: "file-path", Value: encodeURIComponent(fileWithPath.path) }
    ]

    const progress = new Upload({ client: this.client, params, tags: s3Tags })

    const { processedChunks, totalChunks, totalFiles } = progressInfo
    if (fileWithPath.file.size >= 1) {
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
