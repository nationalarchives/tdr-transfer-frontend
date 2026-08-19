import { isTransientFileReadError } from "../src/upload/form/file-types"

describe("isTransientFileReadError", () => {
  it("returns true for transient error names", () => {
    const timeoutError = new Error("Timed out")
    timeoutError.name = "TimeoutError"
    const abortError = new Error("Aborted")
    abortError.name = "AbortError"

    expect(isTransientFileReadError(timeoutError)).toBe(true)
    expect(isTransientFileReadError(abortError)).toBe(true)
  })

  it("returns false for non-transient errors", () => {
    expect(isTransientFileReadError(new Error("Permission denied"))).toBe(false)
    expect(isTransientFileReadError("not-an-error")).toBe(false)
  })
})
