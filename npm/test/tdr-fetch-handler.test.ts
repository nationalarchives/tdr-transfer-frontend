import fetchMock, { enableFetchMocks } from "jest-fetch-mock"
enableFetchMocks()
import { HttpRequest } from "@aws-sdk/protocol-http"
import {
  maxRequestTimeoutMs,
  TdrFetchHandler
} from "../src/s3upload/tdr-fetch-handler"

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
    jest.advanceTimersByTime(5 * 60 * 1000)
    await Promise.resolve()
    expect(signalOfLastRequest()?.aborted).toBe(false)

    jest.advanceTimersByTime(10 * 60 * 1000)
    await expect(handled).rejects.toMatchObject({ name: "TimeoutError" })
  } finally {
    jest.useRealTimers()
  }
})

test("the time added for the body cannot take a request past the maximum timeout", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({
    requestTimeoutMs: 5 * 60 * 1000,
    // A body needing far longer to transfer than the maximum allows.
    minimumThroughputBytesPerSecond: 1
  })

  jest.useFakeTimers()
  try {
    const handled = handler.handle(createRequestWithBody(1024))
    handled.catch(() => {})

    jest.advanceTimersByTime(maxRequestTimeoutMs)
    await expect(handled).rejects.toThrow(
      `Request did not complete within ${maxRequestTimeoutMs} ms`
    )
  } finally {
    jest.useRealTimers()
  }
})

test("aborting a platform signal aborts the request in flight", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  const handler = new TdrFetchHandler({ requestTimeoutMs: 60000 })
  const abortController = new AbortController()
  const handled = handler.handle(createRequest(), {
    abortSignal: abortController.signal
  })

  abortController.abort()

  await expect(handled).rejects.toMatchObject({ name: "AbortError" })
  expect(signalOfLastRequest()?.aborted).toBe(true)
})

test("a completed request stops listening to the caller's signal", async () => {
  fetchMock.mockResponse("", { status: 200 })

  const handler = new TdrFetchHandler({ requestTimeoutMs: 60000 })
  const abortController = new AbortController()
  const removeEventListener = jest.spyOn(
    abortController.signal,
    "removeEventListener"
  )

  await handler.handle(createRequest(), {
    abortSignal: abortController.signal
  })

  expect(removeEventListener).toHaveBeenCalledWith(
    "abort",
    expect.any(Function)
  )
  removeEventListener.mockRestore()
})

test("a signal that only supports onabort still aborts the request", async () => {
  fetchMock.mockImplementation(() => new Promise(() => {}))

  // The AbortSignal from the SDK's own AbortController has no addEventListener.
  const sdkSignal: { aborted: boolean; onabort: (() => void) | null } = {
    aborted: false,
    onabort: null
  }

  const handler = new TdrFetchHandler({ requestTimeoutMs: 60000 })
  const handled = handler.handle(createRequest(), { abortSignal: sdkSignal })
  await Promise.resolve()

  sdkSignal.onabort?.()

  await expect(handled).rejects.toMatchObject({ name: "AbortError" })
  expect(signalOfLastRequest()?.aborted).toBe(true)
})
