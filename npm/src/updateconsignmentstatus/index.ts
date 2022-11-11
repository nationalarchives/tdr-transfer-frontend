import {
  ConsignmentStatusInput,
  UpdateConsignmentStatusMutation
} from "@nationalarchives/tdr-generated-graphql"

import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"

export class UpdateConsignmentStatus {
  async setUploadStatusBasedOnFileStatuses(
    uploadFilesInfo: FileUploadInfo
  ): Promise<number | void | Error> {
    const updateConsignmentStatusInput: ConsignmentStatusInput = {
      consignmentId: uploadFilesInfo.consignmentId,
      statusType: "Upload"
    }
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!
    const result: Response | Error = await fetch("/update-consignment-status", {
      credentials: "include",
      method: "POST",
      body: JSON.stringify(updateConsignmentStatusInput),
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Update consignment status failed: ${result.statusText}`)
    } else {
      const response: UpdateConsignmentStatusMutation =
        (await result.json()) as UpdateConsignmentStatusMutation
      return response.updateConsignmentStatus!
    }
  }
}
