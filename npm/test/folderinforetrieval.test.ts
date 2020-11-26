import "@testing-library/jest-dom"
import { IFileWithPath } from "@nationalarchives/file-information"
import { ClientFileMetadataUpload } from "../src/clientfilemetadataupload"
import { GraphqlClient } from "../src/graphql"
import { FileUploader } from "../src/upload"
import { InputElement, UploadForm } from "../src/upload/upload-form"
import { mockKeycloakInstance } from "./utils"

document.body.innerHTML = `
<form id="file-upload-form" data-consignment-id="@consignmentId">
            <div class="govuk-form-group">
                <div class="drag-and-drop">
                    <div class="govuk-summary-list">
                        <div class="govuk-summary-list__row">
                            <dd class="govuk-summary-list__value drag-and-drop__success hide">
                                @greenTickMark()
                                The folder
                                "<span id="folder-name"></span>"
                                (containing
                                <span id="folder-size"></span>)
                                has been selected
                            </dd>
                            <dd class="govuk-summary-list__value drag-and-drop__failure hide">
                                @redWarningSign()
                                You can only drop a single folder
                            </dd>
                        </div>
                    </div>
                    <div>
                        <div class="govuk-form-group">
                            <div class="drag-and-drop__dropzone">
                                <input type="file" id="file-selection" name="files" class="govuk-file-upload drag-and-drop__input" webkitdirectory>
                                <p class="govuk-body drag-and-drop__hint-text">@Messages("upload.dragAndDropHintText")</p>
                                <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">@Messages("upload.chooseFolderLink")</label>
                            </div>
                            <div class="govuk-warning-text">
                                <span class="govuk-warning-text__icon" aria-hidden="true">!</span>
                                <strong class="govuk-warning-text__text">
                                    <span class="govuk-warning-text__assistive">Warning</span>
                                    @Messages("upload.fileExtensionWarning")
                                </strong>
                            </div>
                            <details class="govuk-details" data-module="govuk-details">
                                <summary class="govuk-details__summary">
                                    <span class="govuk-details__summary-text">
                                        @Messages("upload.fileExtensionTitle")
                                    </span>
                                </summary>
                                <div class="govuk-details__text">
                                    @Messages("upload.fileExtensionSummary")
                                </div>
                            </details>
                            <button class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                @Messages("upload.continueLink")
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </form>`

const mockGoToNextPage = jest.fn()

const triggerInputEvent: (
  element: HTMLInputElement,
  domEvent: string
) => void = (element: HTMLInputElement, domEvent: string) => {
  const event = new CustomEvent(domEvent)
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

  triggerInputEvent(folderRetriever!, "change")

  const folderRetrievalSuccessMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__success"
  )

  const folderRetrievalfailureMessage: HTMLElement | null = document.querySelector(
    ".drag-and-drop__failure"
  )
  const folderNameElement: HTMLElement | null = document.querySelector(
    "#folder-name"
  )
  const folderSizeElement: HTMLElement | null = document.querySelector(
    "#folder-size"
  )

  expect(folderRetrievalSuccessMessage!).not.toHaveClass("hide")
  expect(folderRetrievalfailureMessage!).toHaveClass("hide")
  expect(folderNameElement!.textContent).toStrictEqual("Parent_Folder")
  expect(folderSizeElement!.textContent).toStrictEqual("1 file")
})

function setUpUploadFiles(): FileUploader {
  const client = new GraphqlClient("https://test.im", mockKeycloakInstance)
  const uploadMetadata = new ClientFileMetadataUpload(client)
  return new FileUploader(uploadMetadata, "identityId", "test", mockGoToNextPage)
}

;("dropzone updates the page with an error if a non-folder has been dropped")
