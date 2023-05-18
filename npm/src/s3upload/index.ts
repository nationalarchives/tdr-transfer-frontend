import {
  S3Client,
  ServiceOutputTypes,
  PutObjectCommandInput
} from "@aws-sdk/client-s3"

import { Upload } from "@aws-sdk/lib-storage"
import {
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import { isError } from "../errorhandling"
import {
  AddFileStatusInput,
  FileStatus
} from "@nationalarchives/tdr-generated-graphql"

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
      const fileIdsOfFilesThatFailedToUpload: string[] = []

      for (const tdrFileWithPath of iTdrFilesWithPath) {
        const r = await this.uploadSingleFileV2(
            consignmentId,
            userId,
            stage,
            tdrFileWithPath,
            callback, {
              processedChunks,
              totalChunks,
              totalFiles
            }
        )
      }

      // for (const tdrFileWithPath of iTdrFilesWithPath) {
      //   const uploadResult = await this.uploadSingleFile(
      //     consignmentId,
      //     userId,
      //     stage,
      //     tdrFileWithPath,
      //     callback,
      //     {
      //       processedChunks,
      //       totalChunks,
      //       totalFiles
      //     }
      //   )
      //   if (
      //     uploadResult?.$metadata !== undefined &&
      //     uploadResult.$metadata.httpStatusCode != 200
      //   ) {
      //     await this.addFileStatus(tdrFileWithPath.fileId, "Failed")
      //     fileIdsOfFilesThatFailedToUpload.push(tdrFileWithPath.fileId)
      //   }
      //   sendData.push(uploadResult)
      //   processedChunks += tdrFileWithPath.fileWithPath.file.size
      //     ? tdrFileWithPath.fileWithPath.file.size
      //     : 1
      // }

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
      Bucket: this.uploadUrl,
      ACL: "bucket-owner-read",
      Body: fileWithPath.file
    }

    const progress = new Upload({ client: this.client, params })

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

  private async uploadSingleFileV2(
      consignmentId: string,
      userId: string,
      stage: string,
      tdrFileWithPath: ITdrFileWithPath,
      updateProgressCallback: TProgressFunction,
      progressInfo: IFileProgressInfo
  ): Promise<void | Error> {
    const csrfInput: HTMLInputElement = document.querySelector(
        "input[name='csrfToken']"
    )!

    const formData: FormData = new FormData()
    formData.append("consignmentId", consignmentId)
    formData.append("fileId",tdrFileWithPath.fileId)
    formData.append("file", tdrFileWithPath.fileWithPath.file)

    const result: Response | Error = await fetch(`/s3-upload-records/${userId}/${consignmentId}/${tdrFileWithPath.fileId}`, {
      credentials: "include",
      method: "POST",
      body: formData,
      headers: {
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Upload to s3 failed: ${result.statusText}`)
    }
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
