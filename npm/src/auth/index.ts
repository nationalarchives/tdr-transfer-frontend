import Keycloak from "keycloak-js"

export const getToken: () => Promise<string | undefined> = async () => {
  const keycloak: Keycloak.KeycloakInstance<"native"> = Keycloak()
  console.log(Keycloak)
  return ""
  // const isAuthenticated: boolean = await keycloak.init({
  //   promiseType: "native",
  //   onLoad: "check-sso"
  // })
  // if (isAuthenticated) {
  //   return keycloak.token
  // }
}
