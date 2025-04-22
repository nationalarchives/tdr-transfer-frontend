import {
  IFileMetadata,
  IFileWithPath
} from "@nationalarchives/file-information"

import {
  AddFileAndMetadataInput,
  ClientSideMetadataInput,
  FileMatches
} from "@nationalarchives/tdr-generated-graphql"

import { ITdrFileWithPath } from "../s3upload"
import { FileUploadInfo } from "../upload/form/upload-form"
import { isError } from "../errorhandling"

export class ClientFileMetadataUpload {
  async startUpload(uploadFilesInfo: FileUploadInfo): Promise<void | Error> {
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!
    const result: Response | Error = await fetch("/start-upload", {
      credentials: "include",
      method: "POST",
      body: JSON.stringify(uploadFilesInfo),
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Start upload failed: ${result.statusText}`)
    }
  }

  async saveClientFileMetadata(
    consignmentId: string,
    allFileMetadata: IFileMetadata[],
    emptyFolders: string[]
  ): Promise<ITdrFileWithPath[] | Error> {
    const { metadataInputs, matchFileMap } =
      this.createMetadataInputsAndFileMap(allFileMetadata)

    const allFiles: ITdrFileWithPath[] = []
    const csrfInput: HTMLInputElement = document.querySelector(
      "input[name='csrfToken']"
    )!

    const input: AddFileAndMetadataInput = {
      consignmentId,
      metadataInput: metadataInputs,
      emptyDirectories: emptyFolders
    }
    const result: Response | Error = await fetch("/save-metadata", {
      credentials: "include",
      method: "POST",
      body: JSON.stringify(input),
      headers: {
        "Content-Type": "application/json",
        "Csrf-Token": csrfInput.value,
        "X-Requested-With": "XMLHttpRequest"
      }
    }).catch((err) => {
      return Error(err)
    })

    if (isError(result)) {
      return result
    } else if (result.status != 200) {
      return Error(`Add client file metadata failed: ${result.statusText}`)
    } else {
      const matches = (await result.json()) as Array<FileMatches>
      matches.forEach((f) => {
        const fileId: string = f.fileId
        const file: IFileWithPath | undefined = matchFileMap.get(f.matchId)
        if (file) {
          allFiles.push({ fileId, fileWithPath: file })
        } else {
          return Error(`Invalid match id ${f.matchId} for file ${fileId}`)
        }
      })
    }
    return allFiles
  }

  createMetadataInputsAndFileMap(allFileMetadata: IFileMetadata[]): {
    metadataInputs: ClientSideMetadataInput[];
    matchFileMap: Map<String, IFileWithPath>
  } {
    return allFileMetadata.reduce(
      (result, metadata: IFileMetadata, matchId) => {
        const { checksum, path, lastModified, file, size } = metadata
        //Files uploaded with 'drag and files' have '/'  prepended, those uploaded with 'browse' don't
        //Ensure file paths stored in database are consistent
        const pathWithoutSlash = path.startsWith("/") ? path.substring(1) : path
        const filePath = pathWithoutSlash ? pathWithoutSlash : file.name
        result.matchFileMap.set(matchId.toString(), { file, path: filePath })
        const metadataInput: ClientSideMetadataInput = {
          originalPath: filePath,
          checksum,
          lastModified: lastModified.getTime(),
          fileSize: size,
          matchId: matchId.toString()
        }
        result.metadataInputs.push(metadataInput)

        return result
      },
      {
        metadataInputs: <ClientSideMetadataInput[]>[],
        matchFileMap: new Map<String, IFileWithPath>()
      }
    )
  }
}
