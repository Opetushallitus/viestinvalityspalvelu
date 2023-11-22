package fi.oph.viestinvalitys.sesmonitorointi

import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle

/**
 */
@TestInstance(Lifecycle.PER_CLASS)
class DeserialisoijaTest {

  /**
   */
  @Test def testDeserialisointi(): Unit =
    val deserialisoija = new Deserialisoija

    val json =
      """
        |{
        |  "eventType":"Bounce",
        |  "bounce":{
        |    "bounceType":"Permanent",
        |    "bounceSubType":"General",
        |    "bouncedRecipients":[
        |      {
        |        "emailAddress":"recipient@example.com",
        |        "action":"failed",
        |        "status":"5.1.1",
        |        "diagnosticCode":"smtp; 550 5.1.1 user unknown"
        |      }
        |    ],
        |    "timestamp":"2017-08-05T00:41:02.669Z",
        |    "feedbackId":"01000157c44f053b-61b59c11-9236-11e6-8f96-7be8aexample-000000",
        |    "reportingMTA":"dsn; mta.example.com"
        |  },
        |  "mail":{
        |    "timestamp":"2017-08-05T00:40:02.012Z",
        |    "source":"Sender Name <sender@example.com>",
        |    "sourceArn":"arn:aws:ses:us-east-1:123456789012:identity/sender@example.com",
        |    "sendingAccountId":"123456789012",
        |    "messageId":"EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |    "destination":[
        |      "recipient@example.com"
        |    ],
        |    "headersTruncated":false,
        |    "headers":[
        |      {
        |        "name":"From",
        |        "value":"Sender Name <sender@example.com>"
        |      },
        |      {
        |        "name":"To",
        |        "value":"recipient@example.com"
        |      },
        |      {
        |        "name":"Subject",
        |        "value":"Message sent from Amazon SES"
        |      },
        |      {
        |        "name":"MIME-Version",
        |        "value":"1.0"
        |      },
        |      {
        |        "name":"Content-Type",
        |        "value":"multipart/alternative; boundary=\"----=_Part_7307378_1629847660.1516840721503\""
        |      },
        |      {
        |        "name":"Message-ID",
        |        "value":"<800497654.1.1700568068709@[169.254.240.5]>"
        |      }
        |    ],
        |    "commonHeaders":{
        |      "from":[
        |        "Sender Name <sender@example.com>"
        |      ],
        |      "to":[
        |        "recipient@example.com"
        |      ],
        |      "messageId":"EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |      "subject":"Message sent from Amazon SES"
        |    },
        |    "tags":{
        |      "ses:configuration-set":[
        |        "ConfigSet"
        |      ],
        |      "ses:source-ip":[
        |        "192.0.2.0"
        |      ],
        |      "ses:from-domain":[
        |        "example.com"
        |      ],
        |      "ses:caller-identity":[
        |        "ses_user"
        |      ]
        |    }
        |  }
        |}""".stripMargin

    val message = deserialisoija.deserialisoi(json)
    Assertions.assertEquals("Permanent", message.bounce.bounceType)

    Assertions.assertEquals("<800497654.1.1700568068709@[169.254.240.5]>", message.mail.headers.find(h => MESSAGE_ID_HEADER_NAME.equals(h.name)).get.value)


}
