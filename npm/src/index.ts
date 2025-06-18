import { initAll } from "govuk-frontend"
import {
  NestedNavigation,
  InputType,
  MultiSelectSearch,
  ButtonDisabled
} from "@nationalarchives/tdr-components"

window.onload = async function () {
  initAll()
  await renderModules()
}

export interface IFrontEndInfo {
  apiUrl: string
  uploadUrl: string
  authUrl: string
  stage: string
  region: string
  clientId: string
  realm: string,
  awsAccessKeyId: string,
  awsSecretAccessKey: string,
  awsSessionToken: string
}

const getFrontEndInfo: () => IFrontEndInfo | Error = () => {
  const apiUrlElement: HTMLInputElement | null =
    document.querySelector(".api-url")
  const stageElement: HTMLInputElement | null = document.querySelector(".stage")
  const regionElement: HTMLInputElement | null =
    document.querySelector(".region")
  const uploadUrlElement: HTMLInputElement | null =
    document.querySelector(".upload-url")
  const authUrlElement: HTMLInputElement | null =
    document.querySelector(".auth-url")
  const clientIdElement: HTMLInputElement | null =
    document.querySelector(".client-id")
  const awsAccessKeyId: HTMLInputElement | null =
      document.querySelector(".awsAccessKey")
  const awsSecretAccessKey: HTMLInputElement | null =
      document.querySelector(".awsAccessSecret")
  const awsSessionToken: HTMLInputElement | null =
      document.querySelector(".awsAccessSessionToken")
  const realmElement: HTMLInputElement | null = document.querySelector(".realm")
  if (
    apiUrlElement &&
    stageElement &&
    regionElement &&
    uploadUrlElement &&
    authUrlElement &&
    clientIdElement &&
    realmElement &&
    awsAccessKeyId &&
    awsSecretAccessKey &&
    awsSessionToken
  ) {
    return {
      apiUrl: apiUrlElement.value,
      stage: stageElement.value,
      region: regionElement.value,
      uploadUrl: uploadUrlElement.value,
      authUrl: authUrlElement.value,
      clientId: clientIdElement.value,
      realm: realmElement.value,
      awsAccessKeyId: awsAccessKeyId.value,
      awsSecretAccessKey: awsSecretAccessKey.value,
      awsSessionToken: awsSessionToken.value
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
  const draftMetadataValidationContainer: HTMLDivElement | null =
    document.querySelector(".draft-metadata-validation-progress")
  const timeoutDialog: HTMLDialogElement | null =
    document.querySelector(".timeout-dialog")
  const multiSelectSearch = document.querySelector(".tna-multi-select-search")
  const tableRowExpanderButtons = document.querySelectorAll(
    "[data-module=table-row-expander] button[aria-expanded][aria-controls]"
  )
  const buttonDisabled: NodeListOf<HTMLElement> = document.querySelectorAll(
    '[data-tdr-module="button-disabled"]'
  )

  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      const keycloak = await authModule.getKeycloakInstance(frontEndInfo)
      if (!errorHandlingModule.isError(keycloak)) {
        const uploadModule = await import("./upload")
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
        )

        new uploadModule.FileUploader(
          frontEndInfo,
          keycloak,
          nextPageModule.goToFileChecksPage
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
      const keycloak = await authModule.getKeycloakInstance(frontEndInfo)
      if (!errorHandlingModule.isError(keycloak)) {
        const isJudgmentUser = false
        const checksModule = await import("./checks")
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
        )
        //interval for page reload set at 90% of token validity period
        const checksPageRefreshInterval = 1000000

        const resultOrError = new checksModule.Checks().updateFileCheckProgress(
          isJudgmentUser,
          nextPageModule.goToNextPage,
          checksPageRefreshInterval
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
  }

  if (draftMetadataValidationContainer) {
    const frontEndInfo = getFrontEndInfo()
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      const authModule = await import("./auth")
      const keycloak = await authModule.getKeycloakInstance(frontEndInfo)
      if (!errorHandlingModule.isError(keycloak)) {
        const checksModule = await import("./checks")
        const resultOrError =
          new checksModule.Checks().updateDraftMetadataValidationProgress()
        if (errorHandlingModule.isError(resultOrError)) {
          errorHandlingModule.handleUploadError(resultOrError)
        }
      } else {
        errorHandlingModule.handleUploadError(keycloak)
      }
    } else {
      errorHandlingModule.handleUploadError(frontEndInfo)
    }
  }

  if (timeoutDialog) {
    const frontEndInfo = getFrontEndInfo()
    const sessionTimeoutModule = await import("./auth/session-timeout")
    const errorHandlingModule = await import("./errorhandling")
    if (!errorHandlingModule.isError(frontEndInfo)) {
      // await sessionTimeoutModule.initialiseSessionTimeout(frontEndInfo)
    }
  }

  if (multiSelectSearch) {
    const rootElement: HTMLElement | null = document.querySelector(
      "[data-module=multi-select-search]"
    )
    if (rootElement) {
      const multiSelectSearch = new MultiSelectSearch(rootElement)
      multiSelectSearch.initialise()
    }
  }
  if (tableRowExpanderButtons) {
    const disclosureModule = await import("./viewtransfers/disclosure")
    tableRowExpanderButtons.forEach((btn) => {
      new disclosureModule.Disclosure(btn)
    })
  }
  if (buttonDisabled) {
    buttonDisabled.forEach((button) => {
      const buttonDisabled = new ButtonDisabled(button)
      buttonDisabled.initialiseListeners()
    })
  }
}
