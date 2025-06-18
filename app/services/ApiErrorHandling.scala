package services

//import com.nimbusds.oauth2.sdk.token.BearerAccessToken
//import configuration.GraphQLBackend._
//import errors.{AuthorisationException, GraphQlException}
//import sangria.ast.Document
//import slick.jdbc.JdbcBackend.Database
//import uk.gov.nationalarchives.tdr.GraphQLClient
//import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
//
//import scala.concurrent.{ExecutionContext, Future}
//import slick.jdbc.PostgresProfile.api._
//import uk.gov.nationalarchives.Tables._

object ApiErrorHandling {



//  val db = Database.forConfig("consignmentapidb")

//  def getSeries(tdrBodyCode: String): Future[Seq[SeriesRow]] = {
//    val query = for {
//      (series, _) <- Series.join(Body).on(_.bodyid === _.bodyid).filter(_._2.tdrcode === tdrBodyCode)
//    } yield series
//    db.run(query.result)
//  }
}
