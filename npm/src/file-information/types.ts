export interface IFileMetadata {
  checksum: string
  size: number
  path: string
  lastModified: Date
  file: File
}

export interface IProgressInformation {
  percentageProcessed: number
  totalFiles: number
  processedFiles: number
}

export type TFileMetadata = (
  files: IFileWithPath[],
  progressFunction?: TProgressFunction | undefined,
  chunkSize?: number
) => Promise<IFileMetadata[]>

export type TChunkProgressFunction = () => void

export type TGenerateMetadata = (
  file: File,
  chunkSizeBytes: number,
  chunkProgressFunction: TChunkProgressFunction
) => Promise<string>

export type TTotalChunks = (files: IFileWithPath[], chunkSize: number) => number

export type TSliceToArray = (blob: Blob) => Promise<Uint8Array>

export type TProgressFunction = (
  progressInformation: IProgressInformation
) => void

export interface IFileWithPath {
  file: File
  path: string
}
