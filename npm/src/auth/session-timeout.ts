import Keycloak from "keycloak-js"

const timeoutDialog: HTMLDialogElement | null =
  document.querySelector(".timeout-dialog")

export const initialiseSessionTimeout = async (): Promise<void> => {
  const authModule = await import("./index")
  const errorHandlingModule = await import("../errorhandling")
  const keycloak = await authModule.getKeycloakInstance()
  const now: () => number = () => Math.round(new Date().getTime() / 1000)
  //Set timeToShowDialog to how many seconds from expiry you want the dialog log box to appear
  const timeToShowDialog = 300
  await setInterval(async () => {
    if (!errorHandlingModule.isError(keycloak)) {
      const timeUntilExpire = keycloak.refreshTokenParsed!.exp! - now()
      if (timeUntilExpire < 0) {
        keycloak.logout()
      } else if (timeUntilExpire < timeToShowDialog) {
        await showModal(keycloak)
      }
    }
  }, 2000)
}

const showModal = async (keycloak: Keycloak): Promise<void> => {
  const extendTimeout: HTMLButtonElement | null =
    document.querySelector("#extend-timeout")
  if (timeoutDialog && !timeoutDialog.open) {
    timeoutDialog.showModal()
    if (extendTimeout) {
      await extendTimeout.addEventListener("click", async (ev) => {
        ev.preventDefault()
        await refreshToken(keycloak)
      })
    }
  }
}

const refreshToken = async (keycloak: Keycloak): Promise<void> => {
  //Set min validity to the length of the access token, so it will always get a new one.
  const minValiditySec = 3600
  const errorHandlingModule = await import("../errorhandling")
  if (!errorHandlingModule.isError(keycloak)) {
    const updateSuccessful = await keycloak.updateToken(minValiditySec)
    if (updateSuccessful && timeoutDialog && timeoutDialog.open) {
      timeoutDialog.close()
    }
  }
}
