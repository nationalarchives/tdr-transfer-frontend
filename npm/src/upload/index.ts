import { GraphqlClient } from "../graphql"
import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileProcessing } from "../clientprocessing"

interface HTMLInputTarget extends EventTarget {
  files?: InputElement
}

interface InputElement {
  files?: TdrFile[]
}

export const upload: (graphqlClient: GraphqlClient) => void = graphqlClient => {
  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  const clientFileProcessing: ClientFileProcessing = new ClientFileProcessing(
    graphqlClient
  )

  if (uploadForm) {
    uploadForm.addEventListener("submit", ev => {
      ev.preventDefault()

      const consignmentId: number = retrieveConsignmentId()

      if (!consignmentId) {
        throw Error("No consignment provided")
      }

      const target: HTMLInputTarget | null = ev.currentTarget
      const files: TdrFile[] = target!.files!.files!

      clientFileProcessing
        .processFiles(consignmentId, files.length)
        .then(r => {
          clientFileProcessing.processMetadata(files, r).then(md =>
            //For now print number of file metadata returned
            console.log("Returned metadata: " + md.length)
          )
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
