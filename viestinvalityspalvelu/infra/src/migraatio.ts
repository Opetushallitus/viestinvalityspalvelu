import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as constructs from "constructs";
import * as lambda from "aws-cdk-lib/aws-lambda";
import path = require("path");
import * as logs from "aws-cdk-lib/aws-logs";
import * as triggers from "aws-cdk-lib/triggers";
import * as rds from "aws-cdk-lib/aws-rds";

export class MigraatioStack extends cdk.Stack {
  constructor(
    scope: constructs.Construct,
    id: string,
    vpc: ec2.IVpc,
    database: rds.DatabaseCluster,
    accessDatabaseSecurityGroup: ec2.SecurityGroup,
    props: cdk.StackProps,
  ) {
    super(scope, id, props);

    /**
     * Migraatiolambda. Tämä on erillisessä stackissa jotta voidaan deployata ennen uusia lambdoja
     * jotka tarvitsevat skeemamuutoksia
     */
    const migraatioLambdaFunction = new lambda.Function(
      this,
      "MigraatioLambda",
      {
        functionName: "viestinvalityspalvelu-migraatio",
        runtime: lambda.Runtime.JAVA_21,
        handler: "fi.oph.viestinvalitys.migraatio.LambdaHandler",
        code: lambda.Code.fromAsset(
          path.join(__dirname, "../../lambdat/migraatio/target/migraatio.zip"),
        ),
        timeout: cdk.Duration.seconds(60 * 5),
        memorySize: 1024,
        architecture: lambda.Architecture.ARM_64,
        environment: {
          DB_SECRET_ID: database.secret?.secretName!,
        },
        vpc,
        vpcSubnets: {
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        securityGroups: [accessDatabaseSecurityGroup],
        logRetention: logs.RetentionDays.TWO_YEARS,
      },
    );
    database.secret?.grantRead(migraatioLambdaFunction);

    // ajetaan migraatiolambda joka deploylla, perustuu SO-artikkeliin:
    // https://stackoverflow.com/questions/76656702/how-can-i-configure-amazon-cdk-to-trigger-a-lambda-function-after-deployment
    new triggers.Trigger(this, "MigraatioTrigger-" + Date.now().toString(), {
      handler: migraatioLambdaFunction,
    });
  }
}
