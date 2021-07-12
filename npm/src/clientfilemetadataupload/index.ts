import { GraphqlClient } from "../graphql"

import { IFileMetadata } from "@nationalarchives/file-information"

import {
  AddFilesAndMetadata,
  AddFilesAndMetadataMutation,
  AddFilesAndMetadataMutationVariables,
  StartUpload,
  StartUploadMutation,
  StartUploadMutationVariables,
  MetadataInput
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"
import { ITdrFile } from "../s3upload"
import { FileUploadInfo } from "../upload/upload-form"

declare var METADATA_UPLOAD_BATCH_SIZE: string

export interface IClientFileData {
  metadataInput: MetadataInput
  tdrFile: File
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
    metadata: IFileMetadata[]
  ): Promise<ITdrFile[]> {
    let sequenceNumber = 0
    const clientFileData: IClientFileData[] = metadata.map((m) => {
      const { checksum, path, lastModified, file } = m
      const metadataInput: MetadataInput = {
        originalPath: path,
        checksum,
        lastModified: lastModified.getTime(),
        sequenceNumber
      }
      sequenceNumber++
      return { metadataInput, tdrFile: file }
    })

    const metadataInputs: MetadataInput[] = clientFileData.map(
      (cf) => cf.metadataInput
    )

    const sequenceFileMap: Map<number, File> = new Map(
      clientFileData.map((cf) => [cf.metadataInput.sequenceNumber, cf.tdrFile])
    )

    const metadataBatches: MetadataInput[][] =
      this.createMetadataInputBatches(metadataInputs)

    const filesPromiseArray: Promise<ITdrFile[]>[] = metadataBatches.map(
      async (metadataInput, idx) => {
        const isComplete = idx === metadataBatches.length - 1
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
          return result.data.addFilesAndMetadata.map((f) => {
            const fileId: string = f.fileId
            const file: File | undefined = sequenceFileMap.get(f.sequenceNumber)
            if (file) {
              return { fileId, file }
            } else {
              throw Error(
                `Invalid sequence number ${sequenceNumber} for file ${fileId}`
              )
            }
          })
        } else {
          throw Error(
            `No data found in response for consignment ${consignmentId}`
          )
        }
      }
    )
    const allFiles = await Promise.all(filesPromiseArray)
    return allFiles.reduce((acc, files) => acc.concat(files))
  }

  createMetadataInputBatches(metadataInputs: MetadataInput[]) {
    const batches: MetadataInput[][] = []
    // METADATA_UPLOAD_BATCH_SIZE comes in as a string despite typescript thinking it's a number.
    // This means that on the first pass of the loop, index is set to "0250" and then exits.
    // Setting the type to string and parsing the number sets the batches correctly.
    const batchSize = parseInt(METADATA_UPLOAD_BATCH_SIZE, 10)

    for (let index = 0; index < metadataInputs.length; index += batchSize) {
      batches.push(metadataInputs.slice(index, index + batchSize))
    }

    return batches
  }
}
