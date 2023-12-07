import * as cdk from 'aws-cdk-lib';
import {CfnOutput} from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {BucketEncryption} from 'aws-cdk-lib/aws-s3';
import * as route53 from "aws-cdk-lib/aws-route53";
import {Construct} from 'constructs';
import {StringParameter} from "aws-cdk-lib/aws-ssm";
import {Alias} from "aws-cdk-lib/aws-kms";

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class PersistenssiStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitysStackProps) {
    super(scope, id, props);

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
        cdk.Fn.importValue(`hahtuva-BastionSG`)), ec2.Port.tcp(5432), "DB sallittu bastionille")

    const postgresSecurityGroupId = new CfnOutput(this, "PostgresSecurityGroupId", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-postgres-securitygroupid`,
      description: 'Postgres security group id',
      value: postgresSecurityGroup.securityGroupId,
    });

    const parameterGroupName = StringParameter.valueFromLookup(this, `/${props.environmentName}/foundation/rds/aurora/pg/15`)
    const parameterGroup = rds.ParameterGroup.fromParameterGroupName(this, 'pg', parameterGroupName)

    const auroraCluster = new rds.DatabaseCluster(this, 'AuroraCluster', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({ version: rds.AuroraPostgresEngineVersion. VER_15_2}),
      serverlessV2MinCapacity: 0.5,
      serverlessV2MaxCapacity: 8,
      deletionProtection: false, // TODO: päivitä kun siirrytään tuotantoon
      removalPolicy: cdk.RemovalPolicy.DESTROY, // TODO: päivitä kun siirrytään tuotantoon
      writer: rds.ClusterInstance.serverlessV2('Writer', {
        caCertificate: rds.CaCertificate.RDS_CA_RDS4096_G1
      }),
      vpc,
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      securityGroups: [postgresSecurityGroup],
      credentials: rds.Credentials.fromUsername("oph"),
      storageEncrypted: true,
      storageEncryptionKey: Alias.fromAliasName(this, 'rds-key', `alias/${props.environmentName}/rds`),
      parameterGroup
    })

    const publicHostedZone = 'hahtuvaopintopolku.fi'
    const publicHostedZoneId = 'Z20VS6J64SGAG9'

    const zone = route53.HostedZone.fromHostedZoneAttributes(
        this,
        "PublicHostedZone",
        {
          zoneName: `${publicHostedZone}.`,
          hostedZoneId: `${publicHostedZoneId}`,
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
      value: `viestinvalitys.db.${publicHostedZone}`,
    });



/*
    const rdsProxy = new rds.DatabaseProxy(this, 'RDSProxy', {
      proxyTarget: rds.ProxyTarget.fromCluster(auroraCluster),
      iamAuth: false,
      secrets: [auroraCluster.secret!],
      vpc,
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      securityGroups: [postgresSecurityGroup]
    });
*/

  }
}
