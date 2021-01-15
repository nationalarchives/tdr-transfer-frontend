import {
  extractFileMetadata,
  IFileMetadata,
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"

export class ClientFileExtractMetadata {
  async extract(
    files: IFileWithPath[],
    callBack: TProgressFunction
  ): Promise<IFileMetadata[]> {
    try {
      return await extractFileMetadata(files, callBack)
    } catch (e) {
      throw Error("Client file metadata extraction failed: " + e.message)
    }
  }
}
