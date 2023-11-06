import * as cdk from 'aws-cdk-lib';
import {aws_events, Duration, Fn} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {FunctionUrlAuthType} from 'aws-cdk-lib/aws-lambda';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect} from 'aws-cdk-lib/aws-iam';
import {Construct} from 'constructs';
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as acm from "aws-cdk-lib/aws-certificatemanager";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as route53targets from "aws-cdk-lib/aws-route53-targets";
import * as targets from 'aws-cdk-lib/aws-events-targets'
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as eventsources from 'aws-cdk-lib/aws-lambda-event-sources';

import path = require("path");

interface ViestinValitysStackProps extends cdk.StackProps {
  environmentName: string;
}

export class VastaanottoStack extends cdk.Stack {

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

    // Redis-klusteri
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

    // Vastaanottolambda
    const attachmentBucketArn = cdk.Fn.importValue(`${props.environmentName}-viestinvalityspalvelu-liitetiedosto-s3-arn`);
    const vastaanottoLambdaSecurityGroup = new ec2.SecurityGroup(this, "VastaanottoLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-vastaanotto`,
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
              's3:*', // TODO: määrittele vain tarvittavat oikat
            ],
            resources: [attachmentBucketArn + '/*'],
          })]
        }),
        ssmAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'ssm:GetParameter',
            ],
            resources: [`*`],
          })
          ],
        })
      }
    });
    vastaanottoLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    vastaanottoLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const vastaanottoLambda = new lambda.Function(this, 'VastaanottoLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-vastaanotto`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.vastaanotto.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../vastaanotto/target/vastaanotto-0.1-SNAPSHOT.jar')),
      timeout: Duration.seconds(60),
      memorySize: 1024,
      architecture: lambda.Architecture.X86_64,
      role: vastaanottoLambdaRole,
      environment: {
        "spring_redis_host": redisCluster.attrRedisEndpointAddress,
        "spring_redis_port": `${redisCluster.attrRedisEndpointPort}`,
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

    //
    const functionUrl = alias.addFunctionUrl({
      authType: FunctionUrlAuthType.NONE,
    });

    // Annetaan vastaanottolambdalle oikeus kutsua alb:tä (tikettien validointi)
    const albSecurityGroupId = cdk.Fn.importValue('hahtuva-EcsAlbSG');
    const albSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "AlbSecurityGroup", albSecurityGroupId)
    albSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(80), "Allow alb access from viestinvalityspalvelu vastaanotto lambda")

    const postgresSecurityGroupId = cdk.Fn.importValue(`${props.environmentName}-viestinvalityspalvelu-postgres-securitygroupid`);
    const postgresSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "PostgresSecurityGrouop", postgresSecurityGroupId);
    postgresSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu vastaanotto lambda")

    // staattinen saitti
    const staticBucket = new s3.Bucket(this, 'StaticFiles', {
      bucketName: `${props.environmentName}-viestinvalityspalvelu-static`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // toistaiseksi ei tarvitse jättää
      autoDeleteObjects: true
    });

    const staticS3Deployment = new s3deploy.BucketDeployment(this, 'DeployWebsite', {
      sources: [s3deploy.Source.asset('../static')],
      destinationBucket: staticBucket,
    });

    const cloudfrontOAI = new cloudfront.OriginAccessIdentity(
        this,
        "cloudfront-OAI",
        {
          comment: `OAI for viestinvälityspalvelu`,
        }
    );
    staticBucket.grantRead(cloudfrontOAI);

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

    const certificate = new acm.DnsValidatedCertificate(
        this,
        "SiteCertificate",
        {
          domainName: `viestinvalitys.${publicHostedZone}`,
          hostedZone: zone,
          region: "us-east-1", // Cloudfront only checks this region for certificates.
        }
    );

    const noCachePolicy = new cloudfront.CachePolicy(
        this,
        `noCachePolicy-${props.environmentName}-viestinvalitys`,
        {
          cachePolicyName: `noCachePolicy-${props.environmentName}-viestinvalitys`,
          defaultTtl: cdk.Duration.minutes(0),
          minTtl: cdk.Duration.minutes(0),
          maxTtl: cdk.Duration.minutes(0),
        }
    );

    const lambdaHeaderFunction = new cloudfront.Function(this, 'Function', {
      functionName: `viestinvalitys-${props.environmentName}-content-type-original-header`,
      code: cloudfront.FunctionCode.fromInline('function handler(event) {\n' +
          '    var request = event.request;\n' +
          '    var contentType = request.headers[\'content-type\'];\n' +
          '\n' +
          '    if(contentType && contentType.value.startsWith(\'multipart/form-data\')) {\n' +
          '        request.headers[\'content-type\'] = {value: \'application/octet-stream\'};\n' +
          '        request.headers[\'content-type-original\'] = contentType;\n' +
          '    }    \n' +
          '\n' +
          '    return request;\n' +
          '}'),
    });

    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      certificate: certificate,
      domainNames: [`viestinvalitys.${publicHostedZone}`],
      defaultRootObject: 'index.html',
      defaultBehavior: {
        origin: new cloudfront_origins.HttpOrigin(Fn.select(2, Fn.split('/', functionUrl.url)), {}),
        cachePolicy: noCachePolicy,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        originRequestPolicy: new cloudfront.OriginRequestPolicy(this, "LambdaOriginRequestPolicy", {
          originRequestPolicyName: `originRequestPolicy-${props.environmentName}-viestinvalitys`,
          cookieBehavior: cloudfront.OriginRequestCookieBehavior.all(),
          queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
          headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList("Accept", "Content-Type", "Content-Type-Original") // host header must be excluded???
        }),
        functionAssociations: [{
          function: lambdaHeaderFunction,
          eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
        }],
      },
      additionalBehaviors: {
        '/static/*': {
          origin: new cloudfront_origins.S3Origin(staticBucket, {
            originAccessIdentity: cloudfrontOAI
          }),
          cachePolicy: noCachePolicy,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        }
      }
    })

    // Route53 alias record for the CloudFront distribution
    new route53.ARecord(this, "SiteAliasRecord", {
      recordName: `viestinvalitys.${publicHostedZone}`,
      target: route53.RecordTarget.fromAlias(
          new route53targets.CloudFrontTarget(distribution)
      ),
      zone,
    });

    // Orkestroinnin ajastus
    const clockQueue = new sqs.Queue(this, 'ClockQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-timing`,
      visibilityTimeout: Duration.seconds(60)
    });

    const clockLambdaRole = new iam.Role(this, 'KelloLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
        sqsAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'sqs:*', // TODO: määrittele vain tarvittavat oikat
            ],
            resources: [clockQueue.queueArn],
          })]
        })
      }
    });
    clockLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));

    const clockLambda = new lambda.Function(this, 'KelloLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-kello`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.kello.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../kello/target/kello.jar')),
      timeout: Duration.seconds(60),
      memorySize: 256,
      architecture: lambda.Architecture.X86_64,
      role: clockLambdaRole,
      environment: {
        "sqs_queue_url": clockQueue.queueUrl,
      }
    });

    const clockRule = new aws_events.Rule(this, 'KelloRule', {
      schedule: aws_events.Schedule.rate(cdk.Duration.minutes(1))
    });
    clockRule.addTarget(new targets.LambdaFunction(clockLambda));

    // orkestrointi
    const orkestraattoriLambdaSecurityGroup = new ec2.SecurityGroup(this, "OrkestraattoriLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-orkestraattori`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(orkestraattoriLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu orkestraattori lambda")

    const orkestraattoriLambdaRole = new iam.Role(this, 'OrkestraattoriLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
        s3Access: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              's3:*', // TODO: määrittele vain tarvittavat oikat
            ],
            resources: [attachmentBucketArn + '/*'],
          })]
        }),
        ssmAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'ssm:GetParameter',
            ],
            resources: [`*`],
          })
          ],
        }),
        sqsAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'sqs:*', // TODO: määrittele vain tarvittavat oikat
            ],
            resources: [clockQueue.queueArn],
          })]
        })
      }
    });
    orkestraattoriLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    orkestraattoriLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const orkestraattoriLambda = new lambda.Function(this, 'OrkestraattoriLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-orkestraattori`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.orkestraattori.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../orkestraattori/target/orkestraattori.jar')),
      timeout: Duration.seconds(60),
        memorySize: 512,
      architecture: lambda.Architecture.X86_64,
      role: orkestraattoriLambdaRole,
      environment: {
        "spring_redis_host": redisCluster.attrRedisEndpointAddress,
        "spring_redis_port": `${redisCluster.attrRedisEndpointPort}`,
        "attachment_bucket_arn": attachmentBucketArn,
      },
      vpc: vpc,
      securityGroups: [orkestraattoriLambdaSecurityGroup]
    });

    // SnapStart
    const orkestraattoriVersion = orkestraattoriLambda.currentVersion;
    const orkestraattoriAlias = new lambda.Alias(this, 'OrkestraattoriLambdaAlias', {
      aliasName: 'Current',
      version: orkestraattoriVersion,
    });

    const orkestraattoriCfnFunction = orkestraattoriLambda.node.defaultChild as lambda.CfnFunction;
    orkestraattoriCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const eventSource = new eventsources.SqsEventSource(clockQueue);
    orkestraattoriAlias.addEventSource(eventSource);
  }
}
