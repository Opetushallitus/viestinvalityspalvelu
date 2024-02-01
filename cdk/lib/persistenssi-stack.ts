import * as cdk from 'aws-cdk-lib';
import {CfnOutput, Duration} from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {BucketEncryption} from 'aws-cdk-lib/aws-s3';
import * as route53 from "aws-cdk-lib/aws-route53";
import {Construct} from 'constructs';
import {StringParameter} from "aws-cdk-lib/aws-ssm";
import {Alias} from "aws-cdk-lib/aws-kms";
import * as elasticache from "aws-cdk-lib/aws-elasticache";
import * as lambda from "aws-cdk-lib/aws-lambda";
import path = require("path");
import * as iam from "aws-cdk-lib/aws-iam";
import {Effect} from "aws-cdk-lib/aws-iam";
import {RetentionDays} from "aws-cdk-lib/aws-logs";

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

    const parameterGroupName = StringParameter.valueFromLookup(this, `/${props.environmentName}/foundation/rds/aurora/pg/15`)
    const parameterGroup = rds.ParameterGroup.fromParameterGroupName(this, 'pg', parameterGroupName)

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
            resources: [`arn:aws:ssm:eu-west-1:${this.account}:parameter/${props.environmentName}/postgresqls/viestinvalitys/app-user-password`],
          })
          ],
        })
      }
    });
    migraatioRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    migraatioRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const postgresAccessSecurityGroup = new ec2.SecurityGroup(this, `LambdaPostgresAccessSecurityGroup`,{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-migraatio-postgresaccess`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(postgresAccessSecurityGroup, ec2.Port.tcp(5432), `Sallitaan postgres access migraatiolambdalle`)

    const lambdaFunction = new lambda.Function(this, `MigraatioLambda`, {
      functionName: `${props.environmentName}-viestinvalityspalvelu-migraatio`,
      runtime: lambda.Runtime.JAVA_17,
      handler: `fi.oph.viestinvalitys.migraatio.LambdaHandler`,
      code: lambda.Code.fromAsset(path.join(__dirname, `../../lambdat/migraatio/target/migraatio.jar`)),
      timeout: Duration.seconds(60),
      memorySize: 1024,
      architecture: lambda.Architecture.X86_64,
      role: migraatioRole,
      environment: {
        ENVIRONMENT_NAME: `${props.environmentName}`,
        DB_HOST: `viestinvalitys.db.${publicHostedZones[props.environmentName]}`,
      },
      vpc,
      securityGroups: [postgresAccessSecurityGroup],
      logRetention: RetentionDays.TWO_YEARS,
    })

    /**
     * Redis-klusteri. Tätä käytetään sessioiden tallentamiseen
     */
    const redisSecurityGroup = new ec2.SecurityGroup(this, "RedisSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-redis`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, "RedisSubnetGroup", {
      cacheSubnetGroupName: `${props.environmentName}-viestinvalityspalvelu`,
      subnetIds: vpc.privateSubnets.map(subnet => subnet.subnetId),
      description: "subnet group for redis"
    })

    const redisCluster = new elasticache.CfnCacheCluster(this, "RedisCluster", {
      clusterName: `${props.environmentName}-viestinvalityspalvelu`,
      engine: "redis",
      cacheNodeType: "cache.t4g.micro",
      numCacheNodes: 1,
      cacheSubnetGroupName: redisSubnetGroup.ref,
      vpcSecurityGroupIds: [redisSecurityGroup.securityGroupId]
    })

    const redisSecurityGroupId = new CfnOutput(this, "RedisSecurityGroupId", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-redis-securitygroupid`,
      description: 'Redis security group id',
      value: redisSecurityGroup.securityGroupId,
    });

    const redisEndPointAddress = new CfnOutput(this, "RedisEndPointAddress", {
      exportName: `${props.environmentName}-viestinvalityspalvelu-redis-endpoint`,
      description: 'Redis endpoint',
      value: redisCluster.attrRedisEndpointAddress,
    });
  }
}
