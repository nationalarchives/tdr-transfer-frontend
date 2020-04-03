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

  processFiles: (
    consignmentId: number,
    numberOfFiles: number
  ) => Promise<number[]> = async (
    consignmentId: number,
    numberOfFiles: number
  ) => {
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

  processClientFileMetadata: (
    files: File[],
    fileIds: number[]
  ) => Promise<void> = async (files: File[], fileIds: number[]) => {
    extractFileMetadata(files)
      .then(r => {
        this.addClientFileMetadata(fileIds, r).catch(e => {
          throw Error(e.message)
        })
      })
      .catch(err => {
        throw Error("Process client file metadata failed")
      })
  }

  async addClientFileMetadata(fileIds: number[], metadata: IFileMetadata[]) {
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

  // addClientFileMetadata: (
  //     fileId: number,
  //     metadata: IFileMetadata
  // ) => Promise<void> = async (fileId: number, metadata: IFileMetadata) => {
  //   const variables: AddClientFileMetadataMutationVariables = this.generateMutationVariables(
  //     fileId,
  //     metadata
  //   )
  //
  //   const result = await this.client.mutation(AddClientFileMetadata, variables)
  //
  //   if(result.errors) {
  //     throw Error("Metadata upload failed for file " + fileId + ": " + result.errors.toString())
  //   }
  // }

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
        createdDate: Date.now(),
        datetime: Date.now()
      }
    }
  }
}
