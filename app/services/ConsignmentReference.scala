package services

import uk.gov.nationalarchives.oci.Alphabet.Alphabet
import uk.gov.nationalarchives.oci._

object ConsignmentReference {
  val alphabet: Alphabet = Alphabet.loadAlphabet(Right(IncludedAlphabet.GCRb25)) match {
    case Left(error)     => throw new Exception(s"Error loading the GCRb25 encoding alphabet", error)
    case Right(alphabet) => alphabet
  }
  val baseNumber = 25

  def createConsignmentReference(transferStartYear: Int, consignmentSequenceId: Long): String = {
    val alphabetIndices = BaseCoder.encode(consignmentSequenceId, baseNumber)
    val encoded = Alphabet.toString(alphabet, alphabetIndices)

    s"TDR-$transferStartYear-$encoded"
  }
}