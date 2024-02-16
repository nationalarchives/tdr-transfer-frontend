import { IFileCheckProgress } from "./get-check-progress"

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
