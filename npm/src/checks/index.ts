import {
  hasDraftMetadataValidationCompleted,
  haveFileChecksCompleted
} from "./verify-checks-have-completed"
import { displayChecksCompletedBanner } from "./display-checks-completed-banner"
import {
  continueTransfer,
  getDraftMetadataValidationProgress,
  getFileChecksProgress,
  getTransferProgress,
  IDraftMetadataValidationProgress,
  IFileCheckProgress
} from "./get-checks-progress"
import { isError } from "../errorhandling"
import Keycloak from "keycloak-js";
import {IFrontEndInfo} from "../index";
import { scheduleTokenRefresh } from "../auth";

export class Checks {
  checkJudgmentTransferProgress: (
    goToNextPage: (formId: string) => void
  ) => void | Error = (goToNextPage: (formId: string) => void) => {
    continueTransfer()
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const isCompleted: Boolean | Error = await getTransferProgress()
      if (!isError(isCompleted)) {
        if (isCompleted) {
          clearInterval(intervalId)
          goToNextPage("#file-checks-form")
        }
      } else {
        return isCompleted
      }
    }, 5000)
  }

  updateFileCheckProgress: (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void,
    keycloak: Keycloak,
    frontEndInfo: IFrontEndInfo
  ) => Promise<void | Error> = async (
    isJudgmentUser: boolean,
    goToNextPage: (formId: string) => void,
    keycloak: Keycloak,
    frontEndInfo: IFrontEndInfo
  ) => {
    if (isJudgmentUser) {
      this.checkJudgmentTransferProgress(goToNextPage)
    } else {

      const cookiesUrl = `https://app.tdr-integration.nationalarchives.gov.uk/cookies`
      console.log("+++++ cookiesUrl: " + cookiesUrl)
        //TODO: this only impacts the 3 hours session token
      scheduleTokenRefresh(keycloak, cookiesUrl)
      const intervalId: ReturnType<typeof setInterval> = setInterval(
        async () => {
          const fileChecksProgress: IFileCheckProgress | Error =
            await getFileChecksProgress()
          if (!isError(fileChecksProgress)) {
            const checksCompleted = haveFileChecksCompleted(fileChecksProgress)
              //TODO: force refresh of the token
              keycloak.updateToken(30)
              console.log("===> IDTP.exp" + new Date(keycloak.idTokenParsed?.exp * 1000).toString())
              console.log("===>   TP.exp" + new Date(keycloak.tokenParsed?.exp * 1000).toString())
              console.log("===>  RTP.exp" + new Date(keycloak.refreshTokenParsed?.exp * 1000).toString())
              console.log("===>  expired" + (keycloak.isTokenExpired()))
            if (checksCompleted) {
             // clearInterval(intervalId)
             // displayChecksCompletedBanner("file-checks")
            }
         } else {
           console.log("===> an error in fileChecksProgress")
            return fileChecksProgress
          }
        },
        10000
      )
    }
  }

  updateDraftMetadataValidationProgress: () => void | Error = () => {
    const intervalId: ReturnType<typeof setInterval> = setInterval(async () => {
      const validationChecksProgress: IDraftMetadataValidationProgress | Error =
        await getDraftMetadataValidationProgress()
      if (!isError(validationChecksProgress)) {
        const checksCompleted = hasDraftMetadataValidationCompleted(
          validationChecksProgress
        )
        if (checksCompleted) {
          clearInterval(intervalId)
          displayChecksCompletedBanner("draft-metadata-checks")
        }
      } else {
        clearInterval(intervalId)
        return validationChecksProgress
      }
    }, 5000)
  }
}
