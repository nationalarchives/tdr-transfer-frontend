import {
  extractFileMetadata,
  IFileMetadata,
  IFileWithPath,
  TProgressFunction
} from "@nationalarchives/file-information"
import {attemptP, encaseP, FutureInstance} from "fluture";

export class ClientFileExtractMetadata {
  extract(
    files: IFileWithPath[],
    callBack: TProgressFunction
  ): FutureInstance<unknown, IFileMetadata[]> {
    return attemptP(() => extractFileMetadata(files, callBack))
  }
}
