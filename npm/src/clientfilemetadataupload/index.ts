import { GraphqlClient } from "../graphql"

import { IFileMetadata } from "@nationalarchives/file-information"

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
import { FileUploadInfo } from "../upload/upload-form"

declare var METADATA_UPLOAD_BATCH_SIZE: string

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
  ): Promise<ITdrFile[]> {
    const { metadataInputs, matchFileMap } =
      this.createMetadataInputsAndFileMap(allFileMetadata)

    const metadataBatches: ClientSideMetadataInput[][] =
      this.createMetadataInputBatches(metadataInputs)

    const allFiles: ITdrFile[] = []

    for (const [index, metadataInput] of metadataBatches.entries()) {
      const isComplete = index === metadataBatches.length - 1
      const variables: AddFilesAndMetadataMutationVariables = {
        input: {
          consignmentId,
          isComplete,
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
          const file: File | undefined = matchFileMap.get(f.matchId)
          if (file) {
            allFiles.push({ fileId, file })
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
    matchFileMap: Map<number, File>
  } {
    return allFileMetadata.reduce(
      (metadataInputsAndFileMapper, metadata: IFileMetadata, matchId) => {
        const { checksum, path, lastModified, file, size } = metadata
        metadataInputsAndFileMapper.matchFileMap.set(matchId, file)

        //Files uploaded with 'drag and files' have '/'  prepended, those uploaded with 'browse' don't
        //Ensure file paths stored in database are consistent
        const validatedPath = path.startsWith("/") ? path.substring(1) : path
        const metadataInput: ClientSideMetadataInput = {
          originalPath: validatedPath,
          checksum,
          lastModified: lastModified.getTime(),
          fileSize: size,
          matchId
        }
        metadataInputsAndFileMapper.metadataInputs.push(metadataInput)

        return metadataInputsAndFileMapper
      },
      {
        metadataInputs: <ClientSideMetadataInput[]>[],
        matchFileMap: new Map<number, File>()
      }
    )
  }
}
