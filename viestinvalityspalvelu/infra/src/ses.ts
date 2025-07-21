import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ses from "aws-cdk-lib/aws-ses";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as sns from "aws-cdk-lib/aws-sns";
import * as sqs from "aws-cdk-lib/aws-sqs";
import * as sns_subscriptions from "aws-cdk-lib/aws-sns-subscriptions";
import * as config from "./config";

export class SESStack extends cdk.Stack {
  readonly identity: ses.EmailIdentity;
  readonly opintopolkuIdentity: ses.EmailIdentity;
  readonly configurationSet: ses.ConfigurationSet;
  readonly monitorointiQueue: sqs.Queue;

  constructor(
    scope: constructs.Construct,
    id: string,
    hostedZone: route53.IHostedZone,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    this.monitorointiQueue = this.createMonitorointiQueue();
    const monitorointiTopic = this.createMonitorointiTopic(
      this.monitorointiQueue,
    );
    this.configurationSet = this.createConfigurationSet(monitorointiTopic);
    this.identity = new ses.EmailIdentity(this, "EmailIdentity", {
      identity: ses.Identity.publicHostedZone(hostedZone),
      configurationSet: this.configurationSet,
    });
    this.opintopolkuIdentity = new ses.EmailIdentity(
      this,
      "OpintopolkuEmailIdentity",
      {
        identity: ses.Identity.domain(config.getConfig().opintopolkuDomainName),
        configurationSet: this.configurationSet,
        mailFromDomain: config.getConfig().mailFromDomainName,
      },
    );
  }

  private createConfigurationSet(monitorointiTopic: sns.ITopic) {
    const configurationSet = new ses.ConfigurationSet(
      this,
      "ConfigurationSet",
      {
        configurationSetName: "viestinvalityspalvelu",
      },
    );
    configurationSet.addEventDestination("EventDestination", {
      destination: ses.EventDestination.snsTopic(monitorointiTopic),
      enabled: true,
      events: [
        ses.EmailSendingEvent.SEND,
        ses.EmailSendingEvent.REJECT,
        ses.EmailSendingEvent.BOUNCE,
        ses.EmailSendingEvent.COMPLAINT,
        ses.EmailSendingEvent.DELIVERY,
        ses.EmailSendingEvent.DELIVERY_DELAY,
      ],
    });
    return configurationSet;
  }

  private createMonitorointiTopic(monitorointiQueue: sqs.Queue) {
    const monitorointiTopic = new sns.Topic(
      this,
      "viestinvalityspalvelu-ses-monitorointi",
      {
        enforceSSL: true,
      },
    );
    monitorointiTopic.addSubscription(
      new sns_subscriptions.SqsSubscription(monitorointiQueue),
    );
    return monitorointiTopic;
  }

  private createMonitorointiQueue() {
    const monitorointiDLQ = new sqs.Queue(this, "MonitorointiDLQ", {
      queueName: "viestinvalityspalvelu-ses-monitorointiDlq",
      retentionPeriod: cdk.Duration.days(14),
      enforceSSL: true,
    });
    return new sqs.Queue(this, "MonitorointiQueue", {
      queueName: "viestinvalityspalvelu-ses-monitorointi",
      visibilityTimeout: cdk.Duration.seconds(60),
      enforceSSL: true,
      deadLetterQueue: {
        maxReceiveCount: 3,
        queue: monitorointiDLQ,
      },
    });
  }
}
