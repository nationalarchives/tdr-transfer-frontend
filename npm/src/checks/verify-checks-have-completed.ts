import { IDraftMetadataValidationProgress } from "./get-checks-progress"

export const hasDraftMetadataValidationCompleted: (
  validationProgress: IDraftMetadataValidationProgress
) => boolean = (validationProgress: IDraftMetadataValidationProgress) => {
  const { progressStatus } = validationProgress

  return progressStatus !== "InProgress"
}
