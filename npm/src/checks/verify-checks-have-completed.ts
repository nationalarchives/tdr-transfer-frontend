import {
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"

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

export const hasDraftMetadataValidationCompleted: (
  validationProgress: IDraftMetadataValidationProgress
) => boolean = (validationProgress: IDraftMetadataValidationProgress) => {
  const { progressStatus } = validationProgress

  return progressStatus !== "InProgress"
}
