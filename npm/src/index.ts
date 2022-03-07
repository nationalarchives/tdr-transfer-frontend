import { GraphqlClient } from "./graphql"
import { getKeycloakInstance } from "./auth"
import { FileUploader } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"
import { goToNextPage } from "./nextpageredirect/next-page-redirect"
import { FileChecks } from "./filechecks"
import { initAll } from "govuk-frontend"
import { UpdateConsignmentStatus } from "./updateconsignmentstatus"
import { handleUploadError, isError } from "./errorhandling"

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

const getFrontEndInfo: () => IFrontEndInfo | Error = () => {
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
    return Error("The front end information is missing")
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
    if (!isError(frontEndInfo)) {
      getKeycloakInstance().then((keycloak) => {
        if (!isError(keycloak)) {
          const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
          const clientFileProcessing = new ClientFileMetadataUpload(
            graphqlClient
          )
          const updateConsignmentStatus = new UpdateConsignmentStatus(
            graphqlClient
          )
          new FileUploader(
            clientFileProcessing,
            updateConsignmentStatus,
            frontEndInfo,
            goToNextPage,
            keycloak
          ).initialiseFormListeners()
        } else {
          handleUploadError(keycloak)
        }
      })
    } else {
      handleUploadError(frontEndInfo)
    }
  }
  if (fileChecksContainer) {
    const frontEndInfo = getFrontEndInfo()
    if (!isError(frontEndInfo)) {
      getKeycloakInstance().then((keycloak) => {
        if (!isError(keycloak)) {
          const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
          const isJudgmentUser = keycloak.tokenParsed?.judgment_user
          const resultOrError = new FileChecks(
            graphqlClient
          ).updateFileCheckProgress(isJudgmentUser, goToNextPage)
          if (isError(resultOrError)) {
            handleUploadError(resultOrError)
          }
        } else {
          handleUploadError(keycloak)
        }
      })
    } else {
      handleUploadError(frontEndInfo)
    }
  }
}
