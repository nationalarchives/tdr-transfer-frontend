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
  ) => Promise<boolean> = async (files: File[], fileIds: number[]) => {
    await extractFileMetadata(files)
      .then(r => {
        this.addClientFileMetadata(fileIds, r)
      })
      .catch(err => {
        throw err
      })

    return true
  }

  async addClientFileMetadata(fileIds: number[], metadata: IFileMetadata[]) {
    for (const element of metadata) {
      let index = metadata.indexOf(element)
      const fileId = fileIds[index]
      const variables: AddClientFileMetadataMutationVariables = this.generateMutationVariables(
        fileId,
        element
      )

      await this.client
        .mutation(AddClientFileMetadata, variables)
        .catch(err => {
          throw err
        })
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
        createdDate: Date.now(),
        datetime: Date.now()
      }
    }
  }
}
