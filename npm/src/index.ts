import { configureAws } from "./aws-config"
import { GraphqlClient } from "./graphql"
import { getKeycloakInstance } from "./auth"
import { FileUploader } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"
import { goToNextPage } from "./upload/next-page-redirect"
import { FileChecks } from "./filechecks"
import { initAll } from "govuk-frontend"
import { UpdateConsignmentStatus } from "./updateconsignmentstatus"

window.onload = function () {
  initAll()

  renderModules()
}

export interface IFrontEndInfo {
  apiUrl: string
  uploadUrl: string
  stage: string
  region: string
}

const getFrontEndInfo: () => IFrontEndInfo = () => {
  const apiUrlElement: HTMLInputElement | null =
    document.querySelector(".api-url")
  const stageElement: HTMLInputElement | null = document.querySelector(".stage")
  const regionElement: HTMLInputElement | null =
    document.querySelector(".region")
  const uploadUrlElement: HTMLInputElement | null =
    document.querySelector(".upload-url")
  if (apiUrlElement && stageElement && regionElement && uploadUrlElement) {
    return {
      apiUrl: apiUrlElement.value,
      stage: stageElement.value,
      region: regionElement.value,
      uploadUrl: uploadUrlElement.value
    }
  } else {
    throw "The front end information is missing"
  }
}

export const renderModules = () => {
  const uploadContainer: HTMLDivElement | null =
    document.querySelector("#file-upload")
  const fileChecksContainer: HTMLDivElement | null = document.querySelector(
    ".file-check-progress"
  )
  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()
    getKeycloakInstance().then((keycloak) => {
      configureAws(frontEndInfo)
      const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
      const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
      const updateConsignmentStatus = new UpdateConsignmentStatus(graphqlClient)
      new FileUploader(
        clientFileProcessing,
        updateConsignmentStatus,
        frontEndInfo,
        goToNextPage,
        keycloak
      ).initialiseFormListeners()
    })
  }
  if (fileChecksContainer) {
    const frontEndInfo = getFrontEndInfo()
    getKeycloakInstance().then((keycloak) => {
      const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
      new FileChecks(graphqlClient).updateFileCheckProgress()
    })
  }
}
