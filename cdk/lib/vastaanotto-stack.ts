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
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sns_subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import * as ses from 'aws-cdk-lib/aws-ses';

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
        "ATTACHMENTS_BUCKET_NAME": 'hahtuva-viestinvalityspalvelu-attachments'
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
        "clock_queue_url": clockQueue.queueUrl,
      }
    });

    const clockRule = new aws_events.Rule(this, 'KelloRule', {
      schedule: aws_events.Schedule.rate(cdk.Duration.minutes(1))
    });
    clockRule.addTarget(new targets.LambdaFunction(clockLambda));


    // orkestrointi
    const monitorointiTopic = new sns.Topic(this, `${props.environmentName}-viestinvalityspalvelu-ses-monitorointi`);
    const configurationSet = new ses.ConfigurationSet(this, 'ConfigurationSet', {
      configurationSetName: `${props.environmentName}-viestinvalityspalvelu`,
    });
    const eventDestination = new ses.CfnConfigurationSetEventDestination(this, 'EventDestination', {
      configurationSetName: configurationSet.configurationSetName,
      eventDestination: {
        snsDestination: {
          topicArn: monitorointiTopic.topicArn
        },
        enabled: true,
        matchingEventTypes: ['send', 'reject', 'bounce', 'complaint', 'delivery', 'deliveryDelay'],
      }
    })

    const lahetysLambdaSecurityGroup = new ec2.SecurityGroup(this, "LahetysLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-lahetys`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(lahetysLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu lahetys lambda")

    const lahetysLambdaRole = new iam.Role(this, 'LahetysLambdaRole', {
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
        sesAccess: new iam.PolicyDocument({
          statements: [new iam.PolicyStatement({
            effect: Effect.ALLOW,
            actions: [
              'ses:SendRawEmail',
            ],
            resources: [`*`],
          })
          ],
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
    lahetysLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    lahetysLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const lahetysLambda = new lambda.Function(this, 'LahetysLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-lahetys`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.lahetys.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../lahetys/target/lahetys.jar')),
      timeout: Duration.seconds(60),
        memorySize: 1024,
      architecture: lambda.Architecture.X86_64,
      role: lahetysLambdaRole,
      environment: {
        "clock_queue_url": clockQueue.queueUrl,
        MODE: 'TEST',
        FAKEMAILER_HOST: 'fakemailer-1.fakemailer.hahtuvaopintopolku.fi',
        FAKEMAILER_PORT: '1025',
        ATTACHMENTS_BUCKET_NAME: 'hahtuva-viestinvalityspalvelu-attachments',
        CONFIGURATION_SET_NAME: configurationSet.configurationSetName
      },
      vpc: vpc,
      securityGroups: [lahetysLambdaSecurityGroup]
    });

    // SnapStart
    const lahetysVersion = lahetysLambda.currentVersion;
    const lahetysAlias = new lambda.Alias(this, 'LahetysLambdaAlias', {
      aliasName: 'Current',
      version: lahetysVersion,
    });
    const lahetysCfnFunction = lahetysLambda.node.defaultChild as lambda.CfnFunction;
    lahetysCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const eventSource = new eventsources.SqsEventSource(clockQueue);
    lahetysAlias.addEventSource(eventSource);




    // migraatio
    const migraatioLambdaSecurityGroup = new ec2.SecurityGroup(this, "MigraatioLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-migraatio`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(migraatioLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu migraatio lambda")

    const migraatioLambdaRole = new iam.Role(this, 'MigraatioLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
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
      }
    });
    migraatioLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    migraatioLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const migraatioLambda = new lambda.Function(this, 'MigraatioLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-migraatio`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.flyway.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../flyway/target/flyway.jar')),
      timeout: Duration.seconds(60),
      memorySize: 512,
      architecture: lambda.Architecture.X86_64,
      role: migraatioLambdaRole,
      environment: {
      },
      vpc: vpc,
      securityGroups: [migraatioLambdaSecurityGroup]
    });

    // SnapStart
    const migraatioVersion = migraatioLambda.currentVersion;
    const migraatioAlias = new lambda.Alias(this, 'MigraatioLambdaAlias', {
      aliasName: 'Current',
      version: migraatioVersion,
    });
    const migraatioCfnFunction = migraatioLambda.node.defaultChild as lambda.CfnFunction;
    migraatioCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });








    // skannaus
    const skannausLambdaSecurityGroup = new ec2.SecurityGroup(this, "SkannausLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-skannaus`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(skannausLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu skannaus lambda")

    const skannausTopic = sns.Topic.fromTopicArn(this, 'BucketAVTopic', 'arn:aws:sns:eu-west-1:153563371259:bucketav-stack-FindingsTopic-hKCyHnK3Jkyw')
    const skannausQueue = new sqs.Queue(this, 'SkannausQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-skannaus`,
      visibilityTimeout: Duration.seconds(60)
    });
    skannausTopic.addSubscription(new sns_subscriptions.SqsSubscription(skannausQueue))

    const skannausLambdaRole = new iam.Role(this, 'SkannausLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
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
            resources: [skannausQueue.queueArn],
          })]
        })
      }
    });
    skannausLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    skannausLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const skannausLambda = new lambda.Function(this, 'SkannausLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-skannaus`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.skannaus.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../skannaus/target/skannaus.jar')),
      timeout: Duration.seconds(60),
      memorySize: 256,
      architecture: lambda.Architecture.X86_64,
      role: skannausLambdaRole,
      environment: {
        SKANNAUS_QUEUE_URL: skannausQueue.queueUrl
      },
      vpc: vpc,
      securityGroups: [skannausLambdaSecurityGroup]
    });

    const skannausVersion = skannausLambda.currentVersion;
    const skannausAlias = new lambda.Alias(this, 'SkannausLambdaAlias', {
      aliasName: 'Current',
      version: skannausVersion,
    });
    const skannausCfnFunction = skannausLambda.node.defaultChild as lambda.CfnFunction;
    skannausCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const skannausSqsEventSource = new eventsources.SqsEventSource(skannausQueue);
    skannausAlias.addEventSource(skannausSqsEventSource);


    // monitorointi
    const monitorointiLambdaSecurityGroup = new ec2.SecurityGroup(this, "MonitorointiLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-monitorointi`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(monitorointiLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu monitorointi lambda")

    const monitorointiQueue = new sqs.Queue(this, 'MonitorointiQueue', {
      queueName: `${props.environmentName}-viestinvalityspalvelu-ses-monitorointi`,
      visibilityTimeout: Duration.seconds(60)
    });
    monitorointiTopic.addSubscription(new sns_subscriptions.SqsSubscription(monitorointiQueue))

    const monitorointiLambdaRole = new iam.Role(this, 'MonitorointiLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      inlinePolicies: {
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
            resources: [monitorointiQueue.queueArn],
          })]
        })
      }
    });
    monitorointiLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    monitorointiLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const monitorointiLambda = new lambda.Function(this, 'MonitorointiLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-monitorointi`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.sesmonitorointi.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../ses-monitorointi/target/ses-monitorointi.jar')),
      timeout: Duration.seconds(60),
      memorySize: 256,
      architecture: lambda.Architecture.X86_64,
      role: monitorointiLambdaRole,
      environment: {
        "SES_MONITOROINTI_QUEUE_URL": monitorointiQueue.queueUrl
      },
      vpc: vpc,
      securityGroups: [monitorointiLambdaSecurityGroup]
    });

    const monitorointiVersion = monitorointiLambda.currentVersion;
    const monitorointiAlias = new lambda.Alias(this, 'MonitorointiLambdaAlias', {
      aliasName: 'Current',
      version: monitorointiVersion,
    });
    const monitorointiCfnFunction = monitorointiLambda.node.defaultChild as lambda.CfnFunction;
    monitorointiCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const monitorointiSqsEventSource = new eventsources.SqsEventSource(monitorointiQueue);
    monitorointiAlias.addEventSource(monitorointiSqsEventSource);



    // siivous
    const siivousLambdaSecurityGroup = new ec2.SecurityGroup(this, "SiivousLambdaSecurityGroup",{
          securityGroupName: `${props.environmentName}-viestinvalityspalvelu-lambda-siivous`,
          vpc: vpc,
          allowAllOutbound: true
        },
    )
    postgresSecurityGroup.addIngressRule(siivousLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalityspalvelu siivous lambda")

    const siivousLambdaRole = new iam.Role(this, 'SiivousLambdaRole', {
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
    siivousLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
    siivousLambdaRole.addManagedPolicy(iam.ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaVPCAccessExecutionRole"));

    const siivousLambda = new lambda.Function(this, 'SiivousLambda', {
      functionName: `${props.environmentName}-viestinvalityspalvelu-siivous`,
      runtime: lambda.Runtime.JAVA_17,
      handler: 'fi.oph.viestinvalitys.siivous.LambdaHandler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../siivous/target/siivous.jar')),
      timeout: Duration.seconds(60),
      memorySize: 256,
      architecture: lambda.Architecture.X86_64,
      role: siivousLambdaRole,
      environment: {
        ATTACHMENTS_BUCKET_NAME: 'hahtuva-viestinvalityspalvelu-attachments'
      },
      vpc: vpc,
      securityGroups: [siivousLambdaSecurityGroup]
    });

    const siivousVersion = siivousLambda.currentVersion;
    const siivousAlias = new lambda.Alias(this, 'SiivousLambdaAlias', {
      aliasName: 'Current',
      version: siivousVersion,
    });
    const siivousCfnFunction = siivousLambda.node.defaultChild as lambda.CfnFunction;
    siivousCfnFunction.addPropertyOverride("SnapStart", { ApplyOn: "PublishedVersions" });

    const siivousRule = new aws_events.Rule(this, 'SiivousRule', {
      schedule: aws_events.Schedule.rate(cdk.Duration.minutes(1))
    });
    siivousRule.addTarget(new targets.LambdaFunction(siivousAlias));

  }
}
