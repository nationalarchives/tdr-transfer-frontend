import {
  extractFileMetadata,
  IFileMetadata,
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import {getErrorMessage} from "../errorhandling";

export class ClientFileExtractMetadata {
  async extract(
    files: IFileWithPath[],
    callBack: TProgressFunction
  ): Promise<IFileMetadata[] | Error> {
    try {
      return await extractFileMetadata(files, callBack)
    } catch (e) {
      return Error("Client file metadata extraction failed: " + getErrorMessage(e))
    }
  }
}
