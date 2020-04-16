import Keycloak from "keycloak-js"

export const getKeycloakInstance: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  const keycloakInstance: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )
  const authenticated: boolean = await keycloakInstance.init({
    promiseType: "native",
    onLoad: "check-sso"
  })

  if (authenticated) {
    return keycloakInstance
  } else {
    throw "User is not authenticated"
  }
}
