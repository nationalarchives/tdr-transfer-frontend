import { configureAws } from "./aws-config"
import { GraphqlClient } from "./graphql"
import { getKeycloakInstance, authenticateAndGetIdentityId } from "./auth"
import { FileUploader } from "./upload"
import { ClientFileMetadataUpload } from "./clientfilemetadataupload"
import { goToNextPage } from "./upload/next-page-redirect"
import { FileChecks } from "./filechecks"
import { CognitoIdentity, STS } from "aws-sdk"
import { initAll } from "govuk-frontend"
import { UpdateConsignmentStatus } from "./updateconsignmentstatus"

window.onload = function () {
  initAll()

  renderModules()
}

export interface IFrontEndInfo {
  apiUrl: string
  identityProviderName: string
  identityPoolId: string
  stage: string
  region: string
  cognitoEndpointOverride?: string
  s3EndpointOverride?: string
  cognitoRoleArn: string
}

const getFrontEndInfo: () => IFrontEndInfo = () => {
  const identityPoolElement: HTMLInputElement | null =
    document.querySelector(".identity-pool-id")
  const apiUrlElement: HTMLInputElement | null =
    document.querySelector(".api-url")
  const identityProviderNameElement: HTMLInputElement | null =
    document.querySelector(".identity-provider-name")
  const stageElement: HTMLInputElement | null = document.querySelector(".stage")
  const regionElement: HTMLInputElement | null =
    document.querySelector(".region")
  const cognitoEndpointOverrideElement: HTMLInputElement | null =
    document.querySelector(".cognito-endpoint-override")
  const s3EndpointOverrideElement: HTMLInputElement | null =
    document.querySelector(".s3-endpoint-override")
  const cogitoRoleArnElement: HTMLInputElement | null =
    document.querySelector(".cognito-role-arn")

  if (
    apiUrlElement &&
    identityProviderNameElement &&
    identityPoolElement &&
    stageElement &&
    regionElement &&
    cogitoRoleArnElement
  ) {
    return {
      apiUrl: apiUrlElement.value,
      identityProviderName: identityProviderNameElement.value,
      identityPoolId: identityPoolElement.value,
      stage: stageElement.value,
      region: regionElement.value,
      cognitoEndpointOverride: cognitoEndpointOverrideElement?.value,
      s3EndpointOverride: s3EndpointOverrideElement?.value,
      cognitoRoleArn: cogitoRoleArnElement.value
    }
  } else {
    throw "The front end information is missing"
  }
}

export const renderModules = () => {
  const uploadContainer: HTMLDivElement | null =
    document.querySelector("#file-upload")
  const fileChecksContainer: HTMLDivElement | null = document.querySelector(
    ".file-check-progress"
  )
  if (uploadContainer) {
    uploadContainer.removeAttribute("hidden")
    const frontEndInfo = getFrontEndInfo()

    configureAws(frontEndInfo)

    getKeycloakInstance().then((keycloak) => {
      const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
      const cognitoIdentity = new CognitoIdentity({
        region: frontEndInfo.region
      })
      authenticateAndGetIdentityId(
        keycloak,
        frontEndInfo,
        cognitoIdentity,
        new STS({ region: frontEndInfo.region })
      ).then((identityId) => {
        const clientFileProcessing = new ClientFileMetadataUpload(graphqlClient)
        const updateConsignmentStatus = new UpdateConsignmentStatus(
          graphqlClient
        )
        new FileUploader(
          clientFileProcessing,
          updateConsignmentStatus,
          identityId,
          frontEndInfo,
          goToNextPage
        ).initialiseFormListeners()
      })
    })
  }
  if (fileChecksContainer) {
    const frontEndInfo = getFrontEndInfo()
    getKeycloakInstance().then((keycloak) => {
      const graphqlClient = new GraphqlClient(frontEndInfo.apiUrl, keycloak)
      new FileChecks(graphqlClient).updateFileCheckProgress()
    })
  }
  else {
    getKeycloakInstance().then(keycloak => {
      const cacheKey = "numberOfTimeoutWarnings"
      localStorage.setItem(cacheKey, "0")
      const now: () => number = () => Math.round((new Date()).getTime() / 1000)
      const timeToExpiry = 60
      //Set min validity to the length of the access token so it will always get a new one.
      const minValidity = 60
      const timeoutCheck = setInterval(() => {
        if (keycloak.refreshTokenParsed!.exp! - now() < timeToExpiry) {
          showModal()
        }
      }, 2000)

      const showModal: () => void = () => {
        const numberOfWarnings = localStorage.getItem(cacheKey)
        if (numberOfWarnings && parseInt(numberOfWarnings, 10) > 5) {
          clearInterval(timeoutCheck)
        } else {
          const timeout: HTMLDialogElement | null = document.querySelector("#timeout")
          const update: () => void = () => {
            keycloak.updateToken(minValidity).then(e => {
              if (e && timeout && timeout.open) {
                timeout.close()
              }
            })
          }
          if (timeout && !timeout.open) {
            timeout.showModal()
            const extendTimeout: HTMLButtonElement | null = document.querySelector("#extend-timeout")
            const cancelTimeout: HTMLAnchorElement | null = document.querySelector("#cancel-extend")
            if (extendTimeout) {
              extendTimeout.addEventListener("click", (ev) => {
                localStorage.setItem(cacheKey, (parseInt(numberOfWarnings!, 10) + 1).toString())
                ev.preventDefault()
                update()
              })
            }
            if (cancelTimeout) {
              cancelTimeout.addEventListener("click", ev => {
                clearInterval(timeoutCheck)
                timeout.close()
              })
            }
          }
        }
      }

    })
  }
}
