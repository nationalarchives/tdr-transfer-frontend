import { initAll } from "govuk-frontend"

window.onload = async function () {
  initAll()
  await renderModules()
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
  const timeoutDialog: HTMLDialogElement | null =
    document.querySelector(".timeout-dialog")
  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      const keycloak = await authModule.getKeycloakInstance()
      if (!errorHandlingModule.isError(keycloak)) {
        const graphQlModule = await import("./graphql")
        const graphqlClient = new graphQlModule.GraphqlClient(
          frontEndInfo.apiUrl,
          keycloak
        )
        const metadataUploadModule = await import("./clientfilemetadataupload")
        const clientFileProcessing =
          new metadataUploadModule.ClientFileMetadataUpload(graphqlClient)
        const consignmentStatusModule = await import(
          "./updateconsignmentstatus"
        )
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
        )
        const uploadModule = await import("./upload")
        const updateConsignmentStatus =
          new consignmentStatusModule.UpdateConsignmentStatus(graphqlClient)
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
    } else {
      errorHandlingModule.handleUploadError(frontEndInfo)
    }
  }
  if (fileChecksContainer) {
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      const keycloak = await authModule.getKeycloakInstance()
      if (!errorHandlingModule.isError(keycloak)) {
        const graphQlModule = await import("./graphql")
        const graphqlClient = new graphQlModule.GraphqlClient(
          frontEndInfo.apiUrl,
          keycloak
        )
        const isJudgmentUser = keycloak.tokenParsed?.judgment_user
        const fileChecksModule = await import("./filechecks")
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
        )
        const resultOrError = new fileChecksModule.FileChecks(
          graphqlClient
        ).updateFileCheckProgress(isJudgmentUser, nextPageModule.goToNextPage)
        if (errorHandlingModule.isError(resultOrError)) {
          errorHandlingModule.handleUploadError(resultOrError)
        }
      } else {
        errorHandlingModule.handleUploadError(keycloak)
      }
    } else {
      errorHandlingModule.handleUploadError(frontEndInfo)
    }
  } else if (timeoutDialog) {
    const authModule = await import("./auth")
    const errorHandlingModule = await import("./errorhandling")
    authModule.getKeycloakInstance().then((keycloak) => {
      const now: () => number = () => Math.round(new Date().getTime() / 1000)
      console.log(now())
      const timeToExpiry = 60 //You are x number of time from expiry this should match the 5-minute text
      //Set min validity to the length of the access token, so it will always get a new one.
      const minValidity = 300 //This will be set to 1 hour(seconds)
      setInterval(() => {
        if (!errorHandlingModule.isError(keycloak)) {
          console.log(keycloak.refreshTokenParsed!.exp! - now())
          if (keycloak.refreshTokenParsed!.exp! - now() < 0) {
            console.log("5 minutes up, the session has expired")
            keycloak.logout()
          } else if (keycloak.refreshTokenParsed!.exp! - now() < timeToExpiry) {
            showModal()
          }
        }
      }, 2000)

      const showModal: () => void = () => {
        //Method for extending the keycloak session
        const updateToken: () => void = () => {
          if (!errorHandlingModule.isError(keycloak)) {
            keycloak.updateToken(minValidity).then((e) => {
              if (e && timeoutDialog && timeoutDialog.open) {
                timeoutDialog.close()
              }
            })
          }
        }
        if (timeoutDialog && !timeoutDialog.open) {
          timeoutDialog.showModal()
          const extendTimeout: HTMLButtonElement | null =
            document.querySelector("#extend-timeout")
          //This will clear the dialog box and update the keycloak session extending it
          if (extendTimeout) {
            extendTimeout.addEventListener("click", (ev) => {
              ev.preventDefault()
              updateToken()
            })
          }
        }
      }
    })
  }
}
