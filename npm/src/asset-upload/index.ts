// import {ObjectCannedACL, PutObjectCommandInput, S3Client, ServiceOutputTypes} from "@aws-sdk/client-s3";
// import {IFileMetadata, TProgressFunction} from "@nationalarchives/file-information";
// import {v4 as uuidv4} from 'uuid';
// import {Upload} from "@aws-sdk/lib-storage";
// import {S3ClientConfig} from "@aws-sdk/client-s3/dist-types/S3Client";
// import {TdrFetchHandler} from "../s3upload/tdr-fetch-handler";
//
// interface IAssetUploadProgressInfo {
//     processedChunks: number
//     totalChunks: number
//     totalFiles: number
// }
//
// export interface IAssetUploadResult {
//     sendData: ServiceOutputTypes[]
//     processedChunks: number
//     totalChunks: number
// }
//
export interface IClientSideMetadata {
    checksum: string;
    fileSize: number;
    originalPath: string;
    lastModified: number;
    transferId: string;
    userId: string;
    matchId: string;
}
//
// //TODO: handle empty directories - should create metadata sidecar
// export class AssetUpload {
//     uploadUrl: string
//     ifNoneMatchHeaderValue: string
//     aclHeaderValue: string
//     userId: string
//     transferId: string
//     s3Client: S3Client
//
//     constructor(
//         uploadUrl: string,
//         ifNoneMatchHeaderValue: string,
//         aclHeaderValue: string,
//         userId: string,
//         transferId: string
//     ) {
//         const requestTimeoutMs = 20 * 60 * 1000
//         const config: S3ClientConfig = {
//             region: "eu-west-2",
//             credentials: {
//                 accessKeyId: "placeholder-id",
//                 secretAccessKey: "placeholder-secret"
//             },
//             requestHandler: new TdrFetchHandler({ requestTimeoutMs })
//         }
//         this.s3Client = new S3Client(config)
//         this.uploadUrl = uploadUrl.split("//")[1]
//         this.ifNoneMatchHeaderValue = ifNoneMatchHeaderValue
//         this.aclHeaderValue = aclHeaderValue
//         this.userId = userId
//         this.transferId = transferId
//     }
//
//     uploadAssets: (
//         assets: IFileMetadata[],
//         transferId: string,
//         userId: string,
//         callback: TProgressFunction
//     ) => Promise<void | Error> = async (
//         assets,
//         transferId,
//         userId,
//         callback: TProgressFunction
//     ) => {
//         //TODO: call transfer service to get object key prefixes
//         const objectKeyPrefix = `${userId}/networkdrive/${transferId}`
//         const totalFiles = assets.length
//         const totalChunks: number = assets.reduce(
//             (fileSizeTotal, asset) =>
//                 fileSizeTotal +
//                 (asset.file.size
//                     ? asset.file.size
//                     : 1),
//             0
//         )
//         let processedChunks = 0
//         const sendData: ServiceOutputTypes[] = []
//         const matchIdsOfFilesThatFailedToUpload: string[] = []
//
//         for(const asset of assets) {
//             const matchId = uuidv4()
//             const metadataObjectKey = `${objectKeyPrefix}/metadata/${matchId}.metadata`
//             const recordObjectKey = `${objectKeyPrefix}/records/${matchId}`
//             const clientSideMetadata = this.convertToClientSideMetadata(false, matchId, userId, transferId, asset)
//             const metadataJsonString = JSON.stringify(clientSideMetadata)
//             const metadataJson = JSON.parse(metadataJsonString)
//
//             const metadataParams: PutObjectCommandInput = {
//                 Key: metadataObjectKey,
//                 Bucket: this.uploadUrl,
//                 ACL: this.aclHeaderValue as ObjectCannedACL,
//                 Body: metadataJson,
//                 IfNoneMatch: this.ifNoneMatchHeaderValue
//             }
//
//             const recordParams: PutObjectCommandInput = {
//                 Key: recordObjectKey,
//                 Bucket: this.uploadUrl,
//                 ACL: this.aclHeaderValue as ObjectCannedACL,
//                 Body: asset.file,
//                 IfNoneMatch: this.ifNoneMatchHeaderValue
//             }
//
//             await this.uploadObject(
//                 metadataParams,
//                 callback,
//                 {
//                     processedChunks,
//                     totalChunks,
//                     totalFiles
//                 })
//             await this.uploadObject(
//                 recordParams,
//                 callback,
//                 {
//                     processedChunks,
//                     totalChunks,
//                     totalFiles
//                 })
//         }
//     }
//
//     uploadObject: (
//         params: PutObjectCommandInput,
//         updateProgressCallback: TProgressFunction,
//         progressInfo: IAssetUploadProgressInfo
//     ) => Promise<ServiceOutputTypes> = (
//         params,
//         updateProgressCallback,
//         progressInfo
//     ) => {
//         const body = params.Body
//
//         const progress = new Upload({ client: this.s3Client, params })
//         const { processedChunks, totalChunks, totalFiles } = progressInfo
//         if (body.size >= 1) {
//             // httpUploadProgress seems to only trigger if file size is greater than 0
//             progress.on("httpUploadProgress", (ev) => {
//                 const loaded = ev.loaded
//                 if (loaded) {
//                     const chunks = loaded + processedChunks
//                     this.updateUploadProgress(
//                         chunks,
//                         totalChunks,
//                         totalFiles,
//                         updateProgressCallback
//                     )
//                 }
//             })
//         } else {
//             const chunks = 1 + processedChunks
//             this.updateUploadProgress(
//                 chunks,
//                 totalChunks,
//                 totalFiles,
//                 updateProgressCallback
//             )
//         }
//         return progress.done()
//     }
//
//     convertToClientSideMetadata: (
//         isEmptyDirectory: boolean,
//         matchId: string,
//         userId: string,
//         consignmentId: string,
//         sourceMetadata: IFileMetadata
//     ) => IClientSideMetadata = (
//         isEmptyDirectory,
//         matchId,
//         userId,
//         consignmentId,
//         sourceMetadata) => {
//         const { checksum, path, lastModified, file, size } = sourceMetadata
//         const pathWithoutSlash = path.startsWith("/") ? path.substring(1) : path
//         return {
//             checksum: checksum,
//             size: size,
//             path: pathWithoutSlash,
//             lastModified: lastModified.getTime(),
//             transferId: consignmentId,
//             userId: userId,
//             matchId: matchId
//         }
//     }
// }