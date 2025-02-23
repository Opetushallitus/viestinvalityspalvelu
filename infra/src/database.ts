import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as rds from "aws-cdk-lib/aws-rds";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as sns from "aws-cdk-lib/aws-sns";
import * as backup from "./database-backup";

export class DatabaseStack extends cdk.Stack {
  readonly database: rds.DatabaseCluster;
  readonly accessForLambda: ec2.SecurityGroup;
  readonly databaseName = "viestinvalityspalvelu";

  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    bastion: ec2.BastionHostLinux,
    ecsCluster: ecs.Cluster,
    alarmTopic: sns.ITopic,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    this.database = this.createDatabaseCluster(vpc);
    const backupToS3 = new backup.DatabaseBackupToS3(
      this,
      "DatabaseBackupToS3",
      ecsCluster,
      this.database,
      this.databaseName,
      alarmTopic,
    );
    this.accessForLambda = new ec2.SecurityGroup(
      this,
      "DatabaseAccessForLambda",
      { vpc },
    );
    this.database.connections.allowDefaultPortFrom(this.accessForLambda);
    this.database.connections.allowDefaultPortFrom(backupToS3);
    this.database.connections.allowDefaultPortFrom(bastion);
  }

  private createDatabaseCluster(vpc: ec2.IVpc) {
    return new rds.DatabaseCluster(this, "Database", {
      vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      },
      defaultDatabaseName: this.databaseName,
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
