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

    addDraftMetadataItemListener() {
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
                const consignmentIdOrError = this.consignmentId()
                if (!isError(consignmentIdOrError)) {
                    const me :Promise< String | Error> = this.draftMetadataFileUpload.saveDraftMetadataFile(consignmentIdOrError, fileWithPath)
                    //this.disableSubmitButtonAndDropzone()
                }
            } else {

            }
        }
    }




    includeTopLevelFolder: () => boolean = () => {
        return this.formElement.includeTopLevelFolder?.checked
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
        const submitButton = document.querySelector("#to-draft-metadata-checks")
        submitButton?.setAttribute("disabled", "false")

        // const hiddenInputButton = document.querySelector("#file-selection")
        // hiddenInputButton?.setAttribute("disabled", "true")

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
