import { ClientFileProcessing } from "../src/clientfileprocessing"
import { KeycloakInstance } from "keycloak-js"
import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { UploadFiles } from "../src/upload"

jest.mock("../src/clientfilemetadataupload")

beforeEach(() => jest.resetModules())

const mockKeycloak: KeycloakInstance<"native"> = {
  init: jest.fn(),
  login: jest.fn(),
  logout: jest.fn(),
  register: jest.fn(),
  accountManagement: jest.fn(),
  createLoginUrl: jest.fn(),
  createLogoutUrl: jest.fn(),
  createRegisterUrl: jest.fn(),
  createAccountUrl: jest.fn(),
  isTokenExpired: jest.fn(),
  updateToken: jest.fn(),
  clearToken: jest.fn(),
  hasRealmRole: jest.fn(),
  hasResourceRole: jest.fn(),
  loadUserInfo: jest.fn(),
  loadUserProfile: jest.fn(),
  token: "fake-auth-token"
}

class ClientFileMetadataUploadSuccess {
  upload: (consignmentId: string, files: TdrFile[]) => Promise<void> = async (
    consignmentId: string,
    files: TdrFile[]
  ) => {
    return Promise.resolve()
  }
}

class ClientFileMetadataUploadFailure {
  upload: (consignmentId: string, files: TdrFile[]) => Promise<void> = async (
    consignmentId: string,
    files: TdrFile[]
  ) => {
    return Promise.reject(Error("Client file metadata upload failed"))
  }
}

const mockSuccess: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileMetadataUploadSuccess()
  })
}

const mockFailure: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileMetadataUploadFailure()
  })
}

function setUpUploadFiles(): UploadFiles {
  const client = new GraphqlClient("test", mockKeycloak)
  const clientFileMetadataUpload: ClientFileMetadataUpload = new ClientFileMetadataUpload(
    client
  )

  return new UploadFiles(clientFileMetadataUpload)
}

test("upload console logs error when upload fails", () => {
  const consoleSpy = jest.spyOn(console, "error").mockImplementation(() => {})
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form" data-consignment-id="12345">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  if (uploadForm) {
    uploadForm.submit()
  }

  expect(consoleSpy).toHaveBeenCalled()
})

test("upload console logs error no consignment id provided", () => {
  const consoleSpy = jest.spyOn(console, "error").mockImplementation(() => {})
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  if (uploadForm) {
    uploadForm.submit()
  }

  expect(consoleSpy).toHaveBeenCalled()
})
