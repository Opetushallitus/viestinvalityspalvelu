import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect, ServicePrincipal} from 'aws-cdk-lib/aws-iam';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as targets from 'aws-cdk-lib/aws-elasticloadbalancingv2-targets';
import {Construct} from 'constructs';
import path = require("path");

interface ViestinValitusStackProps extends cdk.StackProps {
  environmentName: string;
}

export class VastaanottoStack extends cdk.Stack {

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

    // Redis-klusteri
    const redisSecurityGroup = new ec2.SecurityGroup(this, "RedisSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalituspalvelu-redis`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, "RedisSubnetGroup", {
      cacheSubnetGroupName: `${props.environmentName}-viestinvalituspalvelu`,
      subnetIds: vpc.privateSubnets.map(subnet => subnet.subnetId),
      description: "subnet group for redis"
    })

    const redisCluster = new elasticache.CfnCacheCluster(this, "RedisCluster", {
      clusterName: `${props.environmentName}-viestinvalituspalvelu`,
      engine: "redis",
      cacheNodeType: "cache.t4g.micro",
      numCacheNodes: 1,
      cacheSubnetGroupName: redisSubnetGroup.ref,
      vpcSecurityGroupIds: [redisSecurityGroup.securityGroupId]
    })

    // Vastaanottolambda
    const attachmentBucketArn = cdk.Fn.importValue(`${props.environmentName}-viestinvalituspalvelu-liitetiedosto-s3-arn`);
    const highPriorityQueueUrl = cdk.Fn.importValue(`${props.environmentName}-viestinvalituspalvelu-queue-high-priority-url`);
    const normalPriorityQueueUrl = cdk.Fn.importValue(`${props.environmentName}-viestinvalituspalvelu-queue-normal-priority-url`);

    const vastaanottoLambdaSecurityGroup = new ec2.SecurityGroup(this, "VastaanottoLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalituspalvelu-lambda-vastaanotto`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    redisSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(6379), "Vastaanotto-lambda sallittu")

    const vastaanottoLambdaRole = new iam.Role(this, 'VastaanottoLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
        attachmentS3Access: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
                's3:PutObject', // TODO: määrittely tarvittavat oikat
            ],
            resources: [attachmentBucketArn],
          })],
        })
      }
    });
    vastaanottoLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    vastaanottoLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const vastaanottoLambda = new lambda.Function(this, 'VastaanottoLambda', {
      functionName: `${props.environmentName}-viestinvalituspalvelu-vastaanotto`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitus.vastaanotto.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../vastaanotto/target/vastaanotto-0.1-SNAPSHOT.jar')),
      timeout: Duration.seconds(60),
      memorySize: 512,
      architecture: lambda.Architecture.X86_64,
      role: vastaanottoLambdaRole,
      environment: {
        "spring_redis_host": redisCluster.attrRedisEndpointAddress,
        "spring_redis_port": `${redisCluster.port}`,
        "high_priority_queue_url": highPriorityQueueUrl,
        "normal_priority_queue_url": normalPriorityQueueUrl,
        "attachment_bucket_arn": attachmentBucketArn,
      },
      vpc: vpc,
      securityGroups: [vastaanottoLambdaSecurityGroup]
    });

    // SnapStart
    const version = vastaanottoLambda.currentVersion;
    const alias = new lambda.Alias(this, 'LambdaAlias', {
      aliasName: 'Current',
      version,
    });

    const cfnFunction = vastaanottoLambda.node.defaultChild as lambda.CfnFunction;
    cfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    // Liitetään vastaanottolambda alb:hen
    const albSecurityGroupId = cdk.Fn.importValue('hahtuva-EcsAlbSG');
    const albSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "AlbSecurityGroup", albSecurityGroupId)
    const vastaanottoLambdaTarget = new targets.LambdaTarget(alias);
    const vastaanottoLoadBalancerTargetGroup = new elbv2.ApplicationTargetGroup(this, 'VastaanottoTargetGroup', {
      targets: [vastaanottoLambdaTarget]
    })
    alias.addPermission("VastaanottoAlbPermission", {
      principal: new ServicePrincipal('elasticloadbalancing.amazonaws.com'),
      sourceArn: vastaanottoLoadBalancerTargetGroup.targetGroupArn
    })

    const albListenerArn = cdk.Fn.importValue('hahtuva-ALBListener');
    const albListener = elbv2.ApplicationListener.fromApplicationListenerAttributes(this, "ALBListener", {
      listenerArn: albListenerArn,
      securityGroup: albSecurityGroup
    });
    const VastaanottoListenerRule = new elbv2.ApplicationListenerRule(this, 'VastaanottoListenerRule', {
      listener: albListener,
      priority: 21000,
      conditions:[
        elbv2.ListenerCondition.hostHeaders(['virkailija.*opintopolku.fi']),
        elbv2.ListenerCondition.pathPatterns(['/viestinvalituspalvelu/*'])
      ],
      action: elbv2.ListenerAction.forward([vastaanottoLoadBalancerTargetGroup])
    })

    // Annetaan vastaanottolambdalle oikeus kutsua alb:tä (tikettien validointi)
    albSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(80), "Allow alb access from viestinvalituspalvelu vastaanotto lambda")
  }
}
