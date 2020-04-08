import { ClientFileProcessing } from "../src/clientprocessing"
import { UploadFiles } from "../src/upload"
import { GraphqlClient } from "../src/graphql"
import { KeycloakInstance } from "keycloak-js"
import SpyInstance = jest.SpyInstance

jest.mock("../src/clientprocessing")

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

test("generateFileDetails returns file ids", async () => {
  const client = new GraphqlClient("test", mockKeycloak)
  const spyProcessing: SpyInstance = jest
    .spyOn(ClientFileProcessing.prototype, "processFiles")
    .mockResolvedValue(["1", "2"])
  const clientFileProcessing: ClientFileProcessing = new ClientFileProcessing(
    client
  )
  const uploadFiles = new UploadFiles(clientFileProcessing)

  const result = await uploadFiles.generateFileDetails("1", 2)
  expect(spyProcessing).toBeCalledTimes(1)
  expect(result).toHaveLength(2)
  expect(result[0]).toBe("1")
  expect(result[1]).toBe("2")

  spyProcessing.mockReset()
})

test("generateFileDetails returns an error if adding Files fails", async () => {
  const client = new GraphqlClient("test", mockKeycloak)
  const spyProcessing: SpyInstance = jest
    .spyOn(ClientFileProcessing.prototype, "processFiles")
    .mockRejectedValue(Error("Process files failed"))
  const clientFileProcessing: ClientFileProcessing = new ClientFileProcessing(
    client
  )
  const uploadFiles = new UploadFiles(clientFileProcessing)

  await expect(uploadFiles.generateFileDetails("1", 2)).rejects.toStrictEqual(
    Error("Process files failed")
  )
  expect(spyProcessing).toBeCalledTimes(1)

  spyProcessing.mockReset()
})

test("uploadClientFileMetadata adds client file metadata", async () => {
  const client = new GraphqlClient("test", mockKeycloak)
  const spyProcessing: SpyInstance = jest
    .spyOn(ClientFileProcessing.prototype, "processClientFileMetadata")
    .mockResolvedValue()
  const clientFileProcessing: ClientFileProcessing = new ClientFileProcessing(
    client
  )
  const uploadFiles = new UploadFiles(clientFileProcessing)

  await uploadFiles.uploadClientFileMetadata([], [])
  expect(spyProcessing).toBeCalledTimes(1)

  spyProcessing.mockReset()
})

test("uploadClientFileMetadata returns an error if uploading file metadata fails", async () => {
  const client = new GraphqlClient("test", mockKeycloak)
  const spyProcessing: SpyInstance = jest
    .spyOn(ClientFileProcessing.prototype, "processClientFileMetadata")
    .mockRejectedValue(Error("Process client file metadata failed"))
  const clientFileProcessing: ClientFileProcessing = new ClientFileProcessing(
    client
  )
  const uploadFiles = new UploadFiles(clientFileProcessing)

  await expect(
    uploadFiles.uploadClientFileMetadata([], [])
  ).rejects.toStrictEqual(Error("Process client file metadata failed"))
  expect(spyProcessing).toBeCalledTimes(1)

  spyProcessing.mockReset()
})
