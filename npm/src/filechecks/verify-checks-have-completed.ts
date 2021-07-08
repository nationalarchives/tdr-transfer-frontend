import { IFileCheckProgress } from "./get-file-check-progress"

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
