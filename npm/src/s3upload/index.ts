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
  identityId: string

  constructor(identityId: string, region: string) {
    const timeout = 20 * 60 * 1000
    const connectTimeout = 20 * 60 * 1000
    this.s3 = new S3({ region, httpOptions: { timeout, connectTimeout } })
    this.identityId = identityId
  }

  private uploadSingleFile: (
    consignmentId: string,
    stage: string,
    tdrFile: ITdrFile,
    callback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => Promise<S3.ManagedUpload.SendData> = (
    consignmentId,
    stage,
    tdrFile,
    callback,
    progressInfo
  ) => {
    const { file, fileId } = tdrFile
    const progress: S3.ManagedUpload = this.s3.upload({
      Key: `${this.identityId}/${consignmentId}/${fileId}`,
      Body: file,
      Bucket: `tdr-upload-files-dirty-${stage}`
    })
    const { processedChunks, totalChunks, totalFiles } = progressInfo
    if (file.size >= 1) {
      // httpUploadProgress seems to only trigger if file size is greater than 0
      progress.on("httpUploadProgress", (ev) => {
        const chunks = ev.loaded + processedChunks
        const percentageProcessed = Math.round((chunks / totalChunks) * 100)
        const processedFiles = Math.floor((chunks / totalChunks) * totalFiles)

        callback({ processedFiles, percentageProcessed, totalFiles })
      })
    } else {
      const processedFiles = 1
      const percentageProcessed = Math.round(
        ((file.size + processedChunks) / totalChunks) * 100
      )
      callback({ processedFiles, percentageProcessed, totalFiles })
    }
    return progress.promise()
  }

  uploadToS3: (
    consignmentId: string,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<S3.ManagedUpload.SendData[]> = async (
    consignmentId,
    files,
    callback,
    stage
  ) => {
    const totalFiles = files.length
    const totalChunks: number =
      files.map((file) => file.file.size).reduce((prev, curr) => prev + curr) ||
      totalFiles
    let processedChunks = 0
    const sendData: S3.ManagedUpload.SendData[] = []
    for (const file of files) {
      const uploadResult = await this.uploadSingleFile(
        consignmentId,
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
    return sendData
  }
}
