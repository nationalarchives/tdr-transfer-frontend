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
    const getExpanded: () => string[] = () => {
      const storageItem = localStorage.getItem("state")
      if(storageItem) {
        return JSON.parse(storageItem).expanded
      } else {
        return []
      }
    }
    const allCheckboxes: (input: HTMLInputElement, elements: HTMLInputElement[]) => HTMLInputElement[] = ({children}, elements) => {
      for (let i = children.length - 1; i >= 0; i--) {
        const item: HTMLInputElement | null = children.item(i) as HTMLInputElement | null
        if(item) {
          if(item.nodeName == "LI") {
            const itemCheckbox: HTMLInputElement | null = item.querySelector(".govuk-checkboxes__input")
            if(itemCheckbox){
              elements.push(itemCheckbox)
            }
          } else {
            allCheckboxes(item, elements)
          }
        }
      }
      return elements
    }
    document.querySelectorAll(".nested-file-list").forEach((value, key, parent) => {
      const id = value.getAttribute("id")
      if (id) {
        const expanded = getExpanded()

        if (id != "parent-list" && !expanded.includes(id.replace("folder-group-", ""))) {
          value.classList.add("hide")
        }
      }

    })
    const setState: (input: HTMLInputElement | null) => void = input => {
      if(input) {
        const all = allCheckboxes(input, [])
        const countChecked = all.filter(a => a.checked).length
        const parentCheckbox: HTMLInputElement | null = input.previousElementSibling!.querySelector("input[type=checkbox]")
        if(parentCheckbox) {
          if(countChecked > 0 && countChecked < all.length) {
            parentCheckbox.indeterminate = true
          }
          if(countChecked == all.length) {
            parentCheckbox.indeterminate = false
            parentCheckbox.checked = true
          }
          if(countChecked == 0) {
            parentCheckbox.checked = false
            parentCheckbox.indeterminate = false
          }
          const nextEl: HTMLInputElement | null | undefined = input.parentElement?.closest(".nested-file-list")
          if(nextEl) {
            setState(nextEl)
          }
        }
      }
    }
    document.querySelectorAll("input[type=checkbox]").forEach((checkBox, key, parent) => {
      checkBox.addEventListener("change", (ev) => {
        if(ev.target instanceof HTMLInputElement) {
          const ul: HTMLInputElement | null = ev.target.closest(".nested-file-list")
          if(ev.target.classList.contains("folder-checkbox")) {
            const nextFolder: HTMLInputElement | null = document.querySelector(`#folder-group-${ev.target.id}`)
            if(nextFolder) {
              const checkboxes = allCheckboxes(nextFolder, [])
              for (let checkbox of checkboxes) {
                checkbox.checked = ev.target.checked
              }
            }
          }
          setState(ul)
        }
      })
    })
    document.querySelectorAll(".file-expander").forEach((expander, key, parent) => {
      if (expander) {
        expander.addEventListener("click", (ev) => {
          if (ev.target instanceof Element) {
            const newId = ev.target.id.replace("expander-", "folder-group-")
            const folderGroup: HTMLUListElement | null = document.querySelector(`#${newId}`)

            if (folderGroup) {
              const expanded = getExpanded()
              if (folderGroup.classList.contains("hide")) {
                folderGroup.classList.remove("hide")
                ev.target.innerHTML = " - "
                expanded.push(ev.target.id.replace("expander-", ""))

              } else {
                folderGroup.classList.add("hide")
                ev.target.innerHTML = " + "
                expanded.splice(expanded.indexOf(ev.target.id.replace("expander-", "")))
              }
              localStorage.setItem("state", JSON.stringify({expanded}))
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
