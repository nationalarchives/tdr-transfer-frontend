import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { HttpRequest } from "@aws-sdk/protocol-http"
import { TdrFetchHandler } from "../src/s3upload/tdr-fetch-handler"

/**
 * A request that stalls has to be aborted when it times out. Without that the browser
 * keeps the connection open for a request whose result is thrown away, and on a
 * consignment of thousands of files those stalled requests accumulate until the
 * transfer stops making progress.
 */

const createRequest = () =>
  new HttpRequest({
    method: "PUT",
    protocol: "https:",
    hostname: "upload.example.com",
    path: "/user/consignment/file",
    headers: {}
  })

const createRequestWithBody = (sizeInBytes: number) =>
  new HttpRequest({
    method: "PUT",
    protocol: "https:",
    hostname: "upload.example.com",
    path: "/user/consignment/file",
    headers: {},
    body: new Uint8Array(sizeInBytes)
  })

const signalOfLastRequest = (): AbortSignal | undefined =>
  (fetchMock.mock.calls[0]?.[0] as Request | undefined)?.signal
beforeEach(() => {
  fetchMock.resetMocks()
})

test("a request that does not complete within the timeout is aborted", async () => {
  // A stalled request: the promise never settles on its own.
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({ requestTimeoutMs: 10 })

  await expect(handler.handle(createRequest())).rejects.toThrow(
    "Request did not complete within 10 ms"
  )
  expect(signalOfLastRequest()?.aborted).toBe(true)
})

test("the timeout error is named so the SDK treats it as retryable", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({ requestTimeoutMs: 10 })

  await expect(handler.handle(createRequest())).rejects.toMatchObject({
    name: "TimeoutError"
  })
})

test("a request that completes does not leave its timeout timer pending", async () => {
  fetchMock.mockResponse("", { status: 200 })
  const clearTimeoutSpy = jest.spyOn(global, "clearTimeout")

  const handler = new TdrFetchHandler({ requestTimeoutMs: 60000 })
  const { response } = await handler.handle(createRequest())

  expect(response.statusCode).toEqual(200)
  expect(clearTimeoutSpy).toHaveBeenCalled()
  expect(signalOfLastRequest()?.aborted).toBe(false)

  clearTimeoutSpy.mockRestore()
})

test("a request with no timeout configured is still sent", async () => {
  fetchMock.mockResponse("", { status: 200 })

  const handler = new TdrFetchHandler({})
  const { response } = await handler.handle(createRequest())

  expect(response.statusCode).toEqual(200)
})

/**
 * A part of a large file takes far longer to send than a request carrying no body, and
 * on a slow connection it only gets a share of the bandwidth because parts and files
 * are uploaded concurrently. A deadline that ignores the size of the body expires on a
 * part that is transferring perfectly well, and the retry is no faster, so a large file
 * can never finish.
 */

test("the time the body needs is added to the timeout", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({
    requestTimeoutMs: 10,
    minimumThroughputBytesPerSecond: 1000
  })

  // 2000 bytes at 1000 bytes per second is two seconds on top of the allowance.
  await expect(handler.handle(createRequestWithBody(2000))).rejects.toThrow(
    "Request did not complete within 2010 ms"
  )
})

test("a request with no body is only given the configured allowance", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({
    requestTimeoutMs: 10,
    minimumThroughputBytesPerSecond: 1000
  })

  await expect(handler.handle(createRequest())).rejects.toThrow(
    "Request did not complete within 10 ms"
  )
})

test("a part of a large file is given long enough to transfer on a slow connection", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({ requestTimeoutMs: 5 * 60 * 1000 })
  const partSizeBytes = 5 * 1024 * 1024

  jest.useFakeTimers()
  try {
    const handled = handler.handle(createRequestWithBody(partSizeBytes))
    handled.catch(() => {})
    // The default minimum throughput is 16KB a second, so a 5MB part needs over five
    // minutes of transfer time on top of the allowance.
    jest.advanceTimersByTime(5 * 60 * 1000)
    await Promise.resolve()
    expect(signalOfLastRequest()?.aborted).toBe(false)

    jest.advanceTimersByTime(10 * 60 * 1000)
    await expect(handled).rejects.toMatchObject({ name: "TimeoutError" })
  } finally {
    jest.useRealTimers()
  }
})
