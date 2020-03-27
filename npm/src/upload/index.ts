import { processFiles } from "../clientprocessing"
import { GraphqlClient } from "../graphql"
import { TdrFile } from "@nationalarchives/file-information"

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

interface InputElement {
  files?: TdrFile[]
}

declare var TDR_API_URL: string

export const upload: (graphqlClient: GraphqlClient) => void = graphqlClient => {
  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  if (uploadForm) {
    uploadForm.addEventListener("submit", ev => {
      ev.preventDefault()

      const consignmentId: number = retrieveConsignmentId()

      if (!consignmentId) {
        throw Error()
      }

      const target: HTMLInputTarget | null = ev.currentTarget
      const files: TdrFile[] = target!.files!.files!

      processFiles(graphqlClient, consignmentId, files.length)
        .then(r => {
          console.log("Returned File Ids: " + r.toString())
        })
        .catch(err => {
          throw err
        })
    })
  }
}

export function retrieveConsignmentId(): number {
  const pathName: string = window.location.pathname
  const paths: string[] = pathName.split("/", 3)

  return parseInt(paths[2]!, 10)
}
