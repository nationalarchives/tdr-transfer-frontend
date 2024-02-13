import {hasDraftMetadataValidationCompleted, haveFileChecksCompleted} from "../src/checks/verify-checks-have-completed"

test("'haveFileChecksCompleted' returns true if file checks have completed", () => {
  const mockFileChecksResponse = {
    antivirusProcessed: 2,
    checksumProcessed: 2,
    ffidProcessed: 2,
    totalFiles: 2
  }

  const checksCompleted: boolean = haveFileChecksCompleted(
    mockFileChecksResponse
  )
  expect(checksCompleted).toBe(true)
})

test("'haveFileChecksCompleted' returns false if file checks have not completed", () => {
  const mockFileChecksResponse = {
    antivirusProcessed: 1,
    checksumProcessed: 2,
    ffidProcessed: 1,
    totalFiles: 2
  }
  const checksCompleted: boolean = haveFileChecksCompleted(
    mockFileChecksResponse
  )

  expect(checksCompleted).toBe(false)
})

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

test("'hasDraftMetadataValidationCompleted' returns false if status is not 'Completed' or 'CompletedWithIssues'", () => {
  const mockStatusResponse = {
    progressStatus: "SomeOtherValue"
  }

  const checksCompleted: boolean = hasDraftMetadataValidationCompleted(
      mockStatusResponse
  )
  expect(checksCompleted).toBe(false)
})
