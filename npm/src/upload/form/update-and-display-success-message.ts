export const addFileSelectionSuccessMessage = (fileName: string) => {
  const fileNameElement: HTMLElement | null =
    document.querySelector("#file-name")
  if (fileNameElement) {
    fileNameElement.textContent = fileName
  }
}

export const addFolderSelectionSuccessMessage = (
  folderName: string,
  folderSize: number
) => {
  const folderNameElement: HTMLElement | null =
    document.querySelector("#folder-name")
  const folderSizeElement: HTMLElement | null =
    document.querySelector("#folder-size")

  if (folderNameElement && folderSizeElement) {
    folderNameElement.textContent = folderName
    folderSizeElement.textContent = `${folderSize} ${
      folderSize === 1 ? "file" : "files"
    }`
  }
}

export const displaySelectionSuccessMessage = (
  successMessage: HTMLElement | null,
  warningMessagesToHide: {
    [s: string]: HTMLElement | null
  }
) => {
  const selectionArea = document.querySelector("#selection-area")
  selectionArea?.classList.remove("govuk-form-group--error")

  Object.values(warningMessagesToHide).forEach(
    (warningMessageElement: HTMLElement | null) => {
      warningMessageElement?.setAttribute("hidden", "true")
    }
  )

  successMessage?.removeAttribute("hidden")
  successMessage?.focus()
}
