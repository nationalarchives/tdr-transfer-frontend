import {
  haveFileChecksCompleted
} from "../src/filechecks/verify-checks-have-completed"

test("haveFileChecksCompleted returns true if file checks have completed", () => {
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

test("haveFileChecksCompleted returns false if file checks have not completed", () => {
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

