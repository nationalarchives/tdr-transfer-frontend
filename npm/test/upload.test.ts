import { ClientFileProcessing } from "../src/clientprocessing"
import { UploadFiles } from "../src/upload"
import { GraphqlClient } from "../src/graphql"
import { KeycloakInstance } from "keycloak-js"

jest.mock("../src/clientprocessing")

class ClientFileProcessingSuccess {
  processFiles: (
    consignmentId: number,
    numberOfFiles: number
  ) => Promise<number[]> = async (
    consignmentId: number,
    numberOfFiles: number
  ) => {
    const data: number[] = [1, 2]
    return data
  }
  processClientFileMetadata: (
    files: File[],
    fileIds: number[]
  ) => Promise<void> = async (files: File[], fileIds: number[]) => {
    return Promise.resolve()
  }
}

class ClientFileProcessingError {
  processFiles: (
    consignmentId: number,
    numberOfFiles: number
  ) => Promise<number[]> = async (
    consignmentId: number,
    numberOfFiles: number
  ) => {
    return Promise.reject(Error("Process files failed"))
  }
  processClientFileMetadata: (
    files: File[],
    fileIds: number[]
  ) => Promise<void> = async (files: File[], fileIds: number[]) => {
    return Promise.reject(Error("Process client file metadata failed"))
  }
}

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

function setUpUpload(): UploadFiles {
  const client = new GraphqlClient("test", mockKeycloak)
  const clientFileProcessing = new ClientFileProcessing(client)

  return new UploadFiles(clientFileProcessing)
}

const mockSuccess: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingSuccess()
  })
}

const mockFailure: () => void = () => {
  const mock = ClientFileProcessing as jest.Mock
  mock.mockImplementation(() => {
    return new ClientFileProcessingError()
  })
}

test("retrieveConsignmentId returns consignment id from windows location", () => {
  const url = "https://test.gov.uk/consignment/1/upload"
  Object.defineProperty(window, "location", {
    value: {
      href: url,
      pathname: "/consignment/1/upload"
    }
  })

  const uploadFiles = setUpUpload()

  expect(uploadFiles.retrieveConsignmentId()).toBe(1)
})

test("generateFileDetails adds Files to database and returns file ids", async () => {
  mockSuccess()
  const uploadFiles = setUpUpload()

  const result = await uploadFiles.generateFileDetails(1, 2)

  expect(result).toHaveLength(2)
  expect(result[0]).toBe(1)
  expect(result[1]).toBe(2)
})

test("generateFileDetails returns an error if adding Files fails", async () => {
  mockFailure()
  const uploadFiles = setUpUpload()

  await expect(uploadFiles.generateFileDetails(1, 2)).rejects.toStrictEqual(
    Error("Process files failed")
  )
})

test("uploadClientFileMetadata adds client file metadata to database", async () => {
  mockSuccess()
  const uploadFiles = setUpUpload()

  await expect(
    uploadFiles.uploadClientFileMetadata([], [])
  ).resolves.not.toThrow()
})

test("uploadClientFileMetadata returns an error if uploading file metadata fails", async () => {
  mockFailure()
  const uploadFiles = setUpUpload()

  await expect(
    uploadFiles.uploadClientFileMetadata([], [])
  ).rejects.toStrictEqual(Error("Process client file metadata failed"))
})
