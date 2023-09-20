import { HttpHandler, HttpRequest, HttpResponse } from "@aws-sdk/protocol-http"
import { buildQueryString } from "@aws-sdk/querystring-builder"
import { HeaderBag, HttpHandlerOptions, Provider } from "@aws-sdk/types"

declare let AbortController: any

export function requestTimeout(timeoutInMs = 0): Promise<never> {
  return new Promise((resolve, reject) => {
    if (timeoutInMs) {
      setTimeout(() => {
        const timeoutError = new Error(
          `Request did not complete within ${timeoutInMs} ms`
        )
        timeoutError.name = "TimeoutError"
        reject(timeoutError)
      }, timeoutInMs)
    }
  })
}

/**
 * Represents the http options that can be passed to a browser http client.
 */
export interface FetchHttpHandlerOptions {
  /**
   * The number of milliseconds a request can take before being automatically
   * terminated.
   */
  requestTimeoutMs?: number
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
    const requestTimeoutInMs = this.config!.requestTimeoutMs

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
    const requestOptions: RequestInit = {
      body,
      headers: new Headers(request.headers),
      method: method,
      credentials: "include"
    }

    // some browsers support abort signal
    if (typeof AbortController !== "undefined") {
      ;(requestOptions as any)["signal"] = abortSignal
    }

    const fetchRequest = new Request(url, requestOptions)
    const raceOfPromises = [
      fetch(fetchRequest).then((response) => {
        const fetchHeaders: any = response.headers
        const transformedHeaders: HeaderBag = {}

        for (const pair of <Array<string[]>>fetchHeaders.entries()) {
          transformedHeaders[pair[0]] = pair[1]
        }

        const hasReadableStream = response.body !== undefined

        // Return the response with buffered body
        if (!hasReadableStream) {
          return response.blob().then((body) => ({
            response: new HttpResponse({
              headers: transformedHeaders,
              statusCode: response.status,
              body
            })
          }))
        }
        // Return the response with streaming body
        return {
          response: new HttpResponse({
            headers: transformedHeaders,
            statusCode: response.status,
            body: response.body
          })
        }
      }),
      requestTimeout(requestTimeoutInMs)
    ]
    if (abortSignal) {
      raceOfPromises.push(
        new Promise<never>((resolve, reject) => {
          abortSignal.onabort = () => {
            const abortError = new Error("Request aborted")
            abortError.name = "AbortError"
            reject(abortError)
          }
        })
      )
    }
    return Promise.race(raceOfPromises)
  }
}
