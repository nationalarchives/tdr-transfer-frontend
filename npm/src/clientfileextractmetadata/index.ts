import {
  extractFileMetadata,
  IFileMetadata,
  TProgressFunction
} from "@nationalarchives/file-information"
import { IFileEntry } from "../upload/form/file-types"
import { getErrorMessage } from "../errorhandling"

export class ClientFileExtractMetadata {
  async extract(
    files: IFileEntry[],
    callBack: TProgressFunction
  ): Promise<IFileMetadata[] | Error> {
    try {
      return await extractFileMetadata(files, callBack)
    } catch (e) {
      return Error(
        "Client file metadata extraction failed: " + getErrorMessage(e)
      )
    }
  }
}
