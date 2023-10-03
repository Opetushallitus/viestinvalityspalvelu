import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {Architecture, FunctionUrlAuthType} from 'aws-cdk-lib/aws-lambda';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as targets from 'aws-cdk-lib/aws-elasticloadbalancingv2-targets';
import {Protocol, SecurityGroup, Vpc} from 'aws-cdk-lib/aws-ec2';
import {Construct} from 'constructs';
import path = require("path");
import {ServicePrincipal} from "aws-cdk-lib/aws-iam";

export class ViestinValitysStack extends cdk.Stack {

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

/*
    // korkean prioriteetin jono
    const highPriority = new sqs.Queue(this, 'priority-high', {
       visibilityTimeout: cdk.Duration.seconds(300)
    });

    // liitetiedostot
    const attachmentBucket = new s3.Bucket(this, 'attachments');
*/
    const vpc = Vpc.fromLookup(this, "VPC", {
      vpcId: "vpc-e2b58985"
    })

    const redis_subnet_group = new elasticache.CfnSubnetGroup(this, "hahtuva-viestinvalituspalvelu-redis-subnetgroup", {
      subnetIds: vpc.privateSubnets.map(subnet => subnet.subnetId),
      description: "subnet group for redis"
    })

    const lambda_sec_group = new ec2.SecurityGroup(this, "hahtuva-viestinvalituspalvelu-lambda-securitygroup",{
          securityGroupName: "lambda-sec-group",
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    const redis_sec_group = new ec2.SecurityGroup(this, "hahtuva-viestinvalituspalvelu-redis-securitygroup",{
          securityGroupName: "redis-sec-group",
          vpc: vpc,
          allowAllOutbound: true
        },
    )

    redis_sec_group.addIngressRule(lambda_sec_group, ec2.Port.tcp(6379), "Allow redis access from lambda")

    const redis_cluster = new elasticache.CfnCacheCluster(this, "hahtuva-viestinvalituspalvelu-redis", {
      engine: "redis",
      cacheNodeType: "cache.t4g.micro",
      numCacheNodes: 1,
      cacheSubnetGroupName: redis_subnet_group.ref,
      vpcSecurityGroupIds: [redis_sec_group.securityGroupId]
    })

    const vastaanOttoRole = new iam.Role(this, 'hahtuva-viestinvalituspalvelu-vastaanotto', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
    });
    vastaanOttoRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    vastaanOttoRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole")); // only required if your function lives in a VPC

    const vastaanotto = new lambda.Function(this, 'vastaanotto', {
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitus.vastaanotto.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../vastaanotto/target/vastaanotto-0.1-SNAPSHOT.jar')),
      timeout: Duration.seconds(60),
      memorySize: 512,
      architecture: Architecture.X86_64,
      role: vastaanOttoRole,
      environment: {
        "spring_redis_host": redis_cluster.attrRedisEndpointAddress,
        "spring_redis_port": "6379"
      },
      vpc: vpc,
      securityGroups: [lambda_sec_group]
    });

    // Enable SnapStart
    const cfnFunction = vastaanotto.node.defaultChild as lambda.CfnFunction;
    cfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const version = vastaanotto.currentVersion;
    const alias = new lambda.Alias(this, 'LambdaAlias', {
      aliasName: 'Test',
      version,
    });

/*
    const versionUrl = alias.addFunctionUrl({
      authType: FunctionUrlAuthType.NONE
    })
*/

    const albSecurityGroup = SecurityGroup.fromSecurityGroupId(this, "AlbSecurityGroup", "sg-4e78b234")
    albSecurityGroup.addIngressRule(lambda_sec_group, ec2.Port.tcp(80), "Allow alb access from viestinvalituspalvelu lambda")

    const listener = elbv2.ApplicationListener.fromApplicationListenerAttributes(this, "hahtuvaListener", {
      listenerArn: "arn:aws:elasticloadbalancing:eu-west-1:153563371259:listener/app/hahtuva-ALB/0d3edebd6dff68b8/65bfaa2f483fa868",
      securityGroup: albSecurityGroup
    });

    const lambdaTarget = new targets.LambdaTarget(alias);
    const applicationLoadBalancerTargetGroup = new elbv2.ApplicationTargetGroup(this, 'lambdaTargetGroup', {
      targets: [lambdaTarget]
    })

    alias.addPermission("albPermission", {
      principal: new ServicePrincipal('elasticloadbalancing.amazonaws.com'),
      sourceArn: applicationLoadBalancerTargetGroup.targetGroupArn
    })

    const applicationLoadBalancerPathListenerRule = new elbv2.ApplicationListenerRule(this, 'PathListenerRule', {
      listener: listener,
      priority: 21000,
      conditions:[
        elbv2.ListenerCondition.hostHeaders(['virkailija.*opintopolku.fi']),
        elbv2.ListenerCondition.pathPatterns(['/viestinvalituspalvelu/*'])
      ],
      action: elbv2.ListenerAction.forward([applicationLoadBalancerTargetGroup])
    })
  }
}
