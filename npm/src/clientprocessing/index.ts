import { GraphqlClient } from "../graphql"

import {
  AddFilesMutation,
  AddFiles,
  AddFilesMutationVariables
} from "@nationalarchives/tdr-generated-graphql"

import { FetchResult, ApolloQueryResult } from "apollo-boost"

export const processFiles: (
  graphqlClient: GraphqlClient,
  consignmentId: number,
  numberOfFiles: number
) => Promise<number[]> = async (
  graphqlClient: GraphqlClient,
  consignmentId: number,
  numberOfFiles: number
) => {
  console.log("Consignment Id: " + consignmentId)
  console.log("Number of files: " + numberOfFiles)

  const variables: AddFilesMutationVariables = {
    addFilesInput: {
      consignmentId: consignmentId,
      numberOfFiles: numberOfFiles
    }
  }

  const result: FetchResult<AddFilesMutation> = await graphqlClient.mutation(
    AddFiles,
    variables
  )

  return result.data?.addFiles.fileIds!
}
