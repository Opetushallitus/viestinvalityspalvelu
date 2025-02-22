import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as rds from "aws-cdk-lib/aws-rds";

export class DatabaseStack extends cdk.Stack {
  readonly database: rds.DatabaseCluster;

  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    bastion: ec2.BastionHostLinux,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    this.database = this.createDatabaseCluster(vpc);
    this.database.connections.allowDefaultPortFrom(bastion);
  }

  private createDatabaseCluster(vpc: ec2.IVpc) {
    return new rds.DatabaseCluster(this, "Database", {
      vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      },
      defaultDatabaseName: "viestinvalityspalvelu",
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_15_4,
      }),
      credentials: rds.Credentials.fromGeneratedSecret(
        "viestinvalityspalvelu",
        {
          secretName: "ViestinvalitysPalveluDatabaseSecret",
        },
      ),
      serverlessV2MinCapacity: 0.5,
      serverlessV2MaxCapacity: 16,
      storageEncrypted: true,
      storageType: rds.DBClusterStorageType.AURORA_IOPT1,
      writer: rds.ClusterInstance.serverlessV2("writer", {
        enablePerformanceInsights: true,
      }),
      readers: [
        rds.ClusterInstance.serverlessV2("reader", {
          enablePerformanceInsights: true,
          scaleWithWriter: true,
        }),
      ],
    });
  }
}
