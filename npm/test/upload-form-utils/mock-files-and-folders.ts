import { IFileWithPath } from "@nationalarchives/file-information"

export const mockFileList: (file: File[]) => FileList = (file: File[]) => {
  return {
    length: file.length,
    item: (index: number) => file[index],
    0: file[0],
    1: file[1]
  } as FileList
}

export const mockDataTransferItemList: (
  entry: DataTransferItem,
  itemLength: number
) => DataTransferItemList = (entry: DataTransferItem, itemLength: number) => {
  return {
    item: jest.fn(),
    [Symbol.iterator]: jest.fn(),
    add: jest.fn(),
    length: itemLength,
    clear: jest.fn(),
    0: entry,
    remove: jest.fn()
  } as DataTransferItemList
}

export const dummyFolder = {
  lastModified: 2147483647,
  name: "Mock Folder",
  size: 0,
  type: "",
  webkitRelativePath: ""
} as unknown as File

export const dummyFile = {
  lastModified: 2147483647,
  name: "Mock File",
  size: 3008,
  type: "pdf",
  webkitRelativePath: "Parent_Folder"
} as unknown as File

export const dummyIFileWithPath = {
  file: dummyFile,
  path: "Parent_Folder",
  webkitRelativePath: "Parent_Folder"
} as IFileWithPath
