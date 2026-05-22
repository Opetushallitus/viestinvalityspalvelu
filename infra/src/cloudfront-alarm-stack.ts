import * as cdk from "aws-cdk-lib";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";
import * as cloudwatch_actions from "aws-cdk-lib/aws-cloudwatch-actions";
import * as sns from "aws-cdk-lib/aws-sns";
import * as constructs from "constructs";

export class GlobalCloudFrontAlarmStack extends cdk.Stack {
  readonly globalAlarmTopic: sns.ITopic;

  constructor(scope: constructs.Construct, id: string, globalAlarmTopic: sns.ITopic, cfDistributionId: string, props: cdk.StackProps) {
    super(scope, id, props);
    this.globalAlarmTopic = globalAlarmTopic;

    const metric = this.createCf5xxMetric(cfDistributionId);
    const alarm = this.createAlarm(metric);
    alarm.addAlarmAction(new cloudwatch_actions.SnsAction(globalAlarmTopic));
    alarm.addOkAction(new cloudwatch_actions.SnsAction(globalAlarmTopic));
  }

  createCf5xxMetric(distributionId: string) {
    return new cloudwatch.Metric({
      namespace: "AWS/CloudFront",
      metricName: "5xxErrorRate",
      dimensionsMap: {
        DistributionId: distributionId,
        Region: "Global",
      },
      // CloudFront metrics are only available in us-east-1
      region: this.env.region,
      account: this.env.account,
      statistic: "Average",
      period: cdk.Duration.minutes(5),
    }); 
  }

  createAlarm(metric: cloudwatch.Metric) {
    return new cloudwatch.Alarm(this, "viestinvalitys-raportointi-alarm", {
      alarmName: 'ViestinvalitysRaportointiNextJsTarget5XXAlarm',
      metric,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      threshold: 10,
      evaluationPeriods: 2,
      datapointsToAlarm: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
  }
}