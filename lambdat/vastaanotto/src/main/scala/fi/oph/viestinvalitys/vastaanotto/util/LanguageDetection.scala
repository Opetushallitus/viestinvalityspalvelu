package fi.oph.viestinvalitys.vastaanotto.util

import com.github.pemistahl.lingua.api.{Language, LanguageDetectorBuilder}
import fi.oph.viestinvalitys.business.Kieli

import scala.jdk.CollectionConverters.*

object LanguageDetection {

  private val detector = LanguageDetectorBuilder.fromLanguages(Language.FINNISH, Language.SWEDISH, Language.ENGLISH).build()

  def tunnistaKieli(text: String): Set[Kieli] =
    detector.computeLanguageConfidenceValues(text).asScala
      .filter((kieli, luottamus) => luottamus>0.85)
      .map((kieli, luottamus) => kieli match
        case Language.FINNISH => Kieli.FI
        case Language.SWEDISH => Kieli.SV
        case Language.ENGLISH => Kieli.EN)
      .toSet
}
