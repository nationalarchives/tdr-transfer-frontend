import { IFileCheckProgress } from "./file-check-processing"

export const haveFileChecksCompleted: (
  fileChecksProgress: IFileCheckProgress | null
) => boolean = (fileChecksProgress: IFileCheckProgress | null) => {
  if (fileChecksProgress) {
    const { antivirusProcessed, checksumProcessed, ffidProcessed, totalFiles } =
      fileChecksProgress

    return (
      antivirusProcessed == totalFiles &&
      checksumProcessed == totalFiles &&
      ffidProcessed == totalFiles
    )
  } else {
    return false
  }
}

export const displayChecksCompletedBanner: () => void = () => {
  const checksCompletedBanner: HTMLDivElement | null = document.querySelector(
    "#file-checks-completed-banner"
  )
  if (checksCompletedBanner) {
    checksCompletedBanner.removeAttribute("hidden")
    checksCompletedBanner.focus()
  }
  const continueButton: HTMLDivElement | null = document.querySelector(
    "#file-checks-continue"
  )
  if (continueButton) {
    continueButton.classList.remove("govuk-button--disabled")
    continueButton.removeAttribute("disabled")
  }
}
