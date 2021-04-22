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
    consigmentId,
    stage,
    tdrFile,
    callback,
    progressInfo
  ) => {
    const { file, fileId } = tdrFile
    const progress: S3.ManagedUpload = this.s3.upload({
      Key: `${this.identityId}/${consigmentId}/${fileId}`,
      Body: file,
      Bucket: `tdr-upload-files-dirty-${stage}`
    })
    const { processedChunks, totalChunks, totalFiles } = progressInfo
    progress.on("httpUploadProgress", (ev) => {
      const chunks = ev.loaded + processedChunks
      const percentageProcessed = Math.round((chunks / totalChunks) * 100)
      const processedFiles = Math.floor((chunks / totalChunks) * totalFiles)

      callback({ processedFiles, percentageProcessed, totalFiles })
    })
    return progress.promise()
  }

  uploadToS3: (
    consignmentId: string,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string,
    chunkSize?: number
  ) => Promise<S3.ManagedUpload.SendData[]> = async (
    consignmentId,
    files,
    callback,
    stage,
    chunkSize = 5 * 1024 * 1024
  ) => {
    const totalChunks: number = files
      .map((file) => file.file.size)
      .reduce((prev, curr) => prev + curr)
    let processedChunks = 0
    const totalFiles = files.length
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
      processedChunks += file.file.size
    }
    return sendData
  }
}
