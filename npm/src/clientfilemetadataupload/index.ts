import { GraphqlClient } from "../graphql"

import { IFileMetadata } from "@nationalarchives/file-information"

import {
  AddClientFileMetadata,
  AddFiles,
  AddFilesMutation,
  AddFilesMutationVariables,
  AddClientFileMetadataInput
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"
import { ITdrFile } from "../s3upload"

declare var METADATA_UPLOAD_BATCH_SIZE: number

export class ClientFileMetadataUpload {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async saveFileInformation(
    consignmentId: string,
    numberOfFiles: number
  ): Promise<string[]> {
    const variables: AddFilesMutationVariables = {
      addFilesInput: {
        consignmentId: consignmentId,
        numberOfFiles: numberOfFiles
      }
    }

    const result: FetchResult<AddFilesMutation> = await this.client.mutation(
      AddFiles,
      variables
    )

    if (!result.data || result.errors) {
      const errorMessage: string = result.errors
        ? result.errors.toString()
        : "no data"
      throw Error("Add files failed: " + errorMessage)
    } else {
      return result.data.addFiles.fileIds
    }
  }

  async saveClientFileMetadata(
    fileIds: string[],
    metadata: IFileMetadata[]
  ): Promise<ITdrFile[]> {
    const inputs: Map<
      AddClientFileMetadataInput,
      ITdrFile
    > = this.generateInputs(fileIds, metadata)
    const metadataBatches: AddClientFileMetadataInput[][] = this.createMetadataInputBatches(
      Array.from(inputs.keys())
    )

    for (const metadataInputs of metadataBatches) {
      const variables = { input: metadataInputs }
      const result = await this.client.mutation(
        AddClientFileMetadata,
        variables
      )

      if (result.errors) {
        throw Error(
          "Add client file metadata failed: " + result.errors.toString()
        )
      }
    }

    return Array.from(inputs.values())
  }

  generateInputs(
    fileIds: string[],
    metadata: IFileMetadata[]
  ): Map<AddClientFileMetadataInput, ITdrFile> {
    const metadataInputs = new Map<AddClientFileMetadataInput, ITdrFile>()

    metadata.forEach((value, index) => {
      const fileId = fileIds[index]
      const input: AddClientFileMetadataInput = {
        fileId: fileId,
        lastModified: value.lastModified.getTime(),
        fileSize: value.size,
        originalPath: value.path,
        checksum: value.checksum,
        //For now add current time
        createdDate: Date.now(),
        //Unclear what this field is meant to represent
        datetime: Date.now()
      }

      const { file } = value
      const tdrFile: ITdrFile = {
        fileId: fileId,
        file: file
      }

      metadataInputs.set(input, tdrFile)
    })

    return metadataInputs
  }

  createMetadataInputBatches(metadataInput: AddClientFileMetadataInput[]) {
    const batches: AddClientFileMetadataInput[][] = []

    for (
      let index = 0;
      index < metadataInput.length;
      index += METADATA_UPLOAD_BATCH_SIZE
    ) {
      batches.push(
        metadataInput.slice(index, index + METADATA_UPLOAD_BATCH_SIZE)
      )
    }

    return batches
  }
}
