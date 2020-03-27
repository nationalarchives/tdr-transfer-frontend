import Keycloak from "keycloak-js"
import { Key } from "readline"

export const getToken: () => Promise<
  Keycloak.KeycloakInstance<"native">
> = async () => {
  //const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak()
  const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )

  console.log("Keycloak: " + keycloak.clientId)

  console.log("In get token")

  const authenticated: boolean = await keycloak.init({
    promiseType: "native",
    onLoad: "check-sso"
  })

  console.log("Authenticated: " + authenticated)
  if (authenticated) {
    console.log("Returning token")
    return keycloak
  } else {
    throw "User is not authenticated"
  }
}
