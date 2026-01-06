import {ObjectCannedACL, PutObjectCommandInput, S3Client, ServiceOutputTypes} from "@aws-sdk/client-s3"

import {Upload} from "@aws-sdk/lib-storage"
import {IFileMetadata, IFileWithPath, TProgressFunction} from "@nationalarchives/file-information"
import {isError} from "../errorhandling"
import {AddFileStatusInput, FileStatus} from "@nationalarchives/tdr-generated-graphql"
import {v4 as uuidv4} from "uuid";
import {IClientSideMetadata} from "../asset-upload";
import {IUploadResult} from "../clientfileprocessing";

export interface ITdrFileWithPath {
  fileId: string
  fileWithPath: IFileWithPath
}

interface IFileProgressInfo {
  processedChunks: number
  totalChunks: number
  totalFiles: number
}

export class S3Upload {
  client: S3Client
  uploadUrl: string
  ifNoneMatchHeaderValue: string
  aclHeaderValue: string

  constructor(
    client: S3Client,
    uploadUrl: string,
    ifNoneMatchHeaderValue: string,
    aclHeaderValue: string
  ) {
    this.client = client
    this.uploadUrl = uploadUrl.split("//")[1]
    this.ifNoneMatchHeaderValue = ifNoneMatchHeaderValue
    this.aclHeaderValue = aclHeaderValue
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
      //TODO: call transfer service to get object key prefixes
      const objectKeyPrefix = `${userId}/networkdrive/${consignmentId}`
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
        const matchId = uuidv4()
        const metadataObjectKey = `${objectKeyPrefix}/metadata/${matchId}.metadata`
        const recordObjectKey = `${objectKeyPrefix}/records/${matchId}`

        const metadata = this.convertToClientSideMetadata(false, matchId, userId, consignmentId, tdrFileWithPath)
        await this.uploadMetadata(metadataObjectKey, JSON.stringify(metadata))

        const uploadResult = await this.uploadSingleFile(
          recordObjectKey,
          tdrFileWithPath,
          callback,
          {
            processedChunks,
            totalChunks,
            totalFiles
          }
        )
        if (
          uploadResult?.$metadata !== undefined &&
          uploadResult.$metadata.httpStatusCode != 200
        ) {
          // await this.addFileStatus(tdrFileWithPath.fileId, "Failed")
          fileIdsOfFilesThatFailedToUpload.push(matchId)
        }
        sendData.push(uploadResult)
        processedChunks += tdrFileWithPath.file.size
          ? tdrFileWithPath.file.size
          : 1
      }

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

  private uploadMetadata: (
      key: string,
      body: string
  ) => Promise<ServiceOutputTypes> = (
      key,
      body) => {
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: this.uploadUrl,
      ACL: this.aclHeaderValue as ObjectCannedACL,
      Body: body,
      IfNoneMatch: this.ifNoneMatchHeaderValue
    }
    const progress = new Upload({ client: this.client, params })
    return progress.done()
  }

  private uploadSingleFile: (
    key: string,
    tdrFileWithPath: IFileMetadata,
    updateProgressCallback: TProgressFunction,
    progressInfo: IFileProgressInfo
  ) => Promise<ServiceOutputTypes> = (
    key,
    tdrFileWithPath,
    updateProgressCallback,
    progressInfo
  ) => {
    const params: PutObjectCommandInput = {
      Key: key,
      Bucket: this.uploadUrl,
      ACL: this.aclHeaderValue as ObjectCannedACL,
      Body: tdrFileWithPath.file,
      IfNoneMatch: this.ifNoneMatchHeaderValue
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

  private convertToClientSideMetadata: (
      isEmptyDirectory: boolean,
      matchId: string,
      userId: string,
      consignmentId: string,
      sourceMetadata: IFileMetadata
  ) => IClientSideMetadata = (
      isEmptyDirectory,
      matchId,
      userId,
      consignmentId,
      sourceMetadata) => {
    const { checksum, path, lastModified, file, size } = sourceMetadata
    const pathWithoutSlash = path.startsWith("/") ? path.substring(1) : path
    return {
      checksum: checksum,
      fileSize: size,
      originalPath: pathWithoutSlash,
      lastModified: lastModified.getTime(),
      transferId: consignmentId,
      userId: userId,
      matchId: matchId
    }
  }
}
