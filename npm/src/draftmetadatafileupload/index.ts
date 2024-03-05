 import {
   IFileWithPath
 } from "@nationalarchives/file-information"


export class DraftMetadataFileUpload{


  async saveDraftMetadataFile(
    consignmentId: string,
    iEntryWithPath : IFileWithPath

  ): Promise< Error> {

    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!

    const address = "/consignment/"+ consignmentId +"/metadata"
    const result: Response | Error = await fetch(address, {
      credentials: "include",
      method: "POST",
      body: iEntryWithPath.file,
      headers: {
        "Content-Type": "text/plain",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })
    return Error("no")
  }

}
