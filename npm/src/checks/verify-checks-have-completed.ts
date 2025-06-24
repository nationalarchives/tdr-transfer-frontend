import {
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"

export const haveFileChecksCompleted: (
  fileChecksProgress: IFileCheckProgress
) => boolean = (fileChecksProgress: IFileCheckProgress) => {
  const { avProcessed, checksumProcessed, fileFormatProcessed, totalFiles } =
    fileChecksProgress

  return (
    avProcessed == totalFiles &&
    checksumProcessed == totalFiles &&
    fileFormatProcessed == totalFiles
  )
}

export const hasDraftMetadataValidationCompleted: (
  validationProgress: IDraftMetadataValidationProgress
) => boolean = (validationProgress: IDraftMetadataValidationProgress) => {
  const { progressStatus } = validationProgress

  return progressStatus !== "InProgress"
}
