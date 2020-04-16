import {
  extractFileMetadata,
  IFileMetadata,
  TdrFile
} from "@nationalarchives/file-information"

export class ClientFileExtractMetadata {
  async extract(files: TdrFile[]): Promise<IFileMetadata[]> {
    try {
      return await extractFileMetadata(files)
    } catch (e) {
      throw Error("Client file metadata extraction failed: " + e.message)
    }
  }
}
