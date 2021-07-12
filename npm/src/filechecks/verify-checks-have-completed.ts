import { IFileCheckProgress } from "./get-file-check-progress"

export const haveFileChecksCompleted: (
  fileChecksProgress: IFileCheckProgress
) => boolean = (fileChecksProgress: IFileCheckProgress) => {
  const { antivirusProcessed, checksumProcessed, ffidProcessed, totalFiles } =
    fileChecksProgress

  return (
    antivirusProcessed == totalFiles &&
    checksumProcessed == totalFiles &&
    ffidProcessed == totalFiles
  )
}
