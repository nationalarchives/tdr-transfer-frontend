package services

import configuration.GraphQLConfiguration

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  def getConsignmentMetadata() = {

  }
}
