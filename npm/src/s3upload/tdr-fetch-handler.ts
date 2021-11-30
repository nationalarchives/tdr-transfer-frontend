import { HttpHandler, HttpRequest, HttpResponse } from "@aws-sdk/protocol-http"
import { HttpHandlerOptions, HeaderBag } from "@aws-sdk/types"

function requestTimeout(timeoutInMs = 0): Promise<never> {
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

export interface FetchHttpHandlerOptions {
  /**
   * The number of milliseconds a request can take before being automatically
   * terminated.
   */
  requestTimeoutMs?: number
}

/**
 * This is copied mostly from https://github.com/aws/aws-sdk-js-v3/blob/main/packages/fetch-http-handler/src/fetch-http-handler.ts
 * There are a few changes
 * We set credentials to 'include' so the cookies are sent
 * We stop sending query parameters as they're not needed.
 * We remove the bucket name from the path
 */
export class TdrFetchHandler implements HttpHandler {
  private readonly requestTimeoutMs?: number

  constructor({ requestTimeoutMs }: FetchHttpHandlerOptions = {}) {
    this.requestTimeoutMs = requestTimeoutMs
  }

  destroy(): void {
    // Do nothing. TLS and HTTP/2 connection pooling is handled by the browser.
  }

  handle(
    request: HttpRequest,
    { abortSignal }: HttpHandlerOptions = {}
  ): Promise<{ response: HttpResponse }> {
    // if the request was already aborted, prevent doing extra work
    if (abortSignal?.aborted) {
      const abortError = new Error("Request aborted")
      abortError.name = "AbortError"
      return Promise.reject(abortError)
    }

    let path = `/${request.path.split("/").slice(2).join("/")}`

    const { port, method } = request
    const url = `${request.protocol}//${request.hostname}${
      port ? `:${port}` : ""
    }${path}`
    // Request constructor doesn't allow GET/HEAD request with body
    // ref: https://github.com/whatwg/fetch/issues/551
    const body =
      method === "GET" || method === "HEAD" ? undefined : request.body

    const newMethod = method == "POST" ? "PUT" : method

    const requestOptions: RequestInit = {
      body,
      headers: new Headers(request.headers),
      method: newMethod,
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
      requestTimeout(this.requestTimeoutMs)
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
