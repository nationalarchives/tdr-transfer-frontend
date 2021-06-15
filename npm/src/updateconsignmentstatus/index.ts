import { GraphqlClient } from "../graphql"

import {
  MarkUploadAsCompletedMutation,
  MarkUploadAsCompletedMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"
import { FileUploadInfo } from "../upload/upload-form"
import { getGraphqlDocuments } from "../index"

export class UpdateConsignmentStatus {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async markConsignmentStatusAsCompleted(
    uploadFilesInfo: FileUploadInfo
  ): Promise<number> {
    const variables: MarkUploadAsCompletedMutationVariables = {
      consignmentId: uploadFilesInfo.consignmentId
    }

    const result: FetchResult<MarkUploadAsCompletedMutation> =
      await this.client.mutation(
        (
          await getGraphqlDocuments()
        ).MarkUploadAsCompleted,
        variables
      )

    if (!result.data || !result.data.markUploadAsCompleted || result.errors) {
      const errorMessage: string = result.errors
        ? result.errors.toString()
        : "no data"
      throw Error(
        `Marking the Consignment Status as "Completed" failed: ${errorMessage}`
      )
    } else {
      return result.data.markUploadAsCompleted
    }
  }
}
