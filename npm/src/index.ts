import { GraphqlClient } from "./graphql"
import { getKeycloakInstance, authenticateAndGetIdentityId } from "./auth"
import { UploadFiles } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"

window.onload = function() {
  renderModules()
}

export interface IFrontEndInfo {
  apiUrl: string
  identityProviderName: string
  identityPoolId: string
  stage: string
  region: string
}

const getFrontEndInfo: () => IFrontEndInfo = () => {
  const identityPoolElement: HTMLInputElement | null = document.querySelector(
    ".identity-pool-id"
  )
  const apiUrlElement: HTMLInputElement | null = document.querySelector(
    ".api-url"
  )
  const identityProviderNameElement: HTMLInputElement | null = document.querySelector(
    ".identity-provider-name"
  )
  const stageElement: HTMLInputElement | null = document.querySelector(".stage")
  const regionElement: HTMLInputElement | null = document.querySelector(
    ".region"
  )

  if (
    identityPoolElement &&
    apiUrlElement &&
    identityProviderNameElement &&
    stageElement &&
    regionElement
  ) {
    return {
      apiUrl: apiUrlElement.value,
      identityProviderName: identityProviderNameElement.value,
      identityPoolId: identityPoolElement.value,
      stage: stageElement.value,
      region: regionElement.value
    }
  } else {
    throw "The front end information is missing"
  }
}

export const renderModules = () => {
  const uploadContainer: HTMLDivElement | null = document.querySelector(
    ".govuk-file-upload"
  )
  if (uploadContainer) {
    const frontEndInfo = getFrontEndInfo()
    getKeycloakInstance().then(keycloak => {
      const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
      authenticateAndGetIdentityId(keycloak, frontEndInfo).then(identityId => {
        const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
        new UploadFiles(
          clientFileProcessing,
          identityId,
          frontEndInfo.stage
        ).upload()
      })
    })
  }
}
