import * as cdk from 'aws-cdk-lib';
import {CfnOutput, Duration} from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {BucketEncryption} from 'aws-cdk-lib/aws-s3';
import * as route53 from "aws-cdk-lib/aws-route53";
import {Construct} from 'constructs';
import {Alias} from "aws-cdk-lib/aws-kms";

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class PersistenssiStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitysStackProps) {
    super(scope, id, props);

    const publicHostedZones: {[p: string]: string} = {
      hahtuva: 'hahtuvaopintopolku.fi',
      pallero: 'testiopintopolku.fi',
    }

    const publicHostedZoneIds: {[p: string]: string} = {
      hahtuva: 'Z20VS6J64SGAG9',
      pallero: 'Z175BBXSKVCV3B'
    }

    const backupRetention: {[p: string]: Duration} = {
      hahtuva: Duration.days(1),
      pallero: Duration.days(1),
      sade: Duration.days(30),
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

    // liitetiedostot
    const liitetiedostoBucket = new s3.Bucket(this, 'Attachments', {
      bucketName: `${props.environmentName}-viestinvalityspalvelu-attachments`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: BucketEncryption.KMS_MANAGED,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // toistaiseksi ei tarvitse jättää
    });
    const s3Arn = new CfnOutput(this, "S3Arn", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-liitetiedosto-s3-arn`,
      description: 'Liitetiedostojen S3 arn',
      value: liitetiedostoBucket.bucketArn,
    });

    // Create the serverless cluster, provide all values needed to customise the database.
    const postgresSecurityGroup = new ec2.SecurityGroup(this, "PostgresSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-postgres`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(ec2.SecurityGroup.fromSecurityGroupId(this, "BastionSecurityGroup",
        cdk.Fn.importValue(`${props.environmentName}-BastionSG`)), ec2.Port.tcp(5432), "DB sallittu bastionille")

    const postgresSecurityGroupId = new CfnOutput(this, "PostgresSecurityGroupId", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-postgres-securitygroupid`,
      description: 'Postgres security group id',
      value: postgresSecurityGroup.securityGroupId,
    });

    const parameterGroup = new rds.ParameterGroup(this, 'pg', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({version: rds.AuroraPostgresEngineVersion.VER_15_2}),
      parameters: {
        shared_preload_libraries: 'pg_stat_statements,pg_hint_plan,auto_explain,pg_cron'
      }
    })

    const auroraCluster = new rds.DatabaseCluster(this, 'AuroraCluster', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({ version: rds.AuroraPostgresEngineVersion. VER_15_2}),
      serverlessV2MinCapacity: 0.5,
      serverlessV2MaxCapacity: 16,
      deletionProtection: false, // TODO: päivitä kun siirrytään tuotantoon
      removalPolicy: cdk.RemovalPolicy.DESTROY, // TODO: päivitä kun siirrytään tuotantoon
      writer: rds.ClusterInstance.serverlessV2('Writer', {
        caCertificate: rds.CaCertificate.RDS_CA_RDS4096_G1,
        enablePerformanceInsights: true
      }),
      // TODO: lisää readeri tuotantosetuppiin
      vpc,
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      securityGroups: [postgresSecurityGroup],
      credentials: rds.Credentials.fromUsername("oph"),
      storageEncrypted: true,
      storageEncryptionKey: Alias.fromAliasName(this, 'rds-key', `alias/${props.environmentName}/rds`),
      backup: {
        retention: backupRetention[props.environmentName]
      },
      parameterGroup
    })

    const zone = route53.HostedZone.fromHostedZoneAttributes(
        this,
        "PublicHostedZone",
        {
          zoneName: `${publicHostedZones[props.environmentName]}.`,
          hostedZoneId: `${publicHostedZoneIds[props.environmentName]}`,
        }
    );
    const dbCnameRecord = new route53.CnameRecord(this, "DbCnameRecord", {
      recordName: `viestinvalitys.db`,
      zone,
      domainName: auroraCluster.clusterEndpoint.hostname
    });
    const postgresEndpoint = new CfnOutput(this, "PostgresEndpoint", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-db-dns`,
      description: 'Aurora endpoint',
      value: `viestinvalitys.db.${publicHostedZones[props.environmentName]}`,
    });
  }
}
