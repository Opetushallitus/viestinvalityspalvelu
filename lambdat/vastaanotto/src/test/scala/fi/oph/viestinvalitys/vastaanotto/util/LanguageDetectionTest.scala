package fi.oph.viestinvalitys.vastaanotto.util

import fi.oph.viestinvalitys.business.Kieli
import org.junit.jupiter.api.{Assertions, Test}

@Test
class LanguageDetectionTest {

  val TEKSTI_FI = "Mistä ylioppilastutkinto koostuu?\n" +
    "Ylioppilastutkinnon valmiiksi saamiseen vaaditaan viisi koetta. Äidinkielen ja kirjallisuuden kokeen suorittaminen " +
    "vaaditaan kaikilta kokelailta, ja muut vaadittavat neljä koetta tulee valita seuraavista ryhmistä:\n" +
    "vieras kieli\n" +
    "toinen kotimainen kieli\n" +
    "matematiikka\n" +
    "reaaliaine.\n" +
    "Vähintään yhden kokeen tulee olla pitkän oppimäärän koe. Kokelas valitsee kokeita vähintään kolmesta eri ryhmästä.\n" +
    "Matematiikassa ja toisessa kotimaisessa kielessä järjestetään vaativuudeltaan kahden eri tason mukaiset kokeet. Vieraissa " +
    "kielissä voidaan järjestää kahden eri tason mukaiset kokeet. Voit valita kumman tason mukaiseen kokeeseen osallistut.\n" +
    "Kun olet suorittanut hyväksytysti pakolliset kokeet sekä lukiokoulutuksen oppimäärän tai muun ylioppilastutkinnon " +
    "suorittamiseen oikeuttavan tutkinnon tai koulutuksen, saat ylioppilastutkintotodistuksen. Todistus annetaan sinä " +
    "tutkintokertana, jolloin olet suorittanut kaikki pakolliset kokeet hyväksytysti.\n" +
    "Voit suorittaa ylioppilastutkinnon kokonaan yhdellä tutkintokerralla tai hajautettuna enintään kolmeen peräkkäiseen " +
    "tutkintokertaan. Voit lisätä tutkintoosi uusia ylimääräisiä aineita, kunnes tutkinto on valmis tai kolme tutkintokertaa " +
    "on käytetty.\n" +
    "Voit uusia hyväksytyn tai hylätyn kokeen seuraavien kolmen tutkintokerran aikana. Voit myös täydentää tutkinnon " +
    "oppiaineissa, joiden kokeisiin et ole aikaisemmin osallistunut tai suorittaa aineen eritasoisen kokeen."

  val TEKSTI_SV = "Vad består studentexamen av?\n" +
    "För att få studentexamen bör examinanden ha avlagt fem studentexamensprov. Samtliga examinander bör avlägga provet i " +
    "modersmål och litteratur. De övriga ämnena väljs ur följande ämnesgrupper:\n" +
    "ett främmande språk\n" +
    "det andra inhemska språket\n" +
    "matematik\n" +
    "realämnena.\n" +
    "I minst ett prov bör man avlägga prov i lång lärokurs. Examinanden väljer proven ur minst tre olika ämnesgrupper.\n" +
    "I läroämnena matematik och det andra inhemska språket ordnas prov i två kravnivåer. I främmande språk kan ordnas prov " +
    "i två nivåer. Du får själv välja nivån för proven.\nNär du har avlagt de obligatoriska proven med godkända vitsord samt " +
    "lärokursen för gymnasiet eller en annan examen eller utbildning som berättigar till studentexamen får du " +
    "studentexamensbetyget. Betyget ges vid den examensomgång då du avlagt alla obligatoriska prov med godkända vitsord.\n" +
    "Du kan avlägga studentexamen under högst tre på varandra följande examenstillfällen. Du kan lägga till extra ämnen i din " +
    "examen ända tills examen har avlagts i sin helhet eller under samtliga tre på varandra följande examenstillfällen.\n" +
    "Du kan förnya ett godkänt eller underkänt prov under de tre på varandra följande examenstillfällena. Du kan dessutom " +
    "komplettera examen med läroämnen i vilka du inte ännu har deltagit i. Dessutom kan du avlägga prov i en annan kravnivå " +
    "än den du tidigare avlagt."

  val TEKSTI_EN = "Finnish matriculation examination\n" +
    "Virtually all students who complete the upper secondary school syllabus will also take the national matriculation " +
    "examination.\n" +
    "The purpose of the matriculation examination held at the end of the general upper secondary education is to determine " +
    "whether students\n" +
    "have acquired the knowledge and skills required by the curriculum for the upper secondary school\n" +
    "have reached an adequate level of maturity in line with the goals of the upper secondary school.\n" +
    "Passing the Matriculation Examination entitles the candidate to continue their studies at a higher education level " +
    "(either at a university or a university of applied sciences (UAS). Upon successful completion of the matriculation " +
    "examination and the entire upper secondary school syllabus, students are awarded a separate certificate that shows " +
    "details of the tests passed and the levels and grades achieved." +
    "\nStudents in vocational upper secondary education and training may also take the matriculation examination." +
    "\nThe matriculation examination is drawn up nationally, and there is a centralised body to check each test against " +
    "uniform criteria."

  @Test def testValidateTiedostonimi(): Unit = {
    Assertions.assertEquals(Set(Kieli.FI), LanguageDetection.tunnistaKieli(TEKSTI_FI))
    Assertions.assertEquals(Set(Kieli.SV), LanguageDetection.tunnistaKieli(TEKSTI_SV))
    Assertions.assertEquals(Set(Kieli.EN), LanguageDetection.tunnistaKieli(TEKSTI_EN))
    Assertions.assertEquals(Set(Kieli.FI, Kieli.SV, Kieli.EN), LanguageDetection.tunnistaKieli(TEKSTI_FI + "\n\n" + TEKSTI_SV+ "\n\n" + TEKSTI_EN))
  }
}