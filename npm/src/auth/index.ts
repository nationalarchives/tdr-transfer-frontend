import Keycloak from "keycloak-js"

export const getToken: () => Promise<string | undefined> = async () => {
  const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak(
    `${window.location.origin}/keycloak.json`
  )

  const authenticated: boolean = await keycloak.init({
    promiseType: "native",
    onLoad: "check-sso"
  })

  if (authenticated) {
    return keycloak.token
  } else {
    throw "User is not authenticated"
  }
}
