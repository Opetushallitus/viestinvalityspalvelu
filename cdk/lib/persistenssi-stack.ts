import * as cdk from 'aws-cdk-lib';
import {CfnOutput, Duration} from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {Construct} from 'constructs';

interface ViestinValitusStackProps extends cdk.StackProps {
  environmentName: string;
}

export class PersistenssiStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props: ViestinValitusStackProps) {
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

    // Vastaanoton jonot
    const highPriorityQueue = new sqs.Queue(this, 'SQSPriorityHigh', {
      queueName: `${props.environmentName}-viestinvalituspalvelu-priority-high`,
      visibilityTimeout: cdk.Duration.seconds(300)
    });
    const highPriorityQueueUrl = new CfnOutput(this, "SQSPriorityHighUrl", {
      exportName: `${props.environmentName}-viestinvalituspalvelu-queue-high-priority-url`,
      description: 'Korkean prioriteetin jonon url',
      value: highPriorityQueue.queueUrl,
    });

    const normalPriorityQueue = new sqs.Queue(this, 'SQSPriorityNormal', {
      queueName: `${props.environmentName}-viestinvalituspalvelu-priority-normal`,
      visibilityTimeout: cdk.Duration.seconds(300)
    });
    const normalPriorityQueueUrl = new CfnOutput(this, "SQSPriorityNormalUrl", {
      exportName: `${props.environmentName}-viestinvalituspalvelu-queue-normal-priority-url`,
      description: 'Normaalin prioriteetin jonon url',
      value: normalPriorityQueue.queueUrl,
    });

    // liitetiedostot
    const liitetiedostoBucket = new s3.Bucket(this, 'Attachments', {
      bucketName: `${props.environmentName}-viestinvalituspalvelu-attachments`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // toistaiseksi ei tarvitse jättää
    });
    const s3Arn = new CfnOutput(this, "S3Arn", {
      exportName: `${props.environmentName}-viestinvalituspalvelu-liitetiedosto-s3-arn`,
      description: 'Liitetiedostojen S3 arn',
      value: liitetiedostoBucket.bucketArn,
    });

    // Create the serverless cluster, provide all values needed to customise the database.
    const postgresSecurityGroup = new ec2.SecurityGroup(this, "PostgresSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalituspalvelu-postgres`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    new rds.DatabaseCluster(this, 'AuroraCluster', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({ version: rds.AuroraPostgresEngineVersion. VER_15_2}),
      serverlessV2MinCapacity: 0.5,
      serverlessV2MaxCapacity: 1,
      deletionProtection: false, // TODO: päivitä kun siirrytään tuotantoon
      removalPolicy: cdk.RemovalPolicy.DESTROY, // TODO: päivitä kun siirrytään tuotantoon
      writer: rds.ClusterInstance.serverlessV2('Writer', {}),
      vpc,
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      securityGroups: [postgresSecurityGroup]
    })

/*
    const cluster = new rds.ServerlessCluster(this, 'AuroraCluster', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({ version: rds.AuroraPostgresEngineVersion.VER_15_2 }),
      vpc,
      clusterIdentifier: `${props.environmentName}-viestinvalituspalvelu`,
      deletionProtection: false, // TODO: päivitä kun siirrytään tuotantoon
      removalPolicy: cdk.RemovalPolicy.DESTROY, // TODO: päivitä kun siirrytään tuotantoon
      scaling: {
        autoPause: Duration.minutes(5), // default is to pause after 5 minutes of idle time
        minCapacity: rds.AuroraCapacityUnit.ACU_1,
        maxCapacity: rds.AuroraCapacityUnit.ACU_2,
      },
      vpcSubnets: {
        subnets: vpc.privateSubnets
      },
      securityGroups: [postgresSecurityGroup]
    });
*/
  }
}
