import {ObjectCannedACL, PutObjectCommandInput, S3Client, ServiceOutputTypes} from "@aws-sdk/client-s3";
import {IFileMetadata, TProgressFunction} from "@nationalarchives/file-information";
import {IUploadResult} from "../clientfileprocessing";
import {v4 as uuidv4} from "uuid";
import {Upload} from "@aws-sdk/lib-storage";

export interface IClientSideMetadata {
    checksum: string;
    fileSize: number;
    originalPath: string;
    lastModified: number;
    transferId: string;
    userId: string;
    matchId: string;
}

interface IFileProgressInfo {
    processedChunks: number
    totalChunks: number
    totalFiles: number
}

export class AssetUpload {
    client: S3Client
    uploadUrl: string
    ifNoneMatchHeaderValue: string
    aclHeaderValue: string
    assetSource: string
    metadataCategory: string
    recordsCategory: string

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
        this.assetSource = "networkdrive"
        this.recordsCategory = "records"
        this.metadataCategory = "metadata"
    }

    uploadAssets: (
        consignmentId: string,
        userId: string | undefined,
        assets: IFileMetadata[],
        callback: TProgressFunction
    ) => Promise<IUploadResult | Error> = async (
        consignmentId,
        userId,
        assets,
        callback
    ) => {
        const sendData: ServiceOutputTypes[] = []
        const failedMetadataUploadAssetPaths: string[] = []
        const failedFileUploadAssetPaths: string[] = []
        if (userId) {
            const objectKeyPrefix = `${userId}/${this.assetSource}/${consignmentId}`
            const totalFiles: number = assets.length
            const totalChunks: number = assets.reduce(
                (fileSizeTotal, tdrFileWithPath) =>
                    fileSizeTotal +
                    (tdrFileWithPath.file.size
                        ? tdrFileWithPath.file.size
                        : 1),
                0
            )
            let processedChunks = 0
            for (const asset of assets) {
                const matchId = uuidv4()
                const metadataObjectKey = `${objectKeyPrefix}/${this.metadataCategory}/${matchId}.${this.metadataCategory}`
                const recordObjectKey = `${objectKeyPrefix}/${this.recordsCategory}/${matchId}`
                const metadataUploadResult = await this.uploadMetadata(
                    false,
                    metadataObjectKey,
                    userId,
                    consignmentId,
                    matchId,
                    asset)
                if (this.objectUploadFailed(metadataUploadResult)) {
                    failedMetadataUploadAssetPaths.push(asset.path)
                }
                sendData.push(metadataUploadResult)
                const fileUploadResult = await this.uploadFile(
                    recordObjectKey,
                    asset.file,
                    callback,
                    {
                        processedChunks,
                        totalChunks,
                        totalFiles
                    }
                )
                if (this.objectUploadFailed(fileUploadResult)) {
                    failedFileUploadAssetPaths.push(asset.path)
                }
                sendData.push(fileUploadResult)
                processedChunks += asset.file.size
                    ? asset.file.size
                    : 1
            }
            const failedUploadPaths: string[] = failedMetadataUploadAssetPaths.concat(failedFileUploadAssetPaths)
            return failedUploadPaths.length === 0
            ? {
                    sendData,
                    processedChunks,
                    totalChunks
                }
            : Error(
                    `User's files have failed to upload. Paths of files: ${failedUploadPaths.toString()}`
                )
        } else {
            return Error("No valid user id found")
        }
    }

    uploadMetadata: (
        isEmptyDirectory: boolean,
        objectKey: string,
        userId: string,
        consignmentId: string,
        matchId: string,
        asset: IFileMetadata
    ) => Promise<ServiceOutputTypes> = (
        isEmptyDirectory,
        objectKey,
        userId,
        consignmentId,
        matchId,
        asset) => {
        const assetMetadata = this.convertToClientSideMetadata(isEmptyDirectory, matchId, userId, consignmentId, asset)
        const body = JSON.stringify(assetMetadata)
        const params: PutObjectCommandInput = {
            Key: objectKey,
            Bucket: this.uploadUrl,
            ACL: this.aclHeaderValue as ObjectCannedACL,
            Body: body,
            IfNoneMatch: this.ifNoneMatchHeaderValue
        }
        const progress = this.uploadObject(params)
        return progress.done()
    }

    uploadFile: (
        key: string,
        tdrFileWithPath: File,
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
            Body: tdrFileWithPath,
            IfNoneMatch: this.ifNoneMatchHeaderValue
        }
        const progress = this.uploadObject(params)
        const { processedChunks, totalChunks, totalFiles } = progressInfo
        if (tdrFileWithPath.size >= 1) {
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

    private uploadObject: (
        params: PutObjectCommandInput
    ) => Upload = (
        params
    ) => {
        return new Upload({ client: this.client, params})
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

    private objectUploadFailed: (
        uploadResult: ServiceOutputTypes
    ) => boolean = (
        uploadResult
    ) => {
        return uploadResult?.$metadata !== undefined &&
            uploadResult.$metadata.httpStatusCode != 200
    }
}
