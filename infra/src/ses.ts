import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ses from "aws-cdk-lib/aws-ses";
import * as route53 from "aws-cdk-lib/aws-route53";

export class SESStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    hostedZone: route53.IHostedZone,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    new ses.EmailIdentity(this, "EmailIdentity", {
      identity: ses.Identity.publicHostedZone(hostedZone),
    });
  }
}
