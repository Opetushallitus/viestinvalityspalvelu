import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as constructs from "constructs";
import * as config from "./config";

export class VpcStack extends cdk.Stack {
  readonly vpc: ec2.IVpc;
  readonly bastion: ec2.BastionHostLinux;

  constructor(scope: constructs.Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = this.createVpc();
    this.bastion = this.createBastion();
  }

  private createVpc() {
    const vpcCidr = config.getConfig().vpcCidr;

    return new ec2.Vpc(this, "Vpc", {
      ipAddresses: ec2.IpAddresses.cidr(vpcCidr),
      maxAzs: 2,
      natGateways: 2,
      subnetConfiguration: [
        {
          cidrMask: 22,
          name: "Ingress",
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          cidrMask: 22,
          name: "Application",
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        {
          cidrMask: 22,
          name: "Database",
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
        },
      ],
    });
  }

  private createBastion(): ec2.BastionHostLinux {
    return new ec2.BastionHostLinux(this, "Bastion", {
      vpc: this.vpc,
      instanceName: "Bastion",
    });
  }
}
