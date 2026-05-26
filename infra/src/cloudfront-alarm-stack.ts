import * as cdk from "aws-cdk-lib";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";
import * as cloudwatch_actions from "aws-cdk-lib/aws-cloudwatch-actions";
import * as sns from "aws-cdk-lib/aws-sns";
import * as constructs from "constructs";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import { ROUTE53_HEALTH_CHECK_REGION } from "./health-check";

export function createCloudfrontAlarms(
  scope: constructs.Construct,
  distribution: cloudfront.Distribution,
  globalAlarmTopic: sns.ITopic,
) {
  new GlobalCloudFrontAlarmStack(
    scope,
    "CloudfrontAlarms",
    globalAlarmTopic,
    distribution,
    {
      env: {
        account: process.env.CDK_DEPLOY_TARGET_ACCOUNT,
        region: ROUTE53_HEALTH_CHECK_REGION,
      },
      crossRegionReferences: true,
    },
  );
}

class GlobalCloudFrontAlarmStack extends cdk.Stack {
  readonly globalAlarmTopic: sns.ITopic;

  constructor(
    scope: constructs.Construct,
    id: string,
    globalAlarmTopic: sns.ITopic,
    distribution: cloudfront.Distribution,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);
    this.globalAlarmTopic = globalAlarmTopic;

    const metric = distribution.metric5xxErrorRate();
    const alarm = this.createAlarm(metric);
    alarm.addAlarmAction(new cloudwatch_actions.SnsAction(globalAlarmTopic));
    alarm.addOkAction(new cloudwatch_actions.SnsAction(globalAlarmTopic));
  }

  createAlarm(metric: cloudwatch.Metric) {
    return new cloudwatch.Alarm(this, "Cloudfront5XXAlarm", {
      alarmName: "Cloudfront5XXAlarm",
      metric,
      comparisonOperator:
        cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      threshold: 10,
      evaluationPeriods: 2,
      datapointsToAlarm: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
  }
}
