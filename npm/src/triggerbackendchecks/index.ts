import {handleUploadError, isError} from "../errorhandling";

export class TriggerBackendChecks {
  async triggerBackendChecks(consignmentId: string): Promise<Response | Error> {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!
    const result = await fetch(`/consignment/${consignmentId}/trigger-backend-checks`, {
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (!isError(result) && result.status != 200) {
      return Error(`Backend checks failed to trigger: ${result.statusText}`)
    } else {
      return result
    }
  }
}
