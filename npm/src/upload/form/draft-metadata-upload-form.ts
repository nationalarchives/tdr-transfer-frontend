import {IFileWithPath} from "@nationalarchives/file-information"
import {rejectUserItemSelection} from "./display-warning-message"
import {IEntryWithPath, isFile} from "./get-files-from-drag-event"


import {isError} from "../../errorhandling"
import {addFileSelectionSuccessMessage} from "./update-and-display-success-message";
import {DraftMetadataFileUpload} from "../../draftmetadatafileupload";


interface FileWithRelativePath extends File {
    webkitRelativePath: string
}



export class DraftMetaDataUploadForm {
    formElement: HTMLFormElement
    itemRetriever: HTMLInputElement
    selectedFiles: IEntryWithPath[]
    draftMetadataFileUpload: DraftMetadataFileUpload


    constructor(
        formElement: HTMLFormElement,
        itemRetriever: HTMLInputElement,
        draftMetadataFileUpload: DraftMetadataFileUpload
    ) {
        this.formElement = formElement
        this.itemRetriever = itemRetriever
        this.selectedFiles = []
        this.draftMetadataFileUpload = draftMetadataFileUpload
    }

    consignmentId: () => string | Error = () => {
        const value: string | null = this.formElement.getAttribute(
            "data-consignment-id"
        )

        if (!value) {
            return Error("No consignment provided")
        }
        return value
    }


    handleDraftMetadataItems: () => any = async () => {
        const form: HTMLFormElement | null = this.formElement
        this.selectedFiles = this.convertFilesToIfilesWithPath(form!.files!.files!)
    }

    addFolderListener() {
        this.itemRetriever.addEventListener("change", this.handleSelectedItems)
    }

    handleSelectedItems: () => any = async () => {
        const form: HTMLFormElement | null = this.formElement
        debugger
        this.selectedFiles = this.convertFilesToIfilesWithPath(form!.files!.files!)

        const fileWithPath = this.selectedFiles[0]
        if (isFile(fileWithPath)) {
            const fileName = fileWithPath.file.name
            const checkOrError = this.checkForCorrectDraftMetadataFileExtension(fileName)
            if (!isError(checkOrError)) {
                addFileSelectionSuccessMessage(fileName)
            } else {
                return checkOrError
            }
        }
    }




    includeTopLevelFolder: () => boolean = () => {
        return this.formElement.includeTopLevelFolder?.checked
    }

    handleFormSubmission: (ev: Event) => Promise<void | Error> = async (
        ev: Event
    ) => {
        ev.preventDefault()
        const itemSelected: IEntryWithPath = this.selectedFiles[0]
        const fileWithPath = this.selectedFiles[0] as IFileWithPath
        debugger;
        if (itemSelected) {
            this.formElement.addEventListener("submit", (ev) => ev.preventDefault()) // adding new event listener, in order to prevent default submit button behaviour
            this.disableSubmitButtonAndDropzone()

            const consignmentIdOrError = this.consignmentId()
            if (!isError(consignmentIdOrError)) {
                this.draftMetadataFileUpload.saveDraftMetadataFile(consignmentIdOrError, fileWithPath).then(
                    //Send a 301

                ).catch()

            } else {
                return consignmentIdOrError
            }
        } else {
            this.addSubmitListener() // Add submit listener back as we've set it to be removed after one form submission
            return rejectUserItemSelection(
                this.warningMessages?.submissionWithoutSelectionMessage,
                this.warningMessages,
                this.successAndRemovalMessageContainer,
                "A submission was made without an item being selected"
            )
        }
    }

    addSubmitListener() {
        this.formElement.addEventListener("submit", this.handleFormSubmission, {
            once: true
        })
    }

    readonly warningMessages: {
        [s: string]: HTMLElement | null
    } = {
        incorrectFileExtensionMessage: document.querySelector(
            "#incorrect-file-extension"
        ),
        incorrectItemSelectedMessage: document.querySelector(
            "#item-selection-failure"
        ),
        emptyFolderSelectedMessage: document.querySelector(
            "#empty-folder-selection-failure"
        ),
        multipleItemSelectedMessage: document.querySelector(
            "#multiple-selection-failure"
        ),
        multipleFolderSelectedMessage: document.querySelector(
            "#multiple-folder-selection-failure"
        ),
        submissionWithoutSelectionMessage: document.querySelector(
            "#nothing-selected-submission-message"
        ),
        removedSelectionMessage: document.querySelector(
            "#removed-selection-container"
        )
    }

    readonly successAndRemovalMessageContainer: HTMLElement | null =
        document.querySelector("#success-and-removal-message-container")


    private disableSubmitButtonAndDropzone() {
        const submitButton = document.querySelector("#start-upload-button")
        submitButton?.setAttribute("disabled", "true")

        const hiddenInputButton = document.querySelector("#file-selection")
        hiddenInputButton?.setAttribute("disabled", "true")

    }

    private convertFilesToIfilesWithPath(files: File[]): IFileWithPath[] {
        return [...files].map((file) => ({
            file,
            path: (file as FileWithRelativePath).webkitRelativePath
        }))
    }



    private checkForCorrectDraftMetadataFileExtension(fileName: string) {
        const judgmentFileExtensionsAllowList = [".csv"]

        if (fileName) {
            const indexOfLastDot = fileName.lastIndexOf(".")
            const fileExtension = fileName.slice(indexOfLastDot)
            if (!judgmentFileExtensionsAllowList.includes(fileExtension)) {
                return rejectUserItemSelection(
                    this.warningMessages?.incorrectFileExtensionMessage,
                    this.warningMessages,
                    this.successAndRemovalMessageContainer,
                    "Draft metadata must be supplied in CSV format"
                )
            }
        } else {
            return Error("The file does not have a file name!")
        }
    }
}
