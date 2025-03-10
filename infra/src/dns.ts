import * as cdk from "aws-cdk-lib";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as config from "./config";
import * as constructs from "constructs";

export class DnsStack extends cdk.Stack {
  readonly hostedZone: route53.IHostedZone;
  readonly opintopolkuHostedZone: route53.IHostedZone;
  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.hostedZone = new route53.HostedZone(this, "HostedZone", {
      zoneName: config.getConfig().zoneName,
    });
    this.opintopolkuHostedZone = new route53.HostedZone(this, "HostedZone", {
      zoneName: `viestinvalitys.${config.getConfig().opintopolkuDomainName}`,
    });
  }
}
