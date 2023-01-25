export class TriggerBackendChecks {
  async triggerBackendChecks(consignmentId: string): Promise<Response | Error> {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!
    return await fetch(`/consignment/${consignmentId}/trigger-backend-checks`, {
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
  }
}
