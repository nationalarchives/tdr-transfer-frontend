import { IFileWithPath } from "@nationalarchives/file-information"

export const getAllFiles: (
  entry: IWebkitEntry,
  fileInfoInput: IFileWithPath[]
) => Promise<IFileWithPath[]> = async (entry, fileInfoInput) => {
  const reader: IReader = entry.createReader()
  const entries: IWebkitEntry[] = await getEntriesFromReader(reader)
  for (const entry of entries) {
    if (entry.isDirectory) {
      await getAllFiles(entry, fileInfoInput)
    } else {
      const file: IFileWithPath = await getFileFromEntry(entry)
      fileInfoInput.push(file)
    }
  }
  return fileInfoInput
}

const getEntriesFromReader: (
  reader: IReader
) => Promise<IWebkitEntry[]> = async (reader) => {
  let allEntries: IWebkitEntry[] = []

  let nextBatch = await getEntryBatch(reader)

  while (nextBatch.length > 0) {
    allEntries = allEntries.concat(nextBatch)
    nextBatch = await getEntryBatch(reader)
  }

  return allEntries
}

const getEntryBatch: (reader: IReader) => Promise<IWebkitEntry[]> = (
  reader
) => {
  return new Promise<IWebkitEntry[]>((resolve) => {
    reader.readEntries((entries) => resolve(entries))
  })
}

const getFileFromEntry: (entry: IWebkitEntry) => Promise<IFileWithPath> = (
  entry
) => {
  return new Promise<IFileWithPath>((resolve) => {
    entry.file((file) =>
      resolve({
        file,
        path: entry.fullPath
      })
    )
  })
}

export interface IReader {
  readEntries: (callbackFunction: (entry: IWebkitEntry[]) => void) => void
}

export interface IWebkitEntry extends DataTransferItem {
  createReader: () => IReader
  isFile: boolean
  isDirectory: boolean
  fullPath: string
  name?: string
  file: (success: (file: File) => void) => void
}
