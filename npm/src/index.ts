import { GraphqlClient } from "./graphql"

// All queries can be imported from this package. You add them in the same way you do as for the scala classes.
import {
  GetSeries,
  GetSeriesQueryVariables,
  GetSeriesQuery
} from "@nationalarchives/tdr-generated-graphql"
import { ApolloQueryResult } from "apollo-boost"

// Needed to keep typescript happy
declare var TDR_API_URL: string

// Pass this class instance to any class or function that needs it
// This means we don't create the graphql client each time we need a query or mutation
const graphqlClient = new GraphqlClient(TDR_API_URL)

// Then use the imported types like this
const getSeriesDescription: (
  graphqlClient: GraphqlClient
) => Promise<string> = async graphqlClient => {
  const variables = { body: "Test" }
  const result: ApolloQueryResult<GetSeriesQuery> = await graphqlClient.query(
    GetSeries,
    variables
  )
  return result.data!.getSeries[0].description!
}
