import { GraphqlClient } from "../graphql"

import {
  extractFileMetadata,
  IFileMetadata
} from "@nationalarchives/file-information"

import {
  AddClientFileMetadata,
  AddClientFileMetadataMutationVariables,
  AddFiles,
  AddFilesMutation,
  AddFilesMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"

export class ClientFileProcessing {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async processFiles(
    consignmentId: number,
    numberOfFiles: number
  ): Promise<number[]> {
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

    if (!result.data) {
      throw Error("Add files failed")
    } else {
      return result.data.addFiles.fileIds
    }
  }

  async processClientFileMetadata(
    files: File[],
    fileIds: number[]
  ): Promise<void> {
    extractFileMetadata(files)
      .then(clientMetadata => {
        this.addClientFileMetadata(fileIds, clientMetadata).catch(err => {
          this.handleErrors(err)
        })
      })
      .catch(err => {
        this.handleErrors(err)
      })
  }

  async addClientFileMetadata(
    fileIds: number[],
    metadata: IFileMetadata[]
  ): Promise<void> {
    for (const element of metadata) {
      let index = metadata.indexOf(element)
      const fileId = fileIds[index]
      const variables: AddClientFileMetadataMutationVariables = this.generateMutationVariables(
        fileId,
        element
      )

      const result = await this.client.mutation(
        AddClientFileMetadata,
        variables
      )

      if (result.errors) {
        throw Error(
          "Add client file metadata failed for file " +
            fileId +
            ": " +
            result.errors.toString()
        )
      }
    }
  }

  generateMutationVariables(
    fileId: number,
    metadata: IFileMetadata
  ): AddClientFileMetadataMutationVariables {
    return {
      input: {
        fileId: fileId,
        lastModified: metadata.lastModified.getTime(),
        fileSize: metadata.size,
        originalPath: metadata.path,
        checksum: metadata.checksum,
        //For now add current time
        createdDate: Date.now(),
        //Unclear what this field is meant to represent
        datetime: Date.now()
      }
    }
  }

  handleErrors(error: Error) {
    //For now console log errors
    console.log(error.message)
  }
}
