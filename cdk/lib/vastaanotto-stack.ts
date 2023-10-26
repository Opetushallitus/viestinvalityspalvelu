import * as cdk from 'aws-cdk-lib';
import {Duration, Fn} from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {FunctionUrlAuthType} from 'aws-cdk-lib/aws-lambda';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect, ServicePrincipal} from 'aws-cdk-lib/aws-iam';
import {Construct} from 'constructs';
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as route53 from "aws-cdk-lib/aws-route53";
import * as acm from "aws-cdk-lib/aws-certificatemanager";
import * as cloudfront_origins from "aws-cdk-lib/aws-cloudfront-origins";
import * as route53targets from "aws-cdk-lib/aws-route53-targets";
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
                's3:*', // TODO: määrittele vain tarvittavat oikat
            ],
            resources: [attachmentBucketArn + '/*'],
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
    albSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(80), "Allow alb access from viestinvalituspalvelu vastaanotto lambda")

    const postgresSecurityGroupId = cdk.Fn.importValue(`${props.environmentName}-viestinvalituspalvelu-postgres-securitygroupid`);
    const postgresSecurityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, "PostgresSecurityGrouop", postgresSecurityGroupId);
    postgresSecurityGroup.addIngressRule(vastaanottoLambdaSecurityGroup, ec2.Port.tcp(5432), "Allow postgres access from viestinvalituspalvelu vastaanotto lambda")

    // staattinen saitti
    const staticBucket = new s3.Bucket(this, 'StaticFiles', {
      bucketName: `${props.environmentName}-viestinvalituspalvelu-static`,
      publicReadAccess: false,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY, // toistaiseksi ei tarvitse jättää
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
          domainName: `viestinvalitus.${publicHostedZone}`,
          hostedZone: zone,
          region: "us-east-1", // Cloudfront only checks this region for certificates.
        }
    );

    const noCachePolicy = new cloudfront.CachePolicy(
        this,
        `noCachePolicy-${props.environmentName}-viestinvalitus`,
        {
          cachePolicyName: `noCachePolicy-${props.environmentName}-viestinvalitus`,
          defaultTtl: cdk.Duration.minutes(0),
          minTtl: cdk.Duration.minutes(0),
          maxTtl: cdk.Duration.minutes(0),
        }
    );

    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      certificate: certificate,
      domainNames: [`viestinvalitus.${publicHostedZone}`],
      defaultRootObject: 'index.html',
      defaultBehavior: {
        origin: new cloudfront_origins.HttpOrigin(Fn.select(2, Fn.split('/', functionUrl.url)), {}),
        cachePolicy: noCachePolicy,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        originRequestPolicy: new cloudfront.OriginRequestPolicy(this, "LambdaOriginRequestPolicy", {
          originRequestPolicyName: `originRequestPolicy-${props.environmentName}-viestinvalitus`,
          cookieBehavior: cloudfront.OriginRequestCookieBehavior.all(),
          queryStringBehavior: cloudfront.OriginRequestQueryStringBehavior.all(),
          headerBehavior: cloudfront.OriginRequestHeaderBehavior.allowList("Accept", "Content-Type", "Content-Type-Original") // host header must be excluded???
        })
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
      recordName: `viestinvalitus.${publicHostedZone}`,
      target: route53.RecordTarget.fromAlias(
          new route53targets.CloudFrontTarget(distribution)
      ),
      zone,
    });
  }
}
