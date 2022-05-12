import { GraphqlClient } from "../graphql"

import { IFileMetadata } from "@nationalarchives/file-information"

import {
  AddFilesAndMetadata,
  AddFilesAndMetadataMutation,
  AddFilesAndMetadataMutationVariables,
  ClientSideMetadataInput,
  StartUpload,
  StartUploadInput,
  StartUploadMutation,
  StartUploadMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "@apollo/client/core"
import { ITdrFile } from "../s3upload"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"


export class ClientFileMetadataUpload {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async startUpload(uploadFilesInfo: FileUploadInfo): Promise<void | Error> {
    const variables: StartUploadMutationVariables = {
      input: uploadFilesInfo
    }

    const result: FetchResult<StartUploadMutation> | Error = await this.client
      .mutation<StartUploadMutation, { input: StartUploadInput }>(
        StartUpload,
        variables
      )
      .catch((err) => {
        return Error(err)
      })
    if (isError(result)) {
      return result
    } else {
      if (!result.data || result.errors) {
        const errorMessage: string = result.errors
          ? result.errors.toString()
          : "no data"
        return Error(`Start upload failed: ${errorMessage}`)
      }
    }
  }

  async saveClientFileMetadata(
    consignmentId: string,
    allFileMetadata: IFileMetadata[],
    emptyFolders: string[]
  ): Promise<ITdrFile[] | Error> {
    const { metadataInputs, matchFileMap } =
      this.createMetadataInputsAndFileMap(allFileMetadata)

    const allFiles: ITdrFile[] = []

    const variables: AddFilesAndMetadataMutationVariables = {
      input: {
        consignmentId,
        metadataInput: metadataInputs,
        emptyDirectories: emptyFolders
      }
    }
    const result: FetchResult<AddFilesAndMetadataMutation> =
      await this.client.mutation(AddFilesAndMetadata, variables)

    if (result.errors) {
      return Error(
        `Add client file metadata failed: ${result.errors.toString()}`
      )
    }
    if (result.data) {
      result.data.addFilesAndMetadata.forEach((f) => {
        const fileId: string = f.fileId
        const file: File | undefined = matchFileMap.get(f.matchId)
        if (file) {
          allFiles.push({fileId, file})
        } else {
          return Error(`Invalid match id ${f.matchId} for file ${fileId}`)
        }
      })
    } else {
      return Error(
        `No data found in response for consignment ${consignmentId}`
      )
    }
    return allFiles
  }

  createMetadataInputsAndFileMap(allFileMetadata: IFileMetadata[]): {
    metadataInputs: ClientSideMetadataInput[]
    matchFileMap: Map<number, File>
  } {
    return allFileMetadata.reduce(
      (result, metadata: IFileMetadata, matchId) => {
        const { checksum, path, lastModified, file, size } = metadata
        result.matchFileMap.set(matchId, file)

        //Files uploaded with 'drag and files' have '/'  prepended, those uploaded with 'browse' don't
        //Ensure file paths stored in database are consistent
        const validatedPath = path.startsWith("/") ? path.substring(1) : path
        const metadataInput: ClientSideMetadataInput = {
          originalPath: validatedPath ? validatedPath : file.name,
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
        matchFileMap: new Map<number, File>()
      }
    )
  }
}
