import {
  ApolloClient,
  InMemoryCache,
  ApolloQueryResult,
  DocumentNode,
  NormalizedCacheObject,
  QueryOptions,
  MutationOptions,
  FetchResult,
  createHttpLink
} from "@apollo/client/core"
import "unfetch/polyfill"
import Keycloak, {KeycloakInstance} from "keycloak-js"
import {refreshOrReturnToken} from "../auth"
import {attemptP, chain, encaseP, FutureInstance, map} from "fluture";

type CommonQueryOptions<T> = Omit<T, "query">

const tokenMinValidityInSecs: number = 30

export class GraphqlClient {
  client: ApolloClient<NormalizedCacheObject>
  keycloak: KeycloakInstance

  constructor(uri: string, keycloak: Keycloak.KeycloakInstance) {
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
  ) => FutureInstance<unknown, CommonQueryOptions<QueryOptions<V> | MutationOptions<D, V>>> =
    <D, V>(variables: V) => {
      return attemptP(() => refreshOrReturnToken(this.keycloak, tokenMinValidityInSecs))
        .pipe(map(token => {
          return {
            variables,
            context: {
              headers: {
                Authorization: `Bearer ${token}`
              }
            }
          }
        }))
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
  ) => FutureInstance<unknown, FetchResult<D>> = <D, V>(
    mutation: DocumentNode,
    variables: V
  ) => {
    return this.getOptions<D, V>(variables)
      .pipe(map(opts => {
        const options: MutationOptions<D, V> = {
          mutation,
          ...opts,
          fetchPolicy: "no-cache"
        }
        return options
      }))
      .pipe(chain((options => {
        return encaseP(this.client.mutate)<D, V>(options)
      })))
  }
}
