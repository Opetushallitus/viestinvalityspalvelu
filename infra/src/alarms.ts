import * as cdk from "aws-cdk-lib";
import * as sns from "aws-cdk-lib/aws-sns";
import * as sns_subscriptions from "aws-cdk-lib/aws-sns-subscriptions";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as iam from "aws-cdk-lib/aws-iam";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import * as constructs from "constructs";

export class AlarmStack extends cdk.Stack {
  readonly alarmTopic: sns.ITopic;
  readonly alarmsToSlackLambda: lambda.IFunction;

  constructor(scope: constructs.Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);
    this.alarmsToSlackLambda = this.createAlarmsToSlackLambda();
    this.alarmTopic = this.createAlarmTopic();

    this.alarmTopic.addSubscription(
      new sns_subscriptions.LambdaSubscription(this.alarmsToSlackLambda),
    );

    const pagerDutyIntegrationUrlSecret =
        secretsmanager.Secret.fromSecretNameV2(
            this,
            "PagerDutyIntegrationUrlSecret",
            "PagerDutyIntegrationUrl"
        );

    this.alarmTopic.addSubscription(
        new sns_subscriptions.UrlSubscription(
            pagerDutyIntegrationUrlSecret.secretValue.toString(),
            { protocol: sns.SubscriptionProtocol.HTTPS }
        )
    );

    const radiatorAccountId = "905418271050";
    const radiatorReader = new iam.Role(this, "RadiatorReaderRole", {
      assumedBy: new iam.AccountPrincipal(radiatorAccountId),
      roleName: "RadiatorReader",
    });
    radiatorReader.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ["cloudwatch:DescribeAlarms"],
        resources: ["*"],
      }),
    );
  }

  createAlarmTopic(): sns.ITopic {
    return new sns.Topic(this, "AlarmTopic", {
      displayName: "alarm",
    });
  }

  createAlarmsToSlackLambda() {
    const alarmsToSlack = new lambda.Function(this, "AlarmsToSlack", {
      functionName: "alarms-to-slack",
      code: lambda.Code.fromAsset("../alarms-to-slack"),
      handler: "alarms-to-slack.handler",
      runtime: lambda.Runtime.NODEJS_20_X,
      architecture: lambda.Architecture.ARM_64,
      timeout: cdk.Duration.seconds(30),
    });

    // https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_lambda.html
    const parametersAndSecretsExtension =
      lambda.LayerVersion.fromLayerVersionArn(
        this,
        "ParametersAndSecretsLambdaExtension",
        "arn:aws:lambda:eu-west-1:015030872274:layer:AWS-Parameters-and-Secrets-Lambda-Extension-Arm64:11",
      );

    alarmsToSlack.addLayers(parametersAndSecretsExtension);
    secretsmanager.Secret.fromSecretNameV2(
      this,
      "slack-webhook",
      "slack-webhook",
    ).grantRead(alarmsToSlack);

    return alarmsToSlack;
  }
}
