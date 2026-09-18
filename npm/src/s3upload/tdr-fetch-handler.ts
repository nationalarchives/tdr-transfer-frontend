import { HttpHandler, HttpRequest, HttpResponse } from "@aws-sdk/protocol-http"
import { buildQueryString } from "@aws-sdk/querystring-builder"
import { HeaderBag, HttpHandlerOptions, Provider } from "@aws-sdk/types"

declare let AbortController: any

export function createTimeoutError(timeoutInMs: number): Error {
  const timeoutError = new Error(
    `Request did not complete within ${timeoutInMs} ms`
  )
  // The SDK treats an error with this name as transient, so the request is retried.
  timeoutError.name = "TimeoutError"
  return timeoutError
}

// The slowest upload throughput a single request is expected to sustain.
export const defaultMinimumThroughputBytesPerSecond = 16 * 1024

// A ceiling on the scaled timeout, so that a large body cannot let a dead connection
// stay open for longer than a request was ever allowed before the scaling was added.
export const maxRequestTimeoutMs = 20 * 60 * 1000

const bodySizeInBytes = (body: unknown): number => {
  if (!body) {
    return 0
  }
  if (typeof Blob !== "undefined" && body instanceof Blob) {
    return body.size
  }
  if (ArrayBuffer.isView(body)) {
    return body.byteLength
  }
  if (body instanceof ArrayBuffer) {
    return body.byteLength
  }
  if (typeof body === "string") {
    return body.length
  }
  return 0
}

export interface FetchHttpHandlerOptions {
  // Milliseconds a request is allowed on top of the time its body needs at
  // minimumThroughputBytesPerSecond before being terminated.
  requestTimeoutMs?: number
  minimumThroughputBytesPerSecond?: number
}

type FetchHttpHandlerConfig = FetchHttpHandlerOptions

export class TdrFetchHandler implements HttpHandler {
  private config?: FetchHttpHandlerConfig
  private readonly configProvider?: Provider<FetchHttpHandlerConfig>

  constructor(
    options?:
      FetchHttpHandlerOptions | Provider<FetchHttpHandlerOptions | undefined>
  ) {
    if (typeof options === "function") {
      this.configProvider = async () => (await options()) || {}
    } else {
      this.config = options ?? {}
    }
  }

  destroy(): void {
    // Do nothing. TLS and HTTP/2 connection pooling is handled by the browser.
  }

  async handle(
    request: HttpRequest,
    { abortSignal }: HttpHandlerOptions = {}
  ): Promise<{ response: HttpResponse }> {
    if (!this.config && this.configProvider) {
      this.config = await this.configProvider()
    }
    // if the request was already aborted, prevent doing extra work
    if (abortSignal?.aborted) {
      const abortError = new Error("Request aborted")
      abortError.name = "AbortError"
      return Promise.reject(abortError)
    }

    let path = request.path
    if (request.query) {
      const queryString = buildQueryString(request.query)
      if (queryString) {
        path += `?${queryString}`
      }
    }

    const { method } = request
    const url = `${request.protocol}//${path}`
    // Request constructor doesn't allow GET/HEAD request with body
    // ref: https://github.com/whatwg/fetch/issues/551
    const body =
      method === "GET" || method === "HEAD" ? undefined : request.body

    const requestTimeoutInMs = this.timeoutForBody(body)

    const requestOptions: RequestInit = {
      body,
      headers: new Headers(request.headers),
      method: method,
      credentials: "include"
    }

    // The request gets its own controller so that it can be aborted when it times out,
    // otherwise the browser holds the stalled HTTP/2 stream open indefinitely.
    const controller =
      typeof AbortController !== "undefined" ? new AbortController() : undefined

    if (controller) {
      ;(requestOptions as any)["signal"] = controller.signal
    }

    const fetchRequest = new Request(url, requestOptions)

    const raceOfPromises: Promise<Response>[] = [fetch(fetchRequest)]

    let timeoutId: ReturnType<typeof setTimeout> | undefined
    raceOfPromises.push(
      new Promise<never>((_, reject) => {
        if (requestTimeoutInMs) {
          timeoutId = setTimeout(() => {
            controller?.abort()
            reject(createTimeoutError(requestTimeoutInMs))
          }, requestTimeoutInMs)
        }
      })
    )

    let stopListeningForAbort: (() => void) | undefined
    if (abortSignal) {
      raceOfPromises.push(
        new Promise<never>((_, reject) => {
          const onAbort = () => {
            const abortError = new Error("Request aborted")
            abortError.name = "AbortError"
            controller?.abort()
            reject(abortError)
          }
          stopListeningForAbort = listenForAbort(abortSignal, onAbort)
        })
      )
    }

    try {
      return toHttpResponse(await Promise.race(raceOfPromises))
    } finally {
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId)
      }
      stopListeningForAbort?.()
    }
  }

  // The configured allowance plus the time the body needs at the lowest expected
  // throughput, so that a large part is not given the same deadline as an empty body.
  // Capped so a stalled request still gives up within maxRequestTimeoutMs.
  private timeoutForBody(body: unknown): number | undefined {
    const baseTimeoutInMs = this.config!.requestTimeoutMs
    if (!baseTimeoutInMs) {
      return undefined
    }
    const bytesPerSecond =
      this.config!.minimumThroughputBytesPerSecond ??
      defaultMinimumThroughputBytesPerSecond
    const scaledTimeoutInMs =
      baseTimeoutInMs +
      Math.ceil((bodySizeInBytes(body) / bytesPerSecond) * 1000)
    return Math.min(scaledTimeoutInMs, maxRequestTimeoutMs)
  }
}

// The SDK's own AbortSignal only supports the onabort property, while a platform
// signal supports listeners, which can be removed once the request has finished so
// that a long-lived signal does not retain a handler per request it outlives.
const listenForAbort = (
  abortSignal: NonNullable<HttpHandlerOptions["abortSignal"]>,
  onAbort: () => void
): (() => void) => {
  const eventTarget = abortSignal as Partial<globalThis.AbortSignal>
  if (typeof eventTarget.addEventListener === "function") {
    eventTarget.addEventListener("abort", onAbort, { once: true })
    return () => eventTarget.removeEventListener?.("abort", onAbort)
  }
  abortSignal.onabort = onAbort
  return () => {
    if (abortSignal.onabort === onAbort) {
      abortSignal.onabort = null
    }
  }
}

const toHttpResponse = async (
  response: Response
): Promise<{ response: HttpResponse }> => {
  const transformedHeaders: HeaderBag = {}
  response.headers.forEach((value, name) => {
    transformedHeaders[name] = value
  })

  const hasReadableStream = !!response.body

  // Return the response with buffered body
  if (!hasReadableStream) {
    const body = await response.blob()
    return {
      response: new HttpResponse({
        headers: transformedHeaders,
        statusCode: response.status,
        body
      })
    }
  }
  // Return the response with streaming body
  return {
    response: new HttpResponse({
      headers: transformedHeaders,
      statusCode: response.status,
      body: response.body
    })
  }
}
