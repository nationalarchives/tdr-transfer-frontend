import {hasDraftMetadataValidationCompleted} from "../src/checks/verify-checks-have-completed"

test("'hasDraftMetadataValidationCompleted' returns true if status is 'Completed'", () => {
  const mockStatusResponse = {
    progressStatus: "Completed"
  }

  const checksCompleted: boolean = hasDraftMetadataValidationCompleted(
      mockStatusResponse
  )
  expect(checksCompleted).toBe(true)
})

test("'hasDraftMetadataValidationCompleted' returns true if status is 'CompletedWithIssues'", () => {
  const mockStatusResponse = {
    progressStatus: "CompletedWithIssues"
  }

  const checksCompleted: boolean = hasDraftMetadataValidationCompleted(
      mockStatusResponse
  )
  expect(checksCompleted).toBe(true)
})

test("'hasDraftMetadataValidationCompleted' returns false if status is 'InProgress'", () => {
  const mockStatusResponse = {
    progressStatus: "InProgress"
  }

  const checksCompleted: boolean = hasDraftMetadataValidationCompleted(
      mockStatusResponse
  )
  expect(checksCompleted).toBe(false)
})
