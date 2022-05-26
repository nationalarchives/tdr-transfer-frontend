import { initAll } from "govuk-frontend"

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

export const renderModules = async () => {
  const uploadContainer: HTMLDivElement | null =
    document.querySelector("#file-upload")
  const fileChecksContainer: HTMLDivElement | null = document.querySelector(
    ".file-check-progress"
  )
  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      authModule.getKeycloakInstance().then(async (keycloak) => {
        if (!errorHandlingModule.isError(keycloak)) {
          const graphQlModule = await import("./graphql")
          const graphqlClient = new graphQlModule.GraphqlClient(
            frontEndInfo.apiUrl,
            keycloak
          )
          const metadataUploadModule = await import("./clientfilemetadataupload")
          const clientFileProcessing = new metadataUploadModule.ClientFileMetadataUpload(
            graphqlClient
          )
          const consignmentStatusModule = await import("./updateconsignmentstatus")
          const nextPageModule = await import("./nextpageredirect/next-page-redirect")
          const uploadModule = await import("./upload")
          const updateConsignmentStatus = new consignmentStatusModule.UpdateConsignmentStatus(graphqlClient)
          new uploadModule.FileUploader(
            clientFileProcessing,
            updateConsignmentStatus,
            frontEndInfo,
            nextPageModule.goToNextPage,
            keycloak
          ).initialiseFormListeners()
        } else {
          errorHandlingModule.handleUploadError(keycloak)
        }
      })
    } else {
      errorHandlingModule.handleUploadError(frontEndInfo)
    }
  }
  if (fileChecksContainer) {
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      authModule.getKeycloakInstance().then(async (keycloak) => {
        if (!errorHandlingModule.isError(keycloak)) {
          const graphQlModule = await import("./graphql")
          const graphqlClient = new graphQlModule.GraphqlClient(
            frontEndInfo.apiUrl,
            keycloak
          )
          const isJudgmentUser = keycloak.tokenParsed?.judgment_user
          const fileChecksModule = await import("./filechecks")
          const nextPageModule = await import("./nextpageredirect/next-page-redirect")
          const resultOrError = new fileChecksModule.FileChecks(
            graphqlClient
          ).updateFileCheckProgress(isJudgmentUser, nextPageModule.goToNextPage)
          if (errorHandlingModule.isError(resultOrError)) {
            errorHandlingModule.handleUploadError(resultOrError)
          }
        } else {
          errorHandlingModule.handleUploadError(keycloak)
        }
      })
    } else {
      errorHandlingModule.handleUploadError(frontEndInfo)
    }
  }
}
