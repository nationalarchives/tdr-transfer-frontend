import { GraphqlClient } from "./graphql"
import { getKeycloakInstance, authenticateAndGetIdentityId } from "./auth"
import { UploadFiles } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"

declare var API_URL: string

window.onload = function() {
  renderModules()
}

export const renderModules = () => {
  const uploadContainer: HTMLDivElement | null = document.querySelector(
    ".govuk-file-upload"
  )

  if (uploadContainer) {
    getKeycloakInstance().then(keycloak => {
      const graphqlClient = new GraphqlClient(API_URL, keycloak)
      authenticateAndGetIdentityId(keycloak).then(identityId => {
        const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
        new UploadFiles(clientFileProcessing, identityId).upload()
      })
    })
  }
}
