import {
  IDraftMetadataValidationProgress,
  IFileChecksStatus
} from "./get-checks-progress"

export const haveFileChecksCompleted: (
  fileChecksStatus: IFileChecksStatus
) => boolean = (fileChecksCompleted: IFileChecksStatus) => {
  const { status } = fileChecksCompleted
  return status !== "RUNNING"
}

export const hasDraftMetadataValidationCompleted: (
  validationProgress: IDraftMetadataValidationProgress
) => boolean = (validationProgress: IDraftMetadataValidationProgress) => {
  const { progressStatus } = validationProgress

  return progressStatus !== "InProgress"
}
