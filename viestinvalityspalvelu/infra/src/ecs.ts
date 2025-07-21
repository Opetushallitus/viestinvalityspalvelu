import * as cdk from "aws-cdk-lib";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as constructs from "constructs";

export class EcsStack extends cdk.Stack {
  readonly ecsCluster: ecs.Cluster;

  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    this.ecsCluster = new ecs.Cluster(this, "Cluster", {
      vpc,
      clusterName: "Cluster",
    });
  }
}
