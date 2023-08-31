export const addFileSelectionSuccessMessage = (fileName: string) => {
  // eslint-disable-next-line no-undef
  const fileNameElements: NodeListOf<Element> =
    document.querySelectorAll(".file-name")
  if (fileNameElements) {
    fileNameElements.forEach((e) => (e.textContent = fileName))
  }
}

export const addFolderSelectionSuccessMessage = (
  folderName: string,
  folderSize: number
) => {
  const selectedContainers: NodeListOf<Element> = document.querySelectorAll(
    ".js-drag-and-drop-selected"
  )

  selectedContainers.forEach((e) => {
    const selectedContentFragment = document
      .createRange()
      .createContextualFragment(e.firstElementChild.innerHTML)
    selectedContentFragment.querySelector(".folder-name").textContent =
      folderName
    selectedContentFragment.querySelector(
      ".folder-size"
    ).textContent = `${folderSize} ${folderSize === 1 ? "file" : "files"}`

    e.firstElementChild.innerHTML = ""
    e.firstElementChild.appendChild(selectedContentFragment)
  })
}

export const displaySelectionSuccessMessage = (
  successMessage: HTMLElement | null,
  warningMessagesToHide: {
    [s: string]: HTMLElement | null
  }
) => {
  const selectionArea = document.querySelector("#selection-area")
  const successMessageContainer: HTMLElement | null = document.querySelector(
    "#item-selection-success-container"
  )

  selectionArea?.classList.remove("govuk-form-group--error")

  Object.values(warningMessagesToHide).forEach(
    (warningMessageElement: HTMLElement | null) => {
      warningMessageElement?.setAttribute("hidden", "true")
    }
  )

  successMessageContainer?.removeAttribute("hidden")
  successMessage?.classList.remove("govuk-visually-hidden")
  successMessage?.focus()
}
