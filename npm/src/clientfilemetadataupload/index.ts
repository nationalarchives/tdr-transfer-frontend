import { GraphqlClient } from "../graphql"

import { IFileMetadata } from "@nationalarchives/file-information"

import {
  AddClientFileMetadata,
  AddClientFileMetadataMutationVariables,
  AddFiles,
  AddFilesMutation,
  AddFilesMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"
import { ITdrFile } from "../s3upload"
import { file } from "@babel/types"

export class ClientFileMetadataUpload {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async fileInformation(
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

    if (!result.data) {
      throw Error("Add files failed")
    } else {
      return result.data.addFiles.fileIds
    }
  }

  async clientFileMetadata(
    fileIds: string[],
    metadata: IFileMetadata[]
  ): Promise<ITdrFile[]> {
    const files: ITdrFile[] = []
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

      //Stop processing if an error is encountered
      if (result.errors) {
        throw Error(
          "Add client file metadata failed for file " +
            fileId +
            ": " +
            result.errors.toString()
        )
      }
      const { file } = element
      files.push({ fileId, file })
    }
    return files
  }

  generateMutationVariables(
    fileId: string,
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
}
