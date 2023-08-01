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
  const fileSelectionTree = document.querySelector(".tna-tree")
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
      const keycloak = await authModule.getKeycloakInstance()
      if (!errorHandlingModule.isError(keycloak)) {
        const metadataUploadModule = await import("./clientfilemetadataupload")
        const clientFileProcessing =
          new metadataUploadModule.ClientFileMetadataUpload()
        const uploadModule = await import("./upload")
        const nextPageModule = await import(
          "./nextpageredirect/next-page-redirect"
        )

        new uploadModule.FileUploader(
          clientFileProcessing,
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
  }

  if (timeoutDialog) {
    const sessionTimeoutModule = await import("./auth/session-timeout")
    await sessionTimeoutModule.initialiseSessionTimeout()
  }
  if (fileSelectionTree) {
    const trees: NodeListOf<HTMLUListElement> =
      document.querySelectorAll("[role=tree]")
    trees.forEach((tree) => {
      const nestedNavigation = new NestedNavigation(tree)
      nestedNavigation.initialiseFormListeners(InputType.radios)
    })
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
