import S3 from "aws-sdk/clients/s3"
import { TProgressFunction } from "@nationalarchives/file-information"

export interface ITdrFile {
  fileId: string
  file: File
}

interface IFileProgressInfo {
  processedChunks: number
  totalChunks: number
  totalFiles: number
}

type TdrS3 = Pick<S3, "upload">

export class S3Upload {
  s3: TdrS3

  constructor() {
    const timeout = 20 * 60 * 1000
    const connectTimeout = 20 * 60 * 1000
    this.s3 = new S3({ httpOptions: { timeout, connectTimeout } })
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<{
    sendData: S3.ManagedUpload.SendData[]
    processedChunks: number
    totalChunks: number
  }> = async (consignmentId, userId, files, callback, stage) => {
    if (userId) {
      const totalFiles = files.length
      const totalChunks: number = files.reduce(
        (fileSizeTotal, file) =>
          fileSizeTotal + (file.file.size ? file.file.size : 1),
        0
      )
      let processedChunks = 0
      const sendData: S3.ManagedUpload.SendData[] = []
      for (const file of files) {
        const uploadResult = await this.uploadSingleFile(
          consignmentId,
          userId,
          stage,
          file,
          callback,
          {
            processedChunks,
            totalChunks,
            totalFiles
          }
        )
        sendData.push(uploadResult)
        processedChunks += file.file.size ? file.file.size : 1
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
    tdrFile: ITdrFile,
    updateProgressCallback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => Promise<S3.ManagedUpload.SendData> = (
    consignmentId,
    userId,
    stage,
    tdrFile,
    updateProgressCallback,
    progressInfo
  ) => {
    const { file, fileId } = tdrFile
    const progress: S3.ManagedUpload = this.s3.upload({
      Key: `${userId}/${consignmentId}/${fileId}`,
      Body: file,
      Bucket: `suzanne-test-cloudfront`,
      ACL: "bucket-owner-full-control"
    })
    const { processedChunks, totalChunks, totalFiles } = progressInfo
    if (file.size >= 1) {
      // httpUploadProgress seems to only trigger if file size is greater than 0
      progress.on("httpUploadProgress", (ev) => {
        const chunks = ev.loaded + processedChunks
        this.updateUploadProgress(
          chunks,
          totalChunks,
          totalFiles,
          updateProgressCallback
        )
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
    return progress.promise()
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
