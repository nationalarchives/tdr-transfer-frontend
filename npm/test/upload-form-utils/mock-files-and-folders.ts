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

export const dummyIFileWithPath = {
  file: getDummyFile(),
  path: "Parent_Folder",
  webkitRelativePath: "Parent_Folder"
} as IFileWithPath
