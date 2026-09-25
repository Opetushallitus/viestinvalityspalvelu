import * as cdk from "aws-cdk-lib";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as iam from "aws-cdk-lib/aws-iam";
import * as guardduty from "aws-cdk-lib/aws-guardduty";
import * as constructs from "constructs";

export class PersistenssiStack extends cdk.Stack {
  readonly liitetiedostoBucket: s3.Bucket;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.liitetiedostoBucket = new s3.Bucket(this, "Attachments");

    const managedRuleArn = `arn:${cdk.Aws.PARTITION}:events:${cdk.Aws.REGION}:${cdk.Aws.ACCOUNT_ID}:rule/DO-NOT-DELETE-AmazonGuardDutyMalwareProtectionS3*`;

    const malwareProtectionRole = new iam.Role(
      this,
      "GuardDutyMalwareProtectionRole",
      {
        assumedBy: new iam.ServicePrincipal(
          "malware-protection-plan.guardduty.amazonaws.com",
        ),
        inlinePolicies: {
          MalwareProtectionPolicy: new iam.PolicyDocument({
            statements: [
              new iam.PolicyStatement({
                actions: [
                  "events:PutRule",
                  "events:DeleteRule",
                  "events:PutTargets",
                  "events:RemoveTargets",
                ],
                resources: [managedRuleArn],
                conditions: {
                  StringLike: {
                    "events:ManagedBy":
                      "malware-protection-plan.guardduty.amazonaws.com",
                  },
                },
              }),

              new iam.PolicyStatement({
                actions: ["events:DescribeRule", "events:ListTargetsByRule"],
                resources: [managedRuleArn],
              }),

              new iam.PolicyStatement({
                actions: [
                  "s3:PutObjectTagging",
                  "s3:GetObjectTagging",
                  "s3:PutObjectVersionTagging",
                  "s3:GetObjectVersionTagging",
                ],
                resources: [this.liitetiedostoBucket.arnForObjects("*")],
              }),

              new iam.PolicyStatement({
                actions: [
                  "s3:PutBucketNotification",
                  "s3:GetBucketNotification",
                ],
                resources: [this.liitetiedostoBucket.bucketArn],
              }),

              new iam.PolicyStatement({
                actions: ["s3:PutObject"],
                resources: [
                  this.liitetiedostoBucket.arnForObjects(
                    "malware-protection-resource-validation-object",
                  ),
                ],
              }),

              new iam.PolicyStatement({
                actions: ["s3:ListBucket"],
                resources: [this.liitetiedostoBucket.bucketArn],
              }),

              new iam.PolicyStatement({
                actions: ["s3:GetObject", "s3:GetObjectVersion"],
                resources: [this.liitetiedostoBucket.arnForObjects("*")],
              }),
            ],
          }),
        },
      },
    );

    new guardduty.CfnMalwareProtectionPlan(
      this,
      "AttachmentsMalwareProtection",
      {
        role: malwareProtectionRole.roleArn,

        protectedResource: {
          s3Bucket: {
            bucketName: this.liitetiedostoBucket.bucketName,
          },
        },

        actions: {
          tagging: {
            status: "ENABLED",
          },
        },
      },
    );
  }
}
