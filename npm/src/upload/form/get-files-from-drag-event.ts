import { IFileWithPath } from "@nationalarchives/file-information"
import { IEntryWithPath, withTimeout } from "./file-types"

const READ_ENTRIES_TIMEOUT_MS = 5000

export const getAllFiles: (
  entry: IWebkitEntry | null,
  fileInfoInput: IEntryWithPath[]
) => Promise<IEntryWithPath[]> = async (entry, fileInfoInput) => {
  if (!entry) {
    return fileInfoInput
  }

  let entries: IWebkitEntry[] | null
  try {
    const reader: IReader = entry.createReader()
    entries = await getEntriesFromReader(reader, entry.fullPath)
  } catch {
    fileInfoInput.push({ path: entry.fullPath, unreadable: true })
    return fileInfoInput
  }

  if (entries === null) {
    fileInfoInput.push({ path: entry.fullPath, unreadable: true })
    return fileInfoInput
  }

  if (entry.isDirectory && entries.length === 0) {
    fileInfoInput.push({ path: entry.fullPath })
  }
  for (const entry of entries) {
    if (entry.isDirectory) {
      await getAllFiles(entry, fileInfoInput)
    } else {
      const file: IFileWithPath | null = await getFileFromEntry(entry)
      if (file) {
        fileInfoInput.push(file)
      } else {
        fileInfoInput.push({ path: entry.fullPath, unreadable: true })
      }
    }
  }
  return fileInfoInput
}

const getEntriesFromReader: (
  reader: IReader,
  dirPath: string
) => Promise<IWebkitEntry[] | null> = async (reader, dirPath) => {
  let allEntries: IWebkitEntry[] = []
  try {
    let nextBatch = await withTimeout(
      getEntryBatch(reader),
      READ_ENTRIES_TIMEOUT_MS,
      `readEntries timed out for: ${dirPath}`
    )

    while (nextBatch.length > 0) {
      allEntries = allEntries.concat(nextBatch)
      nextBatch = await withTimeout(
        getEntryBatch(reader),
        READ_ENTRIES_TIMEOUT_MS,
        `readEntries timed out for: ${dirPath}`
      )
    }
  } catch {
    return null
  }

  return allEntries
}

const getEntryBatch: (reader: IReader) => Promise<IWebkitEntry[]> = (
  reader
) => {
  return new Promise<IWebkitEntry[]>((resolve, reject) => {
    reader.readEntries(
      (entries) => resolve(entries),
      (err) => reject(err)
    )
  })
}

const getFileFromEntry: (
  entry: IWebkitEntry
) => Promise<IFileWithPath | null> = (entry) => {
  return withTimeout(
    new Promise<IFileWithPath>((resolve, reject) => {
      entry.file(
        (file) =>
          resolve({
            file,
            path: entry.fullPath
          }),
        (err) => reject(err)
      )
    }),
    READ_ENTRIES_TIMEOUT_MS,
    `entry.file() timed out for: ${entry.fullPath}`
  ).catch((): null => null)
}

export interface IReader {
  readEntries: (
    successCallback: (entry: IWebkitEntry[]) => void,
    errorCallback?: (err: DOMException) => void
  ) => void
}

export interface IWebkitEntry extends DataTransferItem {
  createReader: () => IReader
  isFile: boolean
  isDirectory: boolean
  fullPath: string
  name?: string
  file: (
    success: (file: File) => void,
    error?: (err: DOMException) => void
  ) => void
}
