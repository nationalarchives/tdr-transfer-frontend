import { GraphqlClient } from "./graphql"
import { getToken, authenticateAndGetIdentityId } from "./auth"
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
    getToken().then(keycloak => {
      const graphqlClient = new GraphqlClient(TDR_API_URL, keycloak)
      authenticateAndGetIdentityId(keycloak).then(identityId => {
        const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
        new UploadFiles(clientFileProcessing, identityId).upload()
      })
    })
  }
}
