import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as sns from "aws-cdk-lib/aws-sns";
import * as sqs from "aws-cdk-lib/aws-sqs";

export class BucketAVSupportStack extends cdk.Stack {
  readonly findingsTopic: sns.ITopic;
  readonly scanQueue: sqs.IQueue;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.findingsTopic = this.lookupFindingsTopic();
    this.scanQueue = this.lookupScanQueue();
  }

  private lookupFindingsTopic() {
    const findingsTopicArn = cdk.Fn.importValue(
      "BucketAVStack-FindingsTopicArn",
    );
    return sns.Topic.fromTopicArn(this, "FindingsTopic", findingsTopicArn);
  }

  private lookupScanQueue() {
    const scanQueueArn = cdk.Fn.importValue("BucketAVStack-ScanQueueArn");
    return sqs.Queue.fromQueueArn(this, "ScanQueue", scanQueueArn);
  }
}
