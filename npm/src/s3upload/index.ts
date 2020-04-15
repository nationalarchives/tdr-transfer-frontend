import S3 from "aws-sdk/clients/s3"
export interface ITdrFile {
  fileId: string
  file: File
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
    callback: (percentageProgress: number) => void,
    totalLoaded: number,
    totalSize: number
  ) => Promise<S3.ManagedUpload.SendData> = (
    tdrFile,
    callback,
    totalLoaded,
    totalSize
  ) => {
    const { file, fileId } = tdrFile
    const progress: S3.ManagedUpload = this.s3.upload({
      Key: `${this.identityId}/${fileId}`,
      Body: file,
      Bucket: "tdr-upload-files-intg"
    })
    progress.on("httpUploadProgress", ev => {
      callback(Math.round(((ev.loaded + totalLoaded) / totalSize) * 100))
    })
    return progress.promise()
  }

  uploadToS3: (
    files: ITdrFile[],
    callback: (percentageProgress: number) => void,
    chunkSize?: number
  ) => Promise<S3.ManagedUpload.SendData[]> = async (
    files,
    callback,
    chunkSize = 5 * 1024 * 1024
  ) => {
    const size: number = files
      .map(file => file.file.size)
      .reduce((prev, curr) => prev + curr)
    let totalLoaded = 0
    const sendData: S3.ManagedUpload.SendData[] = []
    for (const file of files) {
      const uploadResult = await this.uploadSingleFile(
        file,
        callback,
        totalLoaded,
        size
      )
      sendData.push(uploadResult)
      const currentLoaded = Math.ceil(file.file.size / chunkSize)
      totalLoaded += currentLoaded
    }
    return sendData
  }
}
