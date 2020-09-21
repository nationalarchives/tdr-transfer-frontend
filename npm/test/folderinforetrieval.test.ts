import { TdrFile } from "@nationalarchives/file-information"
import { String } from "aws-sdk/clients/acm"
import { UploadForm } from "../src/upload/upload-form"

class MockHTMLForm {
  html: String = `
  <form id="file-upload-form" data-consignment-id="@consignmentId">
    <div class="govuk-form-group">
        <div class="drag-and-drop">
            <div class="govuk-summary-list">
                <div class="govuk-summary-list__row hide">
                    <dd class="govuk-summary-list__value">
                        @greenTickMark()
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
                        <p class="govuk-body drag-and-drop__hint-text">@Messages("upload.dragAndDropHintText")</p>
                        <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">@Messages("upload.chooseFolderLink")</label>
                    </div>
                </div>
            </div>
        </div>
    </div>
  </form>`

  constructor() {}
}

const triggerInputEvent: (element: HTMLInputElement) => void = (
  element: HTMLInputElement
) => {
  const event = document.createEvent("Event")
  event.initEvent("change", true, true)
  element.dispatchEvent(event)
}

const mockTriggerInputEvent: (form: HTMLFormElement) => any = (
  form: HTMLFormElement
) => {
  const target: HTMLFormElement | null = form
  const files: TdrFile[] = target!.files!.files!
  if (files === null || files.length === 0) {
    throw Error("No files selected")
  }
  return files
}

test("folder retriever updates the page with correct folder information if there are 1 or more files", () => {
  const mockForm = new MockHTMLForm()
  document.body.innerHTML = mockForm.html

  const dummyFile = {
    webkitRelativePath: "Parent_Folder/testfile"
  } as TdrFile

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const folderRetriever: HTMLInputElement | null = document.querySelector(
    "#file-selection"
  )

  if (uploadForm && folderRetriever) {
    uploadForm.files = { files: [dummyFile] }
    const form = new UploadForm(uploadForm, folderRetriever)
    form.addFolderListener()
    triggerInputEvent(folderRetriever)

    const folderNameElement: HTMLElement | null = document.querySelector(
      "#folder-name"
    )
    const folderSizeElement: HTMLElement | null = document.querySelector(
      "#folder-size"
    )

    if (folderNameElement && folderSizeElement) {
      expect(folderNameElement.textContent).toStrictEqual("Parent_Folder")
      expect(Number(folderSizeElement.textContent)).toStrictEqual(1)
    } else {
      Error("Either the folder name or size element is missing from the page")
    }
  } else {
    Error(
      "Either the form is missing from the page or the folder input is missing from the form"
    )
  }
})

test("folder retriever does not update the page with correct folder information if there are no files", () => {
  const mockForm = new MockHTMLForm()
  document.body.innerHTML = mockForm.html

  const dummyFile = {
    webkitRelativePath: "Parent_Folder/testfile"
  } as TdrFile

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )
  const folderRetriever: HTMLInputElement | null = document.querySelector(
    "#file-selection"
  )

  if (uploadForm && folderRetriever) {
    uploadForm.files = { files: [] }
    const form = new UploadForm(uploadForm, folderRetriever)
    form.addFolderListener()
    expect(() => {
      mockTriggerInputEvent(uploadForm)
    }).toThrow("No files selected")

    const folderNameElement: HTMLElement | null = document.querySelector(
      "#folder-name"
    )
    const folderSizeElement: HTMLElement | null = document.querySelector(
      "#folder-size"
    )
    if (folderNameElement && folderSizeElement) {
      expect(folderNameElement.textContent).toStrictEqual("")
      expect(Number(folderSizeElement.textContent)).toStrictEqual(0)
    }
  }
})
