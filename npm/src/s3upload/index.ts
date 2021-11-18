import {S3Client, S3, ServiceOutputTypes} from "@aws-sdk/client-s3";
import { HttpHandler, HttpRequest, HttpResponse } from "@aws-sdk/protocol-http"
import {HttpHandlerOptions, RequestHandlerOutput} from "@aws-sdk/types"
import {useRegionalEndpointMiddleware} from "@aws-sdk/middleware-sdk-s3"
import { Upload } from "@aws-sdk/lib-storage";
import { TProgressFunction } from "@nationalarchives/file-information"
import {S3ClientConfig} from "@aws-sdk/client-s3/dist-types/S3Client";

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
  endpoint: string
  constructor(endpoint: string) {
    this.endpoint = endpoint
  }

  uploadToS3: (
    consignmentId: string,
    userId: string | undefined,
    files: ITdrFile[],
    callback: TProgressFunction,
    stage: string
  ) => Promise<{
    sendData: ServiceOutputTypes[]
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
      const sendData: ServiceOutputTypes[] = []
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
  ) => Promise<ServiceOutputTypes> = (
    consignmentId,
    userId,
    stage,
    tdrFile,
    updateProgressCallback,
    progressInfo
  ) => {
    const { file, fileId } = tdrFile
    const key = `${userId}/${consignmentId}/${fileId}`
    const params = {Key: key, Bucket: "undefined", ACL: "bucket-owner-read", Body: file}
    const connectTimeout = 20 * 60 * 1000
    const config: S3ClientConfig = {
      region: "eu-west-2",
      endpoint: this.endpoint,
      credentials: {accessKeyId: "placeholder-id", secretAccessKey: "placeholder-secret"},
      bucketEndpoint: true,
    }
    const client = new S3Client(config)

    client.middlewareStack.add(
      (next, context) => async (args: any) => {
        args.request.path = `/${args.request.path.split("/").slice(2).join("/")}`
        args.request.query = {}
        return await next(args);
      },
      { step: "build", name: "removeBucketName" }
    );

    const progress = new Upload({client, params})

    const { processedChunks, totalChunks, totalFiles } = progressInfo
    if (file.size >= 1) {
      // httpUploadProgress seems to only trigger if file size is greater than 0
      progress.on("httpUploadProgress", (ev) => {
        if(ev.loaded) {
          const chunks = ev.loaded! + processedChunks
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
