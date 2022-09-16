import {initAll} from "govuk-frontend"

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
  const fileNavigationContainer: HTMLUListElement | null = document.querySelector(".nested-file-list")
  const timeoutDialog: HTMLDialogElement | null =
    document.querySelector(".timeout-dialog")
  if (fileNavigationContainer) {
    document.querySelectorAll(".nested-file-list").forEach((value, key, parent) => {
      if (value.getAttribute("id") != "parent-list") {
        value.classList.add("hide")
      }
    })
    document.querySelectorAll(".file-expander").forEach((expander, key, parent) => {
      if (expander) {
        expander.addEventListener("click", (ev) => {
          if (ev.target instanceof Element) {
            const newId = ev.target.id.replace("expander-", "folder-group-")
            const folderGroup: HTMLUListElement | null = document.querySelector(`#${newId}`)
            if(folderGroup) {
              if(folderGroup.classList.contains("hide")) {
                folderGroup.classList.remove("hide")
                ev.target.innerHTML = " - "
              } else {
                folderGroup.classList.add("hide")
                ev.target.innerHTML = " + "
              }

            }
          }
        })
      }
    })

  }
  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      const keycloak = await authModule.getKeycloakInstance()
      if (!errorHandlingModule.isError(keycloak)) {
        const metadataUploadModule = await import("./clientfilemetadataupload")
        const clientFileProcessing =
          new metadataUploadModule.ClientFileMetadataUpload()
        const consignmentStatusModule = await import(
          "./updateconsignmentstatus"
          )
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
          )
        const uploadModule = await import("./upload")
        const updateConsignmentStatus =
          new consignmentStatusModule.UpdateConsignmentStatus()
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
        const isJudgmentUser = keycloak.tokenParsed?.judgment_user
        const fileChecksModule = await import("./filechecks")
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
          )
        const resultOrError =
          new fileChecksModule.FileChecks().updateFileCheckProgress(
            isJudgmentUser,
            nextPageModule.goToNextPage
          )
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
    const sessionTimeoutModule = await import("./auth/session-timeout")
    await sessionTimeoutModule.initialiseSessionTimeout()
  }
}
