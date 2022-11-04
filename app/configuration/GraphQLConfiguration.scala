package configuration

import javax.inject.Inject
import play.api.Configuration
import uk.gov.nationalarchives.tdr.GraphQLService

class GraphQLConfiguration @Inject() (configuration: Configuration) extends GraphQLService(configuration.get[String]("consignmentapi.url"))
