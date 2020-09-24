import "@testing-library/jest-dom"
import { TdrFile } from "@nationalarchives/file-information"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { UploadFiles } from "../src/upload"
import { InputElement, UploadForm } from "../src/upload/upload-form"
import { mockKeycloakInstance } from "./utils"

const mockFormHTML = `
  <form id="file-upload-form" data-consignment-id="95d81f57-b8a8-44aa-883b-d66a3037511b">
    <div class="govuk-form-group">
        <div class="drag-and-drop">
            <div class="govuk-summary-list">
                <div class="govuk-summary-list__row hide">
                    <dd class="govuk-summary-list__value">
                      <svg class="green-tick-mark" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg">
                        <path d="M25,6.2L8.7,23.2L0,14.1l4-4.2l4.7,4.9L21,2L25,6.2z"></path>
                      </svg>
                      The folder
                      "<span id="folder-name"></span>"
                      (containing
                      <span id="folder-size"></span>
                      files) has been selected
                    </dd>
                </div>
            </div>
            <div>
                <div class="govuk-form-group">
                    <div class="drag-and-drop__dropzone">
                        <input type="file" id="file-selection" name="files" class="govuk-file-upload drag-and-drop__input" webkitdirectory>
                        <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single folder here or</p>
                        <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">Choose folder</label>
                    </div>
                </div>
            </div>
        </div>
    </div>
  </form>`

const mockGoToNextPage = jest.fn()

const triggerInputEvent: (element: HTMLInputElement) => void = (
  element: HTMLInputElement
) => {
  const event = new CustomEvent("change")
  element.dispatchEvent(event)
}

test("folder retriever updates the page with correct folder information if there are 1 or more files", () => {
  document.body.innerHTML = mockFormHTML

  const dummyFile = {
    webkitRelativePath: "Parent_Folder/testfile"
  } as TdrFile

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const folderRetriever: HTMLInputElement | null = document.querySelector(
    "#file-selection"
  )

  uploadForm!.files = { files: [dummyFile] }
  const uploadFiles = setUpUploadFiles()
  uploadFiles.upload()

  triggerInputEvent(folderRetriever!)

  const folderSummaryElement: HTMLElement | null = document.querySelector(
    ".govuk-summary-list__row"
  )
  const folderNameElement: HTMLElement | null = document.querySelector(
    "#folder-name"
  )
  const folderSizeElement: HTMLElement | null = document.querySelector(
    "#folder-size"
  )

  expect(folderSummaryElement!).not.toHaveClass("hide")
  expect(folderNameElement!.textContent).toStrictEqual("Parent_Folder")
  expect(Number(folderSizeElement!.textContent)).toStrictEqual(1)
})

function setUpUploadFiles(): UploadFiles {
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  return new UploadFiles(uploadMetadata, "identityId", "test", mockGoToNextPage)
}
