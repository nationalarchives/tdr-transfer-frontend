import { ClientFileMetadataUpload } from "../clientfilemetadataupload"
import { ClientFileExtractMetadata } from "../clientfileextractmetadata"
import {
  IFileMetadata,
  IProgressInformation,
  TdrFile,
  TProgressFunction
} from "@nationalarchives/file-information"

export class ClientFileProcessing {
  clientFileMetadataUpload: ClientFileMetadataUpload
  clientFileExtractMetadata: ClientFileExtractMetadata

  constructor(clientFileMetadataUpload: ClientFileMetadataUpload) {
    this.clientFileMetadataUpload = clientFileMetadataUpload
    this.clientFileExtractMetadata = new ClientFileExtractMetadata()
  }

  async processClientFiles(
    consignmentId: string,
    files: TdrFile[]
  ): Promise<void> {
    try {
      const fileIds: string[] = await this.clientFileMetadataUpload.saveFileInformation(
        consignmentId,
        files.length
      )
      const metadata: IFileMetadata[] = await this.clientFileExtractMetadata.extract(
        files,
        //Temporary function until s3 upload in place
        function(progressInformation: IProgressInformation) {
          console.log(
            "Percent of metadata extracted: " +
              progressInformation.percentageProcessed
          )
        }
      )
      await this.clientFileMetadataUpload.saveClientFileMetadata(
        fileIds,
        metadata
      )
    } catch (e) {
      throw Error("Processing client files failed: " + e.message)
    }
  }
}
