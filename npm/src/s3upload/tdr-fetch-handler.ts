import { HttpHandler, HttpRequest, HttpResponse } from "@aws-sdk/protocol-http"
import { buildQueryString } from "@aws-sdk/querystring-builder"
import { HeaderBag, HttpHandlerOptions, Provider } from "@aws-sdk/types"

declare let AbortController: any

export function createTimeoutError(timeoutInMs: number): Error {
  const timeoutError = new Error(
    `Request did not complete within ${timeoutInMs} ms`
  )
  // The SDK classifies an error with this name as transient, so a request that has
  // stalled is retried rather than failing the whole transfer.
  timeoutError.name = "TimeoutError"
  return timeoutError
}

/**
 * The slowest upload throughput a single request is expected to sustain. Parts of a
 * large file are sent four at a time and ten files are uploaded at once, so on a slow
 * connection each request only ever gets a small share of the available bandwidth. A
 * flat deadline would expire on a part that is transferring perfectly well, and the
 * retry that follows is no faster, so a large file can never finish.
 */
export const defaultMinimumThroughputBytesPerSecond = 16 * 1024

/**
 * The size of the body about to be sent, so that the time it legitimately needs can be
 * added to the timeout. Anything whose size cannot be determined is treated as empty,
 * which leaves the base timeout unchanged.
 */
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

/**
 * Represents the http options that can be passed to a browser http client.
 */
export interface FetchHttpHandlerOptions {
  /**
   * The number of milliseconds a request is allowed on top of the time its body needs
   * at minimumThroughputBytesPerSecond before being automatically terminated. The
   * allowance for the body is what keeps this a way of spotting a request that has
   * stalled rather than a limit on how large a file may be.
   */
  requestTimeoutMs?: number
  /**
   * The throughput a request is assumed to achieve at worst, used to work out how long
   * its body should be given. Defaults to defaultMinimumThroughputBytesPerSecond.
   */
  minimumThroughputBytesPerSecond?: number
}

type FetchHttpHandlerConfig = FetchHttpHandlerOptions

export class TdrFetchHandler implements HttpHandler {
  private config?: FetchHttpHandlerConfig
  private readonly configProvider?: Provider<FetchHttpHandlerConfig>

  constructor(
    options?:
      | FetchHttpHandlerOptions
      | Provider<FetchHttpHandlerOptions | undefined>
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

  updateHttpClientConfig(key: never, value: never): void {
    // Added to fix compilation issue
  }

  httpHandlerConfigs(): any {
    // Added to fix compilation issue
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

    // some browsers support abort signal
    // The request is given its own controller rather than the caller's signal so
    // that a request which stalls can be aborted when it times out. Without that
    // the browser holds the HTTP/2 stream open indefinitely, and on a consignment
    // of thousands of files those stalled streams accumulate until the whole
    // transfer stops making progress with no error ever surfacing.
    const controller =
      typeof AbortController !== "undefined" ? new AbortController() : undefined

    if (controller) {
      ;(requestOptions as any)["signal"] = controller.signal
    }

    const fetchRequest = new Request(url, requestOptions)

    const raceOfPromises: Promise<Response>[] = [fetch(fetchRequest)]

    let timeoutId: ReturnType<typeof setTimeout> | undefined
    raceOfPromises.push(
      new Promise<never>((resolve, reject) => {
        if (requestTimeoutInMs) {
          timeoutId = setTimeout(() => {
            controller?.abort()
            reject(createTimeoutError(requestTimeoutInMs))
          }, requestTimeoutInMs)
        }
      })
    )

    if (abortSignal) {
      raceOfPromises.push(
        new Promise<never>((resolve, reject) => {
          abortSignal.onabort = () => {
            const abortError = new Error("Request aborted")
            abortError.name = "AbortError"
            // Aborting the caller's signal has to abort the fetch too, otherwise the
            // browser carries on with a request whose result is thrown away.
            controller?.abort()
            reject(abortError)
          }
        })
      )
    }

    try {
      return toHttpResponse(await Promise.race(raceOfPromises))
    } finally {
      // The timer keeps the request alive in the event loop, and on a large
      // consignment there is one per request.
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId)
      }
    }
  }

  /**
   * How long the request may take, being the configured allowance plus the time the
   * body needs at the lowest throughput a request is expected to achieve. A part of a
   * large file is orders of magnitude bigger than the requests that carry no body, so
   * giving them all the same deadline either expires on a part that is still
   * transferring or lets a genuinely stalled request sit for far too long.
   */
  private timeoutForBody(body: unknown): number | undefined {
    const baseTimeoutInMs = this.config!.requestTimeoutMs
    if (!baseTimeoutInMs) {
      return undefined
    }
    const bytesPerSecond =
      this.config!.minimumThroughputBytesPerSecond ??
      defaultMinimumThroughputBytesPerSecond
    return (
      baseTimeoutInMs +
      Math.ceil((bodySizeInBytes(body) / bytesPerSecond) * 1000)
    )
  }
}

const toHttpResponse = async (
  response: Response
): Promise<{ response: HttpResponse }> => {
  const fetchHeaders: any = response.headers
  const transformedHeaders: HeaderBag = {}

  for (const pair of <Array<string[]>>fetchHeaders.entries()) {
    transformedHeaders[pair[0]] = pair[1]
  }

  const hasReadableStream = response.body !== undefined

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
