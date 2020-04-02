import { GraphqlClient } from "../graphql"

import {
  extractFileMetadata,
  IFileMetadata
} from "@nationalarchives/file-information"

import {
  AddFilesMutation,
  AddFiles,
  AddFilesMutationVariables,
  AddClientFileMetadataMutationVariables,
  AddClientFileMetadataMutation,
  AddClientFileMetadata
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

  processMetadata: (
    files: File[],
    fileIds: number[]
  ) => Promise<boolean> = async (files: File[], fileIds: number[]) => {
    const metadata: IFileMetadata[] = await extractFileMetadata(files)

    for (const element of metadata) {
      let index = metadata.indexOf(element)
      const fileId = fileIds[index]
      const variables: AddClientFileMetadataMutationVariables = this.mutationVariables(
        fileId,
        element
      )

      const result: FetchResult<AddClientFileMetadataMutation> = await this.client.mutation(
        AddClientFileMetadata,
        variables
      )

      if (result.errors) {
        return false
      }
    }

    return true
  }

  mutationVariables(
    fileId: number,
    metadata: IFileMetadata
  ): AddClientFileMetadataMutationVariables {
    const variables: AddClientFileMetadataMutationVariables = {
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

    return variables
  }
}
