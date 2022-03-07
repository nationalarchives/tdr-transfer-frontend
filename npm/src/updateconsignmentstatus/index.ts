import { GraphqlClient } from "../graphql"

import {
  MarkUploadAsCompleted,
  MarkUploadAsCompletedMutation,
  MarkUploadAsCompletedMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "@apollo/client/core"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"

export class UpdateConsignmentStatus {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async markConsignmentStatusAsCompleted(
    uploadFilesInfo: FileUploadInfo
  ): Promise<number | void | Error> {
    const variables: MarkUploadAsCompletedMutationVariables = {
      consignmentId: uploadFilesInfo.consignmentId
    }

    const result: FetchResult<MarkUploadAsCompletedMutation> | Error =
      await this.client
        .mutation(MarkUploadAsCompleted, variables)
        .catch((err) => {
          return err
        })
    if (isError(result)) {
      return result
    } else {
      if (!result.data || !result.data.markUploadAsCompleted || result.errors) {
        const errorMessage: string = result.errors
          ? result.errors.toString()
          : "no data"
        return Error(errorMessage)
      } else {
        return result.data.markUploadAsCompleted
      }
    }
  }
}
