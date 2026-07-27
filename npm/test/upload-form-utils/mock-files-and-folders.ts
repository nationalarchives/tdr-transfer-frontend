import { IFileEntry, EntryKind } from "../../src/upload/form/file-types"
import {
  IFileSystemDirectoryHandle,
  IFileSystemFileHandle
} from "../../src/upload/form/get-files-from-directory-picker"

export const mockFileList: (file: File[]) => FileList = (file: File[]) => {
  return {
    length: file.length,
    item: (index: number) => file[index],
    0: file[0],
    1: file[1],
    [Symbol.iterator]: Array.prototype[Symbol.iterator]
  } as unknown as FileList
}

export const mockDataTransferItemList: (
  entry: DataTransferItem,
  itemLength: number
) => DataTransferItemList = (entry: DataTransferItem, itemLength: number) => {
  return {
    item: jest.fn(),
    add: jest.fn(),
    length: itemLength,
    clear: jest.fn(),
    0: entry,
    remove: jest.fn(),
    [Symbol.iterator]: Array.prototype[Symbol.iterator]
  } as unknown as DataTransferItemList
}

export const getDummyFolder: (folderName?: string) => File = (
  folderName = "Mock Folder"
) => {
  return {
    lastModified: 2147483647,
    name: folderName,
    size: 0,
    type: "",
    webkitRelativePath: ""
  } as unknown as File
}

export const getDummyFile: (fileName?: string, fileType?: string) => File = (
  fileName = "Mock File",
  fileType = "pdf"
) => {
  return {
    lastModified: 2147483647,
    name: fileName,
    size: 3008,
    type: fileType,
    webkitRelativePath: "Parent_Folder"
  } as unknown as File
}

export const dummyIFileWithPath: IFileEntry = {
  file: getDummyFile(),
  path: "Parent_Folder",
  kind: EntryKind.File
}

export function createMockDirectoryHandle(
  folderName: string,
  files: { name: string; file: File }[]
): IFileSystemDirectoryHandle {
  const fileHandles: [string, IFileSystemFileHandle][] = files.map(
    ({ name, file }) => [
      name,
      {
        kind: "file" as const,
        name,
        getFile: () => Promise.resolve(file)
      }
    ]
  )

  return {
    kind: "directory",
    name: folderName,
    entries: () => {
      let index = 0
      return {
        next() {
          if (index < fileHandles.length) {
            return Promise.resolve({
              value: fileHandles[index++],
              done: false
            })
          }
          return Promise.resolve({
            value: undefined,
            done: true
          })
        },
        [Symbol.asyncIterator]() {
          return this
        }
      } as AsyncIterableIterator<[string, IFileSystemFileHandle]>
    }
  }
}
