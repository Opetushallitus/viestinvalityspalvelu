package fi.oph.viestinvalitys.tilapaivitys

import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle

import scala.jdk.CollectionConverters.*

/**
 * Testaa että AWS SES:n lähettämät eventit deserialisoidaan oikein. Eventit on dokumentoitu osoitteessa:
 * https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns-examples.html
 *
 * Click ja Open -eventtejä ei toistaiseksi käytetä.
 */
@TestInstance(Lifecycle.PER_CLASS)
class DeserialisoijaTest {

  /**
   * Testataan että eventeistä saadaan ulos Message-ID, joka on viestinvalityspalvelun käyttämä tunniste
   * vastaanottajalle.
   */
  @Test def testExtractMessageId(): Unit =
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
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals("EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000", message.get.mail.messageId)

  /**
   * Testataan bounce-viestin deserialisointi
   */
  @Test def testBounceDeserialisointi(): Unit =
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
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals(Bounce("Permanent", java.util.List.of(BouncedRecipient("smtp; 550 5.1.1 user unknown"))), message.get.bounce)

  /**
   * Testataan complaint-viestin deserialisointi
   */
  @Test def testComplaitDeserialisointi(): Unit =
    val json =
      """
        |{
        |  "eventType":"Complaint",
        |  "complaint": {
        |    "complainedRecipients":[
        |      {
        |        "emailAddress":"recipient@example.com"
        |      }
        |    ],
        |    "timestamp":"2017-08-05T00:41:02.669Z",
        |    "feedbackId":"01000157c44f053b-61b59c11-9236-11e6-8f96-7be8aexample-000000",
        |    "userAgent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
        |    "complaintFeedbackType":"abuse",
        |    "arrivalDate":"2017-08-05T00:41:02.669Z"
        |  },
        |  "mail":{
        |    "timestamp":"2017-08-05T00:40:01.123Z",
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
        |        "name":"MIME-Version","value":"1.0"
        |      },
        |      {
        |        "name":"Content-Type",
        |        "value":"multipart/alternative; boundary=\"----=_Part_7298998_679725522.1516840859643\""
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
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals("abuse", message.get.complaint.complaintFeedbackType)

  /**
   * Testataan delivery-viestin deserialisointi
   */
  @Test def testDeliveryDeserialisointi(): Unit =
    val json =
      """
        |{
        |  "eventType": "Delivery",
        |  "mail": {
        |    "timestamp": "2016-10-19T23:20:52.240Z",
        |    "source": "sender@example.com",
        |    "sourceArn": "arn:aws:ses:us-east-1:123456789012:identity/sender@example.com",
        |    "sendingAccountId": "123456789012",
        |    "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |    "destination": [
        |      "recipient@example.com"
        |    ],
        |    "headersTruncated": false,
        |    "headers": [
        |      {
        |        "name": "From",
        |        "value": "sender@example.com"
        |      },
        |      {
        |        "name": "To",
        |        "value": "recipient@example.com"
        |      },
        |      {
        |        "name": "Subject",
        |        "value": "Message sent from Amazon SES"
        |      },
        |      {
        |        "name": "MIME-Version",
        |        "value": "1.0"
        |      },
        |      {
        |        "name": "Content-Type",
        |        "value": "text/html; charset=UTF-8"
        |      },
        |      {
        |        "name": "Content-Transfer-Encoding",
        |        "value": "7bit"
        |      }
        |    ],
        |    "commonHeaders": {
        |      "from": [
        |        "sender@example.com"
        |      ],
        |      "to": [
        |        "recipient@example.com"
        |      ],
        |      "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |      "subject": "Message sent from Amazon SES"
        |    },
        |    "tags": {
        |      "ses:configuration-set": [
        |        "ConfigSet"
        |      ],
        |      "ses:source-ip": [
        |        "192.0.2.0"
        |      ],
        |      "ses:from-domain": [
        |        "example.com"
        |      ],
        |      "ses:caller-identity": [
        |        "ses_user"
        |      ],
        |      "ses:outgoing-ip": [
        |        "192.0.2.0"
        |      ],
        |      "myCustomTag1": [
        |        "myCustomTagValue1"
        |      ],
        |      "myCustomTag2": [
        |        "myCustomTagValue2"
        |      ]
        |    }
        |  },
        |  "delivery": {
        |    "timestamp": "2016-10-19T23:21:04.133Z",
        |    "processingTimeMillis": 11893,
        |    "recipients": [
        |      "recipient@example.com"
        |    ],
        |    "smtpResponse": "250 2.6.0 Message received",
        |    "reportingMTA": "mta.example.com"
        |  }
        |}""".stripMargin
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals("2016-10-19T23:21:04.133Z", message.get.delivery.timestamp)

  /**
   * Testataan send-viestin deserialisointi
   */
  @Test def testSendDeserialisointi(): Unit =
    val json =
      """
        |{
        |  "eventType": "Send",
        |  "mail": {
        |    "timestamp": "2016-10-14T05:02:16.645Z",
        |    "source": "sender@example.com",
        |    "sourceArn": "arn:aws:ses:us-east-1:123456789012:identity/sender@example.com",
        |    "sendingAccountId": "123456789012",
        |    "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |    "destination": [
        |      "recipient@example.com"
        |    ],
        |    "headersTruncated": false,
        |    "headers": [
        |      {
        |        "name": "From",
        |        "value": "sender@example.com"
        |      },
        |      {
        |        "name": "To",
        |        "value": "recipient@example.com"
        |      },
        |      {
        |        "name": "Subject",
        |        "value": "Message sent from Amazon SES"
        |      },
        |      {
        |        "name": "MIME-Version",
        |        "value": "1.0"
        |      },
        |      {
        |        "name": "Content-Type",
        |        "value": "multipart/mixed;  boundary=\"----=_Part_0_716996660.1476421336341\""
        |      },
        |      {
        |        "name": "X-SES-MESSAGE-TAGS",
        |        "value": "myCustomTag1=myCustomTagValue1, myCustomTag2=myCustomTagValue2"
        |      }
        |    ],
        |    "commonHeaders": {
        |      "from": [
        |        "sender@example.com"
        |      ],
        |      "to": [
        |        "recipient@example.com"
        |      ],
        |      "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |      "subject": "Message sent from Amazon SES"
        |    },
        |    "tags": {
        |      "ses:configuration-set": [
        |        "ConfigSet"
        |      ],
        |      "ses:source-ip": [
        |        "192.0.2.0"
        |      ],
        |      "ses:from-domain": [
        |        "example.com"
        |      ],
        |      "ses:caller-identity": [
        |        "ses_user"
        |      ],
        |      "myCustomTag1": [
        |        "myCustomTagValue1"
        |      ],
        |      "myCustomTag2": [
        |        "myCustomTagValue2"
        |      ]
        |    }
        |  },
        |  "send": {}
        |}""".stripMargin
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals(new Send, message.get.send)

  /**
   * Testataan reject-viestin deserialisointi
   */
  @Test def testRejectDeserialisointi(): Unit =
    val json =
      """
        |{
        |  "eventType": "Reject",
        |  "mail": {
        |    "timestamp": "2016-10-14T17:38:15.211Z",
        |    "source": "sender@example.com",
        |    "sourceArn": "arn:aws:ses:us-east-1:123456789012:identity/sender@example.com",
        |    "sendingAccountId": "123456789012",
        |    "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |    "destination": [
        |      "sender@example.com"
        |    ],
        |    "headersTruncated": false,
        |    "headers": [
        |      {
        |        "name": "From",
        |        "value": "sender@example.com"
        |      },
        |      {
        |        "name": "To",
        |        "value": "recipient@example.com"
        |      },
        |      {
        |        "name": "Subject",
        |        "value": "Message sent from Amazon SES"
        |      },
        |      {
        |        "name": "MIME-Version",
        |        "value": "1.0"
        |      },
        |      {
        |        "name": "Content-Type",
        |        "value": "multipart/mixed; boundary=\"qMm9M+Fa2AknHoGS\""
        |      },
        |      {
        |        "name": "X-SES-MESSAGE-TAGS",
        |        "value": "myCustomTag1=myCustomTagValue1, myCustomTag2=myCustomTagValue2"
        |      }
        |    ],
        |    "commonHeaders": {
        |      "from": [
        |        "sender@example.com"
        |      ],
        |      "to": [
        |        "recipient@example.com"
        |      ],
        |      "messageId": "EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |      "subject": "Message sent from Amazon SES"
        |    },
        |    "tags": {
        |      "ses:configuration-set": [
        |        "ConfigSet"
        |      ],
        |      "ses:source-ip": [
        |        "192.0.2.0"
        |      ],
        |      "ses:from-domain": [
        |        "example.com"
        |      ],
        |      "ses:caller-identity": [
        |        "ses_user"
        |      ],
        |      "myCustomTag1": [
        |        "myCustomTagValue1"
        |      ],
        |      "myCustomTag2": [
        |        "myCustomTagValue2"
        |      ]
        |    }
        |  },
        |  "reject": {
        |    "reason": "Bad content"
        |  }
        |}""".stripMargin
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals("Bad content", message.get.reject.reason)

  /**
   * Testataan deliveryDelay-viestin deserialisointi
   */
  @Test def testDeliveryDelayDeserialisointi(): Unit =
    val json =
      """
        |{
        |  "eventType": "DeliveryDelay",
        |  "mail":{
        |    "timestamp":"2020-06-16T00:15:40.641Z",
        |    "source":"sender@example.com",
        |    "sourceArn":"arn:aws:ses:us-east-1:123456789012:identity/sender@example.com",
        |    "sendingAccountId":"123456789012",
        |    "messageId":"EXAMPLE7c191be45-e9aedb9a-02f9-4d12-a87d-dd0099a07f8a-000000",
        |    "destination":[
        |      "recipient@example.com"
        |    ],
        |    "headersTruncated":false,
        |    "tags":{
        |      "ses:configuration-set":[
        |        "ConfigSet"
        |      ]
        |    }
        |  },
        |  "deliveryDelay": {
        |    "timestamp": "2020-06-16T00:25:40.095Z",
        |    "delayType": "TransientCommunicationFailure",
        |    "expirationTime": "2020-06-16T00:25:40.914Z",
        |    "delayedRecipients": [{
        |      "emailAddress": "recipient@example.com",
        |      "status": "4.4.1",
        |      "diagnosticCode": "smtp; 421 4.4.1 Unable to connect to remote host"
        |    }]
        |  }
        |}
        |}""".stripMargin
    val message = Deserialisoija.deserialisoiSesNotifikaatio(json)
    Assertions.assertEquals(DeliveryDelay("TransientCommunicationFailure", java.util.List.of(new DelayedRecipient("smtp; 421 4.4.1 Unable to connect to remote host"))), message.get.deliveryDelay)
}
