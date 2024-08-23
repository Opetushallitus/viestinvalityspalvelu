import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import {Construct} from 'constructs';
import * as lambda from "aws-cdk-lib/aws-lambda";
import path = require("path");
import * as iam from "aws-cdk-lib/aws-iam";
import {Effect} from "aws-cdk-lib/aws-iam";
import {RetentionDays} from "aws-cdk-lib/aws-logs";
import * as triggers from 'aws-cdk-lib/triggers';

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class MigraatioStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitysStackProps) {
    super(scope, id, props);

    const publicHostedZones: {[p: string]: string} = {
      untuva: 'untuvaopintopolku.fi',
      hahtuva: 'hahtuvaopintopolku.fi',
      pallero: 'testiopintopolku.fi',
      sade: 'opintopolku.fi',
    }

    const vpc = ec2.Vpc.fromVpcAttributes(this, "VPC", {
      vpcId: cdk.Fn.importValue(`${props.environmentName}-Vpc`),
      availabilityZones: [
        cdk.Fn.importValue(`${props.environmentName}-SubnetAvailabilityZones`),
      ],
      privateSubnetIds: [
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet1`),
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet2`),
        cdk.Fn.importValue(`${props.environmentName}-PrivateSubnet3`),
      ],
    });

    /**
     * Migraatiolambda. Tämä on persistenssistackissa jotta voidaan deployata ennen uusia lambdoja
     * jotka tarvitsevat skeemamuutoksia
     */
    const migraatioRole = new iam.Role(this, 'MigraatioRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
        ssmAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'ssm:GetParameter',
            ],
            resources: [`arn:aws:ssm:eu-west-1:${this.account}:parameter/${props.environmentName}/postgresqls/viestinvalityspalvelu/app-user-password`],
          })
          ],
        })
      }
    });
    migraatioRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    migraatioRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const postgresSecurityGroupId = cdk.Fn.importValue(`${props.environmentName}-PostgreSQLSG`);
    const postgresSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "PostgresSecurityGroup", postgresSecurityGroupId);
    const postgresAccessSecurityGroup = new ec2.SecurityGroup(this, `LambdaPostgresAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-migraatio-postgresaccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(postgresAccessSecurityGroup, ec2.Port.tcp(5432), `Sallitaan postgres access migraatiolambdalle`)

    const migraatioLambdaFunction = new lambda.Function(this, `MigraatioLambda`, {
      functionName: `${props.environmentName}-viestinvalityspalvelu-migraatio`,
      runtime: lambda.Runtime.JAVA_17,
      handler: `fi.oph.viestinvalitys.migraatio.LambdaHandler`,
      code: lambda.Code.fromAsset(path.join(__dirname, `../../target/lambdat/migraatio.zip`)),
      timeout: Duration.seconds(60*5),
      memorySize: 1024,
      architecture: lambda.Architecture.X86_64,
      role: migraatioRole,
      environment: {
        ENVIRONMENT_NAME: `${props.environmentName}`,
        DB_HOST: `viestinvalityspalvelu.db.${publicHostedZones[props.environmentName]}`,
      },
      vpc,
      securityGroups: [postgresAccessSecurityGroup],
      logRetention: RetentionDays.TWO_YEARS,
    })

    // ajetaan migraatiolambda joka deploylla, perustuu SO-artikkeliin:
    // https://stackoverflow.com/questions/76656702/how-can-i-configure-amazon-cdk-to-trigger-a-lambda-function-after-deployment
    new triggers.Trigger(this, 'MigraatioTrigger-' + Date.now().toString(), {
      handler: migraatioLambdaFunction,
    });
  }
}
