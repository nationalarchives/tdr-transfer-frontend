import { GraphqlClient } from "./graphql"
import { getKeycloakInstance } from "./auth"
import { UploadFiles } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"

declare var TDR_API_URL: string

window.onload = function() {
  renderModules()
}

export const renderModules = () => {
  const uploadContainer: HTMLDivElement | null = document.querySelector(
    ".govuk-file-upload"
  )

  if (uploadContainer) {
    const client: Promise<GraphqlClient> = getKeycloakInstance().then(
      keycloak => new GraphqlClient(TDR_API_URL, keycloak)
    )

    client.then(graphqlClient => {
      const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
      new UploadFiles(clientFileProcessing).upload()
    })
  }
}
