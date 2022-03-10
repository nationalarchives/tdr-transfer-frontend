import {
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput
} from "@aws-sdk/client-s3"

import {Upload} from "@aws-sdk/lib-storage"
import {TProgressFunction} from "@nationalarchives/file-information"
import {attemptP, chain, encase, encaseP, Future, FutureInstance, map, parallel} from "fluture";

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
  uploadUrl: string

  constructor(client: S3Client, uploadUrl: string) {
    this.client = client
    this.uploadUrl = uploadUrl.split("//")[1]
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => FutureInstance<unknown, number> = (consignmentId, userId, files, callback, stage) => {
    const getUserId: (userId: string | undefined) => string = userId => {
      if(userId != undefined) {
        return userId
      } else {
        throw new Error("No valid user id found")
      }
    }
    const totalFiles = files.length
    const totalChunks: number = files.reduce(
      (fileSizeTotal, file) =>
        fileSizeTotal + (file.file.size ? file.file.size : 1),
      0
    )

    let processedChunks = 0
    return encase(getUserId)(userId)
      .pipe(chain(userId =>
        parallel(1)(files.map(file =>
          this.uploadSingleFile(
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
        ))
      ))
      .pipe(map(_ => processedChunks))
  }

  private uploadSingleFile: (
    consignmentId: string,
    userId: string,
    stage: string,
    tdrFile: ITdrFile,
    updateProgressCallback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => FutureInstance<unknown, ServiceOutputTypes> = (
    consignmentId,
    userId,
    stage,
    tdrFile,
    updateProgressCallback,
    progressInfo
  ) => {
    const {file, fileId} = tdrFile
    const key = `${userId}/${consignmentId}/${fileId}`
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: this.uploadUrl,
      ACL: "bucket-owner-read",
      Body: file
    }

    const progress = new Upload({client: this.client, params})

    const {processedChunks, totalChunks, totalFiles} = progressInfo
    if (file.size >= 1) {
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
    return attemptP(() => progress.done())
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

    updateProgressFunction({processedFiles, percentageProcessed, totalFiles})
  }
}
