import { createHttpLink } from "apollo-link-http"
import {
  ApolloClient,
  InMemoryCache,
  ApolloQueryResult,
  DocumentNode,
  NormalizedCacheObject,
  QueryOptions,
  MutationOptions,
  FetchResult
} from "apollo-boost"
import fetch from "unfetch"
import { KeycloakInstance } from "keycloak-js"

type CommonQueryOptions<T> = Omit<T, "query">

export class GraphqlClient {
  client: ApolloClient<NormalizedCacheObject>
  keycloak: KeycloakInstance<"native">

  constructor(uri: string, keycloak: Keycloak.KeycloakInstance<"native">) {
    console.log("URI: " + uri)
    this.keycloak = keycloak

    const link = createHttpLink({
      uri,
      fetch
    })

    this.client = new ApolloClient({
      link,
      cache: new InMemoryCache()
    })
  }

  private getOptions: <D, V>(
    variables: V
  ) => Promise<
    CommonQueryOptions<QueryOptions<V> | MutationOptions<D, V>>
  > = async <V>(variables: V) => {
    try {
      if (this.keycloak.isTokenExpired(30)) {
        await this.keycloak.updateToken(30)
        console.log("Token refreshed")
      }
      return {
        variables,
        context: {
          headers: {
            Authorization: `Bearer ${this.keycloak.token}`
          }
        }
      }
    } catch (e) {
      console.log("In error!!!!")
      throw Error(e)
    }
  }

  query: <D, V>(
    query: DocumentNode,
    variables: V
  ) => Promise<ApolloQueryResult<D>> = async <D, V>(
    query: DocumentNode,
    variables: V
  ) => {
    const options: QueryOptions<V> = {
      query,
      ...(await this.getOptions<D, V>(variables))
    }
    const result: ApolloQueryResult<D> = await this.client.query<D, V>(options)
    return result
  }

  mutation: <D, V>(
    query: DocumentNode,
    variables: V
  ) => Promise<FetchResult<D>> = async <D, V>(
    mutation: DocumentNode,
    variables: V
  ) => {
    console.log("In mutation")

    const options: MutationOptions<D, V> = {
      mutation,
      ...(await this.getOptions<D, V>(variables))
    }
    console.log("Fetching result...")
    const result: FetchResult<D> = await this.client.mutate<D, V>(options)
    console.log("REsult fetched")
    return result
  }
}
