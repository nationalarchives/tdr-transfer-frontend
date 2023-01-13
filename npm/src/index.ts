import { initAll } from "govuk-frontend"
import { NestedNavigation, InputType } from "@nationalarchives/tdr-components"
import { MultiSelectSearch } from "@nationalarchives/tdr-components"

window.onload = async function () {
  initAll()
  await renderModules()
  hideConditionalElements()
}

export interface IFrontEndInfo {
  apiUrl: string
  uploadUrl: string
  stage: string
  region: string
}

export const hideConditionalElements: () => void = () => {
  // We display all conditional elements by default if JavaScript is disabled; if it's enabled, then we'll hide them.
  const classNameAndConditionalElements: {
    [className: string]: NodeListOf<Element>
  } = {
    "govuk-radios__conditional": document.querySelectorAll(
      ".govuk-radios__conditional"
    ),
    "govuk-checkboxes__conditional": document.querySelectorAll(
      ".govuk-checkboxes__conditional"
    )
  }

  for (const [className, conditionalElements] of Object.entries(
    classNameAndConditionalElements
  )) {
    if (conditionalElements) {
      conditionalElements.forEach((conditionalElement) =>
        conditionalElement.classList.add(`${className}--hidden`)
      )
    }
  }
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
  const fileNavigation = document.querySelector(".tna-tree")
  const timeoutDialog: HTMLDialogElement | null =
    document.querySelector(".timeout-dialog")
  const multiSelectSearch = document.querySelector(".tna-multi-select-search")

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
  if (fileNavigation) {
    const treeItems: NodeListOf<HTMLUListElement> =
      document.querySelectorAll("[role=tree]")
    const tree: HTMLUListElement | null = document.querySelector("[role=tree]")
    const treeItemList: HTMLUListElement[] = []
    if (tree != null) {
      treeItems.forEach((item) => treeItemList.push(item))
      const nestedNavigation = new NestedNavigation(tree, treeItemList)
      nestedNavigation.initialiseFormListeners(InputType.radios)
    }
    const form = document.querySelector("form")
    if (form) {
      form.addEventListener("submit", async (ev) => {
        ev.preventDefault()
        const body = new URLSearchParams()
        document
          .querySelectorAll("li[aria-checked=true]")
          .forEach((el, _, __) => {
            body.set(el.id, "on")
          })
        const csrfInput: HTMLInputElement | null = document.querySelector(
          "input[name='csrfToken']"
        )
        fetch(form.action, {
          body,
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "Csrf-Token": csrfInput!.value,
            "X-Requested-With": "XMLHttpRequest"
          },
          redirect: "follow"
        }).then((res) => {
          window.location.replace(res.url)
        })
      })
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
}
