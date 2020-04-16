import S3 from "aws-sdk/clients/s3"
import {
  IProgressInformation,
  TProgressFunction
} from "@nationalarchives/file-information"
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
  constructor(identityId: string) {
    this.s3 = new S3()
    this.identityId = identityId
  }
  private uploadSingleFile: (
    tdrFile: ITdrFile,
    callback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => Promise<S3.ManagedUpload.SendData> = (
    tdrFile,
    callback,
    progressInfo
  ) => {
    const { file, fileId } = tdrFile
    const progress: S3.ManagedUpload = this.s3.upload({
      Key: `${this.identityId}/${fileId}`,
      Body: file,
      Bucket: "tdr-upload-files-intg"
    })
    const {
      processedChunks: loadedChunks,
      totalChunks,
      totalFiles
    } = progressInfo
    progress.on("httpUploadProgress", ev => {
      const chunks = ev.loaded + loadedChunks
      const percentageProcessed = Math.round((chunks / totalChunks) * 100)
      const processedFiles = Math.floor((chunks / totalChunks) * totalFiles)

      callback({ processedFiles, percentageProcessed, totalFiles })
    })
    return progress.promise()
  }

  uploadToS3: (
    files: ITdrFile[],
    callback: TProgressFunction,
    chunkSize?: number
  ) => Promise<S3.ManagedUpload.SendData[]> = async (
    files,
    callback,
    chunkSize = 5 * 1024 * 1024
  ) => {
    const totalChunks: number = files
      .map(file => file.file.size)
      .reduce((prev, curr) => prev + curr)
    let processedChunks = 0
    const totalFiles = files.length
    const sendData: S3.ManagedUpload.SendData[] = []
    for (const file of files) {
      const uploadResult = await this.uploadSingleFile(file, callback, {
        processedChunks,
        totalChunks,
        totalFiles
      })
      sendData.push(uploadResult)
      const currentLoaded = Math.ceil(file.file.size / chunkSize)
      processedChunks += currentLoaded
    }
    return sendData
  }
}
