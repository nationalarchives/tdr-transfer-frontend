import { GraphqlClient } from "./graphql"
import { getToken } from "./auth"
import { upload } from "./upload"

declare var TDR_API_URL: string

const client: Promise<GraphqlClient> = getToken().then(
  keycloak => new GraphqlClient(TDR_API_URL, keycloak)
)

window.onload = function() {
  renderModules()
}

const renderModules = () => {
  const uploadContainer: HTMLDivElement | null = document.querySelector(
    ".govuk-file-upload"
  )

  if (uploadContainer) {
    client.then(graphqlClient => upload(graphqlClient))
  }
}
