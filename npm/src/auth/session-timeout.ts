import Keycloak from "keycloak-js"

const timeoutDialog: HTMLDialogElement | null =
  document.querySelector(".timeout-dialog")

export const initialiseSessionTimeout = async (): Promise<void> => {
  const authModule = await import("./index")
  const errorHandlingModule = await import("../errorhandling")
  authModule.getKeycloakInstance().then((keycloak) => {
    const now: () => number = () => Math.round(new Date().getTime() / 1000)
    //Set timeToShowDialog to how many seconds from expiry you want the dialog log box to appear
    const timeToShowDialog = 300
    setInterval(() => {
      if (!errorHandlingModule.isError(keycloak)) {
        const timeUntilExpire = keycloak.refreshTokenParsed!.exp! - now()
        if (timeUntilExpire < 0) {
          keycloak.logout()
        } else if (timeUntilExpire < timeToShowDialog) {
          showModal(keycloak)
        }
      }
    }, 2000)
  })
}

const showModal = async (keycloak: Keycloak): Promise<void> => {
  const extendTimeout: HTMLButtonElement | null =
    document.querySelector("#extend-timeout")
  if (timeoutDialog && !timeoutDialog.open) {
    timeoutDialog.showModal()
    if (extendTimeout) {
      extendTimeout.addEventListener("click", (ev) => {
        ev.preventDefault()
        refreshToken(keycloak)
      })
    }
  }
}
//Function for extending the keycloak session
const refreshToken = async (keycloak: Keycloak): Promise<void> => {
  //Set min validity to the length of the access token, so it will always get a new one.
  const minValidity = 3600
  const errorHandlingModule = await import("../errorhandling")
  if (!errorHandlingModule.isError(keycloak)) {
    keycloak.updateToken(minValidity).then((e) => {
      if (e && timeoutDialog && timeoutDialog.open) {
        timeoutDialog.close()
      }
    })
  }
}
