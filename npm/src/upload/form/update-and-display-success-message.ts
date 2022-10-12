export const addFileSelectionSuccessMessage = (fileName: string) => {
  const fileNameElements: NodeListOf<Element> =
    document.querySelectorAll(".file-name")
  if (fileNameElements) {
    fileNameElements.forEach(e => e.textContent = fileName)
  }
}

export const addFolderSelectionSuccessMessage = (
  folderName: string,
  folderSize: number
) => {
  const folderNameElements: NodeListOf<Element> =
    document.querySelectorAll(".folder-name")
  const folderSizeElements: NodeListOf<Element> =
    document.querySelectorAll(".folder-size")

  if (folderNameElements && folderSizeElements) {
    folderNameElements.forEach(e => e.textContent = folderName)
    folderSizeElements.forEach(e => e.textContent = `${folderSize} ${folderSize === 1 ? "file" : "files"}`)
  }
}

export const displaySelectionSuccessMessage = (
  successMessage: HTMLElement | null,
  warningMessagesToHide: {
    [s: string]: HTMLElement | null
  }
) => {
  const selectionArea = document.querySelector("#selection-area")
  const successMessageContainer: HTMLElement | null = document.querySelector("#item-selection-success-container")

  selectionArea?.classList.remove("govuk-form-group--error")

  Object.values(warningMessagesToHide).forEach(
    (warningMessageElement: HTMLElement | null) => {
      warningMessageElement?.setAttribute("hidden", "true")
    }
  )

  successMessageContainer?.removeAttribute("hidden")
  successMessage?.removeAttribute("hidden")
  successMessage?.focus()
}
