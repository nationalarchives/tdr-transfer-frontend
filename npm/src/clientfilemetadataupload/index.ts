import { GraphqlClient } from "../graphql"

import {
  IFileMetadata,
  IFileWithPath
} from "@nationalarchives/file-information"

import {
  AddFilesAndMetadata,
  AddFilesAndMetadataMutation,
  AddFilesAndMetadataMutationVariables,
  ClientSideMetadataInput,
  StartUpload,
  StartUploadMutation,
  StartUploadMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "@apollo/client/core"
import { ITdrFile } from "../s3upload"
import { FileUploadInfo } from "../upload/form/upload-form"

declare var METADATA_UPLOAD_BATCH_SIZE: string

export interface ITdrFileWithPath {
  fileId: string
  fileWithPath: IFileWithPath
}

export class ClientFileMetadataUpload {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async startUpload(uploadFilesInfo: FileUploadInfo): Promise<void> {
    const variables: StartUploadMutationVariables = {
      input: uploadFilesInfo
    }

    const result: FetchResult<StartUploadMutation> = await this.client.mutation(
      StartUpload,
      variables
    )

    if (!result.data || result.errors) {
      const errorMessage: string = result.errors
        ? result.errors.toString()
        : "no data"
      throw Error(`Start upload failed: ${errorMessage}`)
    }
  }

  async saveClientFileMetadata(
    consignmentId: string,
    allFileMetadata: IFileMetadata[]
  ): Promise<ITdrFileWithPath[]> {
    const { metadataInputs, matchFileMap } =
      this.createMetadataInputsAndFileMap(allFileMetadata)

    const metadataBatches: ClientSideMetadataInput[][] =
      this.createMetadataInputBatches(metadataInputs)

    const allFiles: ITdrFileWithPath[] = []

    for (const metadataInput of metadataBatches) {
      const variables: AddFilesAndMetadataMutationVariables = {
        input: {
          consignmentId,
          metadataInput
        }
      }
      const result: FetchResult<AddFilesAndMetadataMutation> =
        await this.client.mutation(AddFilesAndMetadata, variables)

      if (result.errors) {
        throw Error(
          `Add client file metadata failed: ${result.errors.toString()}`
        )
      }
      if (result.data) {
        result.data.addFilesAndMetadata.forEach((f) => {
          const fileId: string = f.fileId
          const file: IFileWithPath | undefined = matchFileMap.get(f.matchId)
          if (file) {
            allFiles.push({ fileId, fileWithPath: file })
          } else {
            throw Error(`Invalid match id ${f.matchId} for file ${fileId}`)
          }
        })
      } else {
        throw Error(
          `No data found in response for consignment ${consignmentId}`
        )
      }
    }
    return allFiles
  }

  createMetadataInputBatches(metadataInputs: ClientSideMetadataInput[]) {
    const batches: ClientSideMetadataInput[][] = []
    // METADATA_UPLOAD_BATCH_SIZE comes in as a string despite typescript thinking it's a number.
    // This means that on the first pass of the loop, index is set to "0250" and then exits.
    // Setting the type to string and parsing the number sets the batches correctly.
    const batchSize = parseInt(METADATA_UPLOAD_BATCH_SIZE, 10)

    for (let index = 0; index < metadataInputs.length; index += batchSize) {
      batches.push(metadataInputs.slice(index, index + batchSize))
    }

    return batches
  }

  createMetadataInputsAndFileMap(allFileMetadata: IFileMetadata[]): {
    metadataInputs: ClientSideMetadataInput[]
    matchFileMap: Map<number, IFileWithPath>
  } {
    return allFileMetadata.reduce(
      (result, metadata: IFileMetadata, matchId) => {
        const { checksum, path, lastModified, file, size } = metadata
        //Files uploaded with 'drag and files' have '/'  prepended, those uploaded with 'browse' don't
        //Ensure file paths stored in database are consistent
        const validatedPath = path.startsWith("/") ? path.substring(1) : path
        const pathWithoutSlash = validatedPath ? validatedPath : file.name
        result.matchFileMap.set(matchId, { file, path: pathWithoutSlash })
        const metadataInput: ClientSideMetadataInput = {
          originalPath: pathWithoutSlash,
          checksum,
          lastModified: lastModified.getTime(),
          fileSize: size,
          matchId
        }
        result.metadataInputs.push(metadataInput)

        return result
      },
      {
        metadataInputs: <ClientSideMetadataInput[]>[],
        matchFileMap: new Map<number, IFileWithPath>()
      }
    )
  }
}
