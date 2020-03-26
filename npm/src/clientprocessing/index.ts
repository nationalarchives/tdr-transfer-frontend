import { GraphqlClient } from "../graphql"

import {
  AddFilesMutation,
  AddFiles,
  AddFilesMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult } from "apollo-boost"

export const processFiles: (
  graphqlClient: GraphqlClient,
  consignmentId: number,
  numberOfFiles: number
) => Promise<number[]> = async (
  graphqlClient: GraphqlClient,
  consignmentId: number,
  numberOfFiles: number
) => {
  const variables: AddFilesMutationVariables = {
    addFilesInput: { consignmentId: 5, numberOfFiles: numberOfFiles }
  }
  const result: FetchResult<AddFilesMutation> = await graphqlClient.mutation(
    AddFiles,
    variables
  )

  return result.data!.addFiles.fileIds
}
