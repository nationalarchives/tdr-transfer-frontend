import { bytes_to_hex, Sha256 } from "asmcrypto.js"

import {
  IFileMetadata,
  IFileWithPath,
  IProgressInformation,
  TFileMetadata,
  TProgressFunction,
  TSliceToArray,
  TTotalChunks
} from "./types"

export * from "./types"

export const extractFileMetadata: TFileMetadata = async (
  tdrFiles: IFileWithPath[],
  progressFunction:
    | ((progressInformation: IProgressInformation) => void)
    | undefined,
  chunkSizeBytes = 100000000
) => {
  let processedChunks: number = 0
  let processedFiles: number = 0
  const totalChunks: number = getTotalChunks(tdrFiles, chunkSizeBytes)

  const updateProgress: (
    processedChunks: number,
    processedFiles: number
  ) => void = (processedChunks: number, processedFiles: number) => {
    if (progressFunction) {
      progressFunction({
        percentageProcessed: Math.round((processedChunks / totalChunks) * 100),
        totalFiles: tdrFiles.length,
        processedFiles
      })
    }
  }

  const metadataFromTdrFiles: IFileMetadata[] = []
  for (const tdrFile of tdrFiles) {
    const { file, path }: IFileWithPath = tdrFile
    const chunkCount = Math.ceil((file.size ? file.size : 1) / chunkSizeBytes)
    const sha256 = new Sha256()
    for (let i = 0; i < chunkCount; i += 1) {
      const start = i * chunkSizeBytes
      const end = start + chunkSizeBytes
      const slice: Blob = file.slice(start, end)
      sha256.process(await sliceToUintArray(slice))
      processedChunks += 1
      updateProgress(processedChunks, processedFiles)
    }
    const { size, lastModified } = file

    const result: Uint8Array = sha256.finish().result!
    const checksum = bytes_to_hex(result).trim()
    metadataFromTdrFiles.push({
      checksum,
      size,
      lastModified: new Date(lastModified),
      path,
      file
    })
    processedFiles += 1
  }

  return metadataFromTdrFiles
}

export const getTotalChunks: TTotalChunks = (
  tdrFiles: IFileWithPath[],
  chunkSizeBytes: number
) => {
  return tdrFiles.reduce(
    (filesSizeTotal, file) =>
      filesSizeTotal +
      Math.ceil((file.file.size ? file.file.size : 1) / chunkSizeBytes),
    0
  )
}

const sliceToUintArray: TSliceToArray = async (blob) => {
  const fileReader = new FileReader()
  fileReader.readAsArrayBuffer(blob)
  return await new Promise((resolve) => {
    fileReader.onload = () => {
      const { result } = fileReader
      if (result instanceof ArrayBuffer) {
        resolve(new Uint8Array(result))
      }
    }
  })
}
