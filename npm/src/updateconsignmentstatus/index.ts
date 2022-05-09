import { GraphqlClient } from "../graphql"

import {
  UpdateConsignmentStatus as UCS,
  ConsignmentStatusInput,
  UpdateConsignmentStatusMutation,
  UpdateConsignmentStatusMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "@apollo/client/core"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"

export class UpdateConsignmentStatus {
  client: GraphqlClient

  constructor(client: GraphqlClient) {
    this.client = client
  }

  async markUploadStatusAsCompleted(
    uploadFilesInfo: FileUploadInfo
  ): Promise<number | void | Error> {
    const updateConsignmentStatusInput: ConsignmentStatusInput = {
      consignmentId: uploadFilesInfo.consignmentId,
      statusType: "Upload",
      statusValue: "Completed"
    }
    const variables: UpdateConsignmentStatusMutationVariables = {
      updateConsignmentStatusInput
    }

    const result: FetchResult<UpdateConsignmentStatusMutation> | Error =
      await this.client.mutation(UCS, variables).catch((err) => {
        return err
      })

    if (isError(result)) {
      return result
    } else {
      if (
        !result.data ||
        !result.data.updateConsignmentStatus ||
        result.errors
      ) {
        const errorMessage: string = result.errors
          ? result.errors.toString()
          : "no data"
        return Error(errorMessage)
      } else {
        return result.data.updateConsignmentStatus
      }
    }
  }
}
